# CareVoice field-selection classifier

TF-IDF + One-vs-Rest Logistic Regression that predicts which of the 22
monitoring fields belong in a patient's daily questionnaire. Java rules and a
doctor still review every plan. The labels are synthetic prototype rules, not
clinician-validated, so every score here is an engineering benchmark only.

## Setup (once, from the `carevoice-ai` folder, in PowerShell)

```powershell
py -3.13 -m venv ml\.venv
ml\.venv\Scripts\Activate.ps1
pip install -r ml\requirements.txt
```

## Run

```powershell
python ml\train_classifier.py       # train v1 -> ml\models\v1\
python ml\evaluate_classifier.py    # frozen challenge set -> ml\results\challenge_v1.json
```

Expected v1 numbers (reproduce the Kaggle run exactly):

| | Precision | Recall | F1 | Exact match | Hamming |
|---|---|---|---|---|---|
| Test (15 ex., 3 groups) | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 0.0000 |
| Challenge (10 cases) | 0.9792 | 0.9216 | 0.9495 | 0.6000 | 0.0455 |

## Files

| File | Purpose |
|---|---|
| `data/` | v1 dataset from Kaggle (train/validation/test JSONL + question catalog) |
| `carevoice_features.py` | Shared text formatting and metrics, used by both scripts |
| `train_classifier.py` | Train, tune the threshold on validation, score test, save artifacts |
| `evaluate_classifier.py` | Score a saved model on the frozen challenge set |
| `challenge_cases.json` | **Frozen** 10-case benchmark. Never train on it. |
| `models/<tag>/` | Saved artifacts + `metadata.json` (threshold, metrics, data hashes, versions) |
| `results/` | Challenge results per model version |

## Dataset v2

Put the v2 files in `ml\data_v2\`, then:

```powershell
python ml\train_classifier.py --tag v2 --data-dir ml\data_v2
python ml\evaluate_classifier.py --tag v2
```

`evaluate_classifier.py` refuses to score if any challenge case appears in
the model's training, validation or test data.
