"""
Run a saved CareVoice classifier against the frozen challenge set.

What it does:
  1. Loads classifier / vectorizer / label encoder / metadata from ml/models/<tag>/.
  2. Warns if your installed scikit-learn differs from the one used for training.
  3. Loads ml/challenge_cases.json (the frozen benchmark).
  4. Contamination guard: stops if any challenge case appears word-for-word in
     the training, validation or test data the model was trained from.
  5. Predicts fields with the threshold frozen in metadata.json (never re-tuned here).
  6. Prints metrics plus the missing/extra fields for every case.
  7. Writes everything to ml/results/challenge_<tag>.json for the v1 vs v2 comparison.

Usage (from the carevoice-ai folder):
    python ml/evaluate_classifier.py              # evaluates ml/models/v1
    python ml/evaluate_classifier.py --tag v2
"""

import argparse
import json
import sys
from pathlib import Path

import joblib
import numpy as np
import sklearn

from carevoice_features import (
    conditions_to_text,
    file_sha256,
    ml_dir,
    multilabel_metrics,
    prepare_dataset,
    print_metrics,
    read_json,
    read_jsonl,
)


def parse_args():
    parser = argparse.ArgumentParser(description="Evaluate a saved CareVoice classifier on the challenge set.")
    parser.add_argument("--tag", default="v1", help="Model version under ml/models/ (default: v1).")
    parser.add_argument("--model-dir", default=None, help="Override the model folder.")
    parser.add_argument("--challenge", default=None, help="Override the challenge file (default: ml/challenge_cases.json).")
    return parser.parse_args()


def main():
    args = parse_args()
    base = ml_dir()
    model_dir = Path(args.model_dir) if args.model_dir else base / "models" / args.tag
    challenge_path = Path(args.challenge) if args.challenge else base / "challenge_cases.json"

    # ------------------------------------------------------------------
    # 1-2. Load artifacts and check library versions
    # ------------------------------------------------------------------
    if not (model_dir / "metadata.json").exists():
        sys.exit(f"No model at {model_dir}. Run: python ml/train_classifier.py --tag {args.tag}")

    metadata = read_json(model_dir / "metadata.json")
    classifier = joblib.load(model_dir / "classifier.joblib")
    vectorizer = joblib.load(model_dir / "tfidf_vectorizer.joblib")
    mlb = joblib.load(model_dir / "label_encoder.joblib")
    threshold = float(metadata["threshold"])
    field_codes = list(mlb.classes_)

    trained_with = metadata.get("environment", {}).get("scikit-learn")
    if trained_with and trained_with != sklearn.__version__:
        print(f"WARNING: model trained with scikit-learn {trained_with}, "
              f"running {sklearn.__version__}. Retrain, or install the pinned version.\n")

    # ------------------------------------------------------------------
    # 3. Load the frozen challenge set
    # ------------------------------------------------------------------
    challenge = read_json(challenge_path)
    cases = challenge["cases"]
    texts = [conditions_to_text(case["conditions"]) for case in cases]

    unknown = sorted({f for c in cases for f in c["expectedFields"]} - set(field_codes))
    if unknown:
        sys.exit(f"Challenge file uses fields the model doesn't know: {unknown}")

    # ------------------------------------------------------------------
    # 4. Contamination guard: no challenge text in the model's own data
    # ------------------------------------------------------------------
    data_texts = set()
    for name in ("train", "validation", "test"):
        info = metadata.get("dataFiles", {}).get(name)
        if info and Path(info["path"]).exists():
            data_texts.update(prepare_dataset(read_jsonl(info["path"]))[0])
    leaked = [c["id"] for c, t in zip(cases, texts) if t in data_texts]
    if leaked:
        sys.exit(f"Challenge cases found in the model's data: {leaked}. "
                 "The benchmark is contaminated, so the score would not be meaningful.")

    # ------------------------------------------------------------------
    # 5. Predict with the FROZEN threshold
    # ------------------------------------------------------------------
    probabilities = classifier.predict_proba(vectorizer.transform(texts))
    y_pred = (probabilities >= threshold).astype(int)
    y_true = mlb.transform([c["expectedFields"] for c in cases])

    constant_fields = set(metadata.get("constantTrainingFields", []))
    variable_mask = np.array([f not in constant_fields for f in field_codes])

    metrics_all = multilabel_metrics(y_true, y_pred)
    metrics_variable = multilabel_metrics(y_true, y_pred, column_mask=variable_mask)

    # ------------------------------------------------------------------
    # 6. Report
    # ------------------------------------------------------------------
    print(f"Model: {model_dir}   Challenge: {challenge.get('benchmarkId')} ({len(cases)} cases)\n")
    per_case = []
    for case, true_row, pred_row, proba_row in zip(cases, y_true, y_pred, probabilities):
        expected = {f for f, v in zip(field_codes, true_row) if v}
        predicted = {f for f, v in zip(field_codes, pred_row) if v}
        missing, extra = sorted(expected - predicted), sorted(predicted - expected)
        # Probabilities of the wrong fields show how close each miss was to the threshold.
        near = {f: round(float(p), 3) for f, p in zip(field_codes, proba_row) if f in missing or f in extra}
        per_case.append({
            "id": case["id"],
            "conditions": conditions_to_text(case["conditions"]),
            "exactMatch": not missing and not extra,
            "missing": missing,
            "extra": extra,
            "errorProbabilities": near,
            "predicted": sorted(predicted),
        })
        status = "OK  " if not missing and not extra else "MISS"
        print(f"[{status}] {case['id']}  {conditions_to_text(case['conditions'])}")
        if missing:
            print(f"        missing: {', '.join(f'{f} ({near[f]:.2f})' for f in missing)}")
        if extra:
            print(f"        extra:   {', '.join(f'{f} ({near[f]:.2f})' for f in extra)}")

    print()
    print_metrics(f"CHALLENGE - {args.tag.upper()} (all {len(field_codes)} fields)", metrics_all, threshold)
    print_metrics(f"CHALLENGE - {int(variable_mask.sum())} variable fields only", metrics_variable)

    reference = challenge.get("v1Reference")
    if reference:
        print("\nv1 Kaggle reference (all fields):  "
              f"P {reference['precision']:.4f}  R {reference['recall']:.4f}  F1 {reference['f1']:.4f}  "
              f"Exact {reference['exact_set_match']:.4f}  Hamming {reference['hamming_loss']:.4f}")

    # ------------------------------------------------------------------
    # 7. Save results
    # ------------------------------------------------------------------
    results_dir = base / "results"
    results_dir.mkdir(exist_ok=True)
    out_path = results_dir / f"challenge_{args.tag}.json"
    with open(out_path, "w", encoding="utf-8") as file:
        json.dump({
            "modelTag": args.tag,
            "benchmarkId": challenge.get("benchmarkId"),
            "challengeSha256": file_sha256(challenge_path),
            "threshold": threshold,
            "metrics": metrics_all,
            "metricsVariableFieldsOnly": metrics_variable,
            "cases": per_case,
            "caveat": challenge.get("labelSource"),
        }, file, indent=2)
    print(f"\nSaved {out_path}")


if __name__ == "__main__":
    main()
