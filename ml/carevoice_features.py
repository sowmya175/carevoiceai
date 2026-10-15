"""
Shared helpers for the CareVoice field-selection classifier.

Both train_classifier.py and evaluate_classifier.py import from here, so the
condition text is built the same way at training time and at evaluation time.
If the two scripts formatted text differently, the TF-IDF vocabulary would
not line up and the scores would be wrong.
"""

import hashlib
import json
from pathlib import Path

import numpy as np
from sklearn.metrics import (
    accuracy_score,
    f1_score,
    hamming_loss,
    precision_score,
    recall_score,
)


# ---------------------------------------------------------------------------
# Loading files
# ---------------------------------------------------------------------------

def read_jsonl(path):
    """Read a JSONL file: one JSON object per line. Blank lines are skipped."""
    records = []
    with open(path, "r", encoding="utf-8") as file:
        for line in file:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


def read_json(path):
    with open(path, "r", encoding="utf-8") as file:
        return json.load(file)


def file_sha256(path):
    """
    Fingerprint a file. Stored in metadata so you can tell later exactly which
    data a model was trained on (and spot it if a file was edited).
    """
    digest = hashlib.sha256()
    with open(path, "rb") as file:
        for chunk in iter(lambda: file.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_field_codes(catalog_path):
    """
    The 22 field codes, in catalog order. This order defines the column order
    of every label vector, so it must come from the catalog, never from
    whatever happens to appear in the training data.
    """
    catalog = read_json(catalog_path)
    return [field["fieldCode"] for field in catalog["fields"]]


# ---------------------------------------------------------------------------
# Turning records into (text, labels)
# ---------------------------------------------------------------------------

def conditions_to_text(conditions):
    """
    Turn a list of condition dicts into the single text string the model reads.

    This is exactly the format the Kaggle v1 notebook used, for example:
        PRIMARY CONDITION: CABG recovery CATEGORY: POST_OPERATIVE |
        ADDITIONAL CONDITION: Type 2 diabetes CATEGORY: DIABETES

    Keep it unchanged while comparing v1 and v2. Changing it changes the
    features, which would make it a different experiment.
    """
    parts = []
    for condition in conditions:
        role = "PRIMARY" if condition["primary"] else "ADDITIONAL"
        parts.append(
            f"{role} CONDITION: {condition['name']} "
            f"CATEGORY: {condition['category']}"
        )
    return " | ".join(parts)


def _message_content(record, role):
    """Return the content of the first chat message with the given role."""
    for message in record["messages"]:
        if message["role"] == role:
            return message["content"]
    raise ValueError(f"{record.get('exampleId')}: no '{role}' message")


def parse_record(record):
    """
    One training record -> (condition text, list of expected field codes).

    The records are stored in chat format (system / user / assistant), left over
    from the LLM plan. The user message holds the conditions as JSON and the
    assistant message holds {"selectedFields": [...]}.
    """
    user_data = json.loads(_message_content(record, "user"))
    output_data = json.loads(_message_content(record, "assistant"))
    return conditions_to_text(user_data["conditions"]), output_data["selectedFields"]


def prepare_dataset(records):
    """Parse a list of records into parallel lists of texts and label lists."""
    texts, labels = [], []
    for record in records:
        text, fields = parse_record(record)
        texts.append(text)
        labels.append(fields)
    return texts, labels


def check_labels_known(label_lists, field_codes, source):
    """
    Stop if a label isn't in the catalog. MultiLabelBinarizer would otherwise
    drop it with only a warning, and the scores would quietly be wrong.
    """
    known = set(field_codes)
    unknown = sorted({f for fields in label_lists for f in fields} - known)
    if unknown:
        raise ValueError(f"{source}: field codes not in question_catalog.json: {unknown}")


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------

def multilabel_metrics(y_true, y_pred, column_mask=None):
    """
    The five numbers used throughout the handoff.

    - precision / recall / F1 are "micro" averaged: every individual
      (patient, field) decision counts once, pooled across all fields.
    - exact_set_match: share of patients whose whole field set is exactly right.
    - hamming_loss: share of individual field decisions that are wrong.

    column_mask (optional): a boolean array that keeps only some fields. It is
    used to report scores without always-positive fields such as
    MEDICATION_ADHERENCE, which make the numbers look slightly better than the
    model has earned.
    """
    y_true = np.asarray(y_true)
    y_pred = np.asarray(y_pred)
    if column_mask is not None:
        y_true = y_true[:, column_mask]
        y_pred = y_pred[:, column_mask]

    return {
        "precision": float(precision_score(y_true, y_pred, average="micro", zero_division=0)),
        "recall": float(recall_score(y_true, y_pred, average="micro", zero_division=0)),
        "f1": float(f1_score(y_true, y_pred, average="micro", zero_division=0)),
        "exact_set_match": float(accuracy_score(y_true, y_pred)),
        "hamming_loss": float(hamming_loss(y_true, y_pred)),
    }


def print_metrics(title, metrics, threshold=None):
    print("=" * 46)
    print(f"  {title}")
    print("=" * 46)
    print(f"Precision:       {metrics['precision']:.4f}")
    print(f"Recall:          {metrics['recall']:.4f}")
    print(f"F1 Score:        {metrics['f1']:.4f}")
    print(f"Exact Set Match: {metrics['exact_set_match']:.4f}")
    print(f"Hamming Loss:    {metrics['hamming_loss']:.4f}")
    if threshold is not None:
        print(f"Threshold:       {threshold:.2f}")
    print("=" * 46)


def ml_dir():
    """The ml/ folder (where this file lives), so the scripts work from any directory."""
    return Path(__file__).resolve().parent
