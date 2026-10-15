"""
Train the CareVoice monitoring-field classifier (TF-IDF + One-vs-Rest
Logistic Regression), and reproduce the Kaggle v1 notebook outside Kaggle.

Steps:
   1. Load train / validation / test JSONL and the question catalog.
   2. Turn each record into condition text + a list of expected fields.
   3. Build the 22-column 0/1 label matrix (column order = catalog order).
   4. Fit TF-IDF on TRAINING text only.
   5. Train one logistic regression per field (One-vs-Rest).
   6. Pick the probability threshold using VALIDATION only.
   7. Freeze that threshold.
   8. Score the untouched TEST split once.
   9. Print precision, recall, F1, exact-set match, Hamming loss.
  10. Save classifier, vectorizer, label encoder, and metadata.json.

Usage (from the carevoice-ai folder):
    python ml/train_classifier.py                 # trains v1 -> ml/models/v1/
    python ml/train_classifier.py --tag v2 --data-dir ml/data_v2

The synthetic labels are prototype CareVoice rules, not clinician-reviewed.
These numbers are an engineering benchmark, not clinical accuracy.
"""

import argparse
import json
import platform
import sys
import warnings
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import scipy
import sklearn
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.multiclass import OneVsRestClassifier
from sklearn.preprocessing import MultiLabelBinarizer

from carevoice_features import (
    check_labels_known,
    file_sha256,
    load_field_codes,
    ml_dir,
    multilabel_metrics,
    prepare_dataset,
    print_metrics,
    read_jsonl,
)

# Fixed seed. lbfgs (the default solver) is deterministic anyway; the seed
# keeps results stable if someone later switches to a randomized solver.
RANDOM_SEED = 42

# The same grid the Kaggle notebook searched: 0.10, 0.15, ..., 0.90.
# Rounding avoids float values like 0.30000000000000004.
THRESHOLD_GRID = np.round(np.arange(0.10, 0.91, 0.05), 2)


def parse_args():
    parser = argparse.ArgumentParser(description="Train the CareVoice field classifier.")
    parser.add_argument("--tag", default="v1",
                        help="Model version name. Artifacts go to ml/models/<tag>/ (default: v1).")
    parser.add_argument("--data-dir", default=None,
                        help="Folder with train/validation/test JSONL + question_catalog.json "
                             "(default: ml/data).")
    parser.add_argument("--model-dir", default=None,
                        help="Where to save artifacts (default: ml/models/<tag>).")
    return parser.parse_args()


def main():
    args = parse_args()
    base = ml_dir()
    data_dir = (base / "data") if args.data_dir is None else Path(args.data_dir)
    model_dir = (base / "models" / args.tag) if args.model_dir is None else Path(args.model_dir)

    files = {
        "train": data_dir / "train.jsonl",
        "validation": data_dir / "validation.jsonl",
        "test": data_dir / "test.jsonl",
        "catalog": data_dir / "question_catalog.json",
    }
    missing = [str(p) for p in files.values() if not p.exists()]
    if missing:
        sys.exit("Missing data files:\n  " + "\n  ".join(missing))

    # ------------------------------------------------------------------
    # 1-2. Load and parse
    # ------------------------------------------------------------------
    train_records = read_jsonl(files["train"])
    val_records = read_jsonl(files["validation"])
    test_records = read_jsonl(files["test"])

    X_train_text, y_train_labels = prepare_dataset(train_records)
    X_val_text, y_val_labels = prepare_dataset(val_records)
    X_test_text, y_test_labels = prepare_dataset(test_records)

    print(f"Data folder: {data_dir}")
    print(f"Train: {len(train_records)}  Validation: {len(val_records)}  Test: {len(test_records)}")

    # Leakage check: paraphrases of one scenario share a groupId, and a group
    # must sit in exactly one split. Stop rather than report inflated scores.
    groups = {name: {r["groupId"] for r in recs}
              for name, recs in [("train", train_records), ("validation", val_records), ("test", test_records)]}
    overlaps = {
        "train∩validation": groups["train"] & groups["validation"],
        "train∩test": groups["train"] & groups["test"],
        "validation∩test": groups["validation"] & groups["test"],
    }
    if any(overlaps.values()):
        sys.exit(f"Scenario groups leak across splits: { {k: sorted(v) for k, v in overlaps.items() if v} }")
    print(f"Groups -> train: {len(groups['train'])}, validation: {len(groups['validation'])}, "
          f"test: {len(groups['test'])} (no overlap)")

    # ------------------------------------------------------------------
    # 3. Label matrix: one 0/1 column per catalog field
    # ------------------------------------------------------------------
    field_codes = load_field_codes(files["catalog"])
    for name, labels in [("train", y_train_labels), ("validation", y_val_labels), ("test", y_test_labels)]:
        check_labels_known(labels, field_codes, name)

    # classes= fixes the column order to the catalog order; fit([[]]) just
    # initialises the encoder without learning anything from the data.
    mlb = MultiLabelBinarizer(classes=field_codes)
    mlb.fit([[]])
    y_train = mlb.transform(y_train_labels)
    y_val = mlb.transform(y_val_labels)
    y_test = mlb.transform(y_test_labels)

    # Fields that are positive in every training example (or in none) can't be
    # learned. The model just outputs a constant for them. They're reported
    # here and later excluded from the "variable fields" metrics.
    positives = y_train.sum(axis=0)
    constant_fields = [f for f, n in zip(field_codes, positives) if n in (0, len(y_train))]

    print("\nTraining positives per field:")
    for field, count in zip(field_codes, positives):
        flag = "  <- constant in training, model cannot learn it" if field in constant_fields else ""
        print(f"  {field:24} {int(count):4}{flag}")

    # ------------------------------------------------------------------
    # 4. TF-IDF fitted on TRAINING text only
    # ------------------------------------------------------------------
    vectorizer = TfidfVectorizer(
        lowercase=True,
        ngram_range=(1, 2),   # single words and word pairs, e.g. "knee replacement"
        min_df=1,             # tiny dataset: keep rare words
        max_features=5000,
    )
    X_train = vectorizer.fit_transform(X_train_text)
    X_val = vectorizer.transform(X_val_text)     # transform only: no peeking at
    X_test = vectorizer.transform(X_test_text)   # validation/test vocabulary
    print(f"\nTF-IDF vocabulary size: {len(vectorizer.vocabulary_)}")

    # ------------------------------------------------------------------
    # 5. One logistic regression per field
    # ------------------------------------------------------------------
    classifier = OneVsRestClassifier(
        LogisticRegression(
            max_iter=2000,
            class_weight="balanced",   # upweight rare positives like WHEEZING
            random_state=RANDOM_SEED,
        )
    )
    with warnings.catch_warnings():
        # The "Label N is present in all training examples" warning is
        # expected (MEDICATION_ADHERENCE) and already reported above.
        warnings.filterwarnings("ignore", message="Label .* is present in all training examples")
        classifier.fit(X_train, y_train)
    print("Classifier trained.")

    # ------------------------------------------------------------------
    # 6-7. Choose threshold on VALIDATION, then freeze it
    # ------------------------------------------------------------------
    val_proba = classifier.predict_proba(X_val)
    print("\nThreshold search (validation only):")
    best_threshold, best_f1, threshold_table = None, -1.0, []
    for threshold in THRESHOLD_GRID:
        m = multilabel_metrics(y_val, (val_proba >= threshold).astype(int))
        threshold_table.append({"threshold": float(threshold), **m})
        print(f"  {threshold:.2f} | P {m['precision']:.3f} | R {m['recall']:.3f} | F1 {m['f1']:.3f}")
        # Strict ">" keeps the lowest threshold on ties, the same as the notebook.
        if m["f1"] > best_f1:
            best_f1, best_threshold = m["f1"], float(threshold)
    print(f"Chosen threshold: {best_threshold:.2f} (validation F1 {best_f1:.4f}) -> frozen")

    val_metrics = multilabel_metrics(y_val, (val_proba >= best_threshold).astype(int))

    # ------------------------------------------------------------------
    # 8-9. Score the untouched TEST split once
    # ------------------------------------------------------------------
    test_pred = (classifier.predict_proba(X_test) >= best_threshold).astype(int)
    test_metrics = multilabel_metrics(y_test, test_pred)
    variable_mask = np.array([f not in constant_fields for f in field_codes])
    test_metrics_variable = multilabel_metrics(y_test, test_pred, column_mask=variable_mask)

    print()
    print_metrics(f"CAREVOICE {args.tag.upper()} - TEST (all {len(field_codes)} fields)",
                  test_metrics, best_threshold)
    print_metrics(f"TEST - {int(variable_mask.sum())} variable fields only", test_metrics_variable)
    print(f"Note: test covers only {len(groups['test'])} scenario groups; read it as a sanity check.")

    # ------------------------------------------------------------------
    # 10. Save artifacts + metadata
    # ------------------------------------------------------------------
    model_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump(classifier, model_dir / "classifier.joblib")
    joblib.dump(vectorizer, model_dir / "tfidf_vectorizer.joblib")
    joblib.dump(mlb, model_dir / "label_encoder.joblib")

    metadata = {
        "modelTag": args.tag,
        "modelType": "TF-IDF (1-2 grams) + OneVsRest LogisticRegression(class_weight=balanced)",
        "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "threshold": best_threshold,
        "thresholdSelectedOn": "validation",
        "monitoringFields": field_codes,
        "constantTrainingFields": constant_fields,
        "counts": {
            "train": len(train_records), "validation": len(val_records), "test": len(test_records),
            "trainGroups": len(groups["train"]), "validationGroups": len(groups["validation"]),
            "testGroups": len(groups["test"]),
        },
        "dataFiles": {name: {"path": str(path), "sha256": file_sha256(path)} for name, path in files.items()},
        "metrics": {
            "validation": val_metrics,
            "test": test_metrics,
            "testVariableFieldsOnly": test_metrics_variable,
        },
        "thresholdSearch": threshold_table,
        # joblib files only load reliably with the same library versions.
        "environment": {
            "python": platform.python_version(),
            "scikit-learn": sklearn.__version__,
            "numpy": np.__version__,
            "scipy": scipy.__version__,
            "joblib": joblib.__version__,
        },
        "randomSeed": RANDOM_SEED,
        "caveat": "Synthetic prototype labels; not clinician-validated. Engineering benchmark only.",
    }
    with open(model_dir / "metadata.json", "w", encoding="utf-8") as file:
        json.dump(metadata, file, indent=2)

    print(f"\nSaved to {model_dir}:")
    for name in ["classifier.joblib", "tfidf_vectorizer.joblib", "label_encoder.joblib", "metadata.json"]:
        print(f"  {name}")


if __name__ == "__main__":
    main()
