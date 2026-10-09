# CareVoice plan-generation dataset

Dataset version: `carevoice-plan-generation-v1`

The dataset is synthetic/curated development data and is not a medically validated clinical protocol.

It teaches a future planning model to read active conditions, choose a subset of the fields supplied in that example, merge duplicate needs, and return one structured daily questionnaire. The canonical files remain the source of truth. Provider JSONL is generated from them and is not a second dataset.

## Objective

```
conditions + allowed fields
        → one deduplicated list of routine questions
```

The model input is the `input` object. The supervised target is the `output` object. `review` is for dataset maintenance and is not model input.

`output` uses the same property names as `PlanGenerationResult` / `GeneratedQuestion`:

- `conditionFamilies`
- `questions[].fieldCode`
- `questions[].questionText`
- `questions[].required`
- `questions[].relevantConditionIds`
- `questions[].rationale`

`input.conditions` uses `conditionName`, `category`, `primary`, and an example-local `patientConditionId`. Those ids are not production patient identifiers. The files do not contain `patientId`, names, usernames, or account data.

## Allowed input fields

Every executable example lists the fields the model may select. v1 plan-askable codes are:

- `PAIN_SCORE`
- `MEDICATION_TAKEN`
- `APPETITE`
- `SLEEP_QUALITY`
- `TEMPERATURE`
- `DIZZINESS_ONSET`
- `LOSS_OF_CONSCIOUSNESS`

The last two are follow-up fields the daily check-in can store. They are not routine training targets. Escalation stays in `EscalationEngine`.

These codes are not selectable outputs in v1: `DIZZINESS`, `SHORTNESS_OF_BREATH`, `INCISION_STATUS`, `SWELLING`, `MOBILITY`, `ACTIVITY_TOLERANCE`, `BLOOD_PRESSURE`, `GLUCOSE`, `WEIGHT`, `HEART_RATE`, `OXYGEN_SATURATION`. See `future_examples/catalog-expansion.json`. That file is not part of any split.

## Target output

Routine targets use this order, and every routine question has `required: true`:

1. `PAIN_SCORE`
2. `MEDICATION_TAKEN`
3. `APPETITE`
4. `SLEEP_QUALITY`
5. `TEMPERATURE`

Inclusion is the union of the active conditions, then one question per field code:

| Clinician category | Routine fields |
| --- | --- |
| `POST_OPERATIVE` | pain, medication, appetite, sleep |
| `HYPERTENSION` | medication |
| `DIABETES` | medication, appetite |
| `CARDIAC` | medication |
| `RESPIRATORY` | medication |
| `WELLNESS` | pain, appetite, sleep |
| `OTHER` | medication |

`TEMPERATURE` is added only for curated cardiac-surgery, abdominal-surgery, and some wellness examples. Orthopedic recovery examples omit it so the model does not select every available field. A field is emitted only when it is also present in that example's `allowedFields`.

Question text is one neutral sentence per field. It does not diagnose, change medication, or set an emergency threshold.

Condition families are documented in `schema/condition-families.md`.

## Multi-condition examples

Two or three active conditions produce one plan. Overlapping medication needs become one `MEDICATION_TAKEN` question. `relevantConditionIds` lists every example-local condition that contributed that field. Primary and additional conditions both contribute. Some examples reverse which condition is primary. Question order stays the routine priority above.

## Paraphrases

Related wording for the same situation shares a `groupId`. The splitter keeps a group in one of train, validation, or test so a paraphrase does not land on both sides of the evaluation.

## Privacy boundary

Examples are fictional. They are not exported from CareVoice patient rows. `PlanPhiScanner` rejects identity field names and email-shaped text. That scanner is not a substitute for formal privacy review.

## Unsupported fields and thin catalogs

Post-operative examples do not output incision, swelling, mobility, or activity fields. Hypertension examples do not output blood pressure. Diabetes examples do not output glucose. Sparse examples restrict `allowedFields` and the target drops any routine field that was not supplied. When none of the relevant routine fields are allowed, the target question list is empty.

A `no-good-fit` tag marks two cases: the allowed catalog omits a field the full routine catalog would have selected, or every condition is category `OTHER`. A one-question hypertension or cardiac plan is selective field choice, not a missing fit.

## Category and name mismatch

Challenge examples include a clinician category that does not match the free-text condition. The target family is `OTHER`. Questions follow the supplied category. The target does not relabel the condition as a diagnosis.

## Splits

`splits/train.json`, `splits/validation.json`, and `splits/test.json` partition the canonical ids. The challenge ids in `splits/challenge.json` are outside that partition. Placement is deterministic: larger groups first, then the split with the most remaining room. Ties prefer train, then validation, then test. Counts are in `manifest.json`.

## Validation

```
mvn -Dtest=PlanDatasetTest,PlanGenerationEvaluatorTest,PlanCatalogAlignmentTest test
```

`mvn test` loads the checked-in JSON, validates every example, deserializes each output into `PlanGenerationResult`, and scores the deterministic generator on the test split. It does not call Gemini.

To regenerate the checked-in files after an intentional corpus edit:

```
$env:CAREVOICE_EXPORT_DATASET="true"
mvn -Dtest=PlanDatasetTest test
```

Bump `carevoice-plan-generation-v1` when the field catalog, output schema, or family vocabulary changes. Do not silently rewrite published examples.

## Evaluation

`PlanGenerationEvaluator` accepts any `PlanGenerationModel`. Metrics are field precision, field recall, field F1, duplicate-field rate, unknown-field rate, unsupported-field rate, structured-output validity, condition-family accuracy, required-flag accuracy, and a rule-based wording screen. Exact wording match is not the score.

The checked-in deterministic baseline is `evaluation/deterministic-baseline.json`. The review summary is `evaluation/dataset-review.md`.

Live provider evaluation is off unless `CAREVOICE_LIVE_PLAN_EVAL_ENABLED=true`. The guard command does not upload this dataset, call Gemini, or submit a tuning job.

## Fine-tuning

Task contract version: `carevoice-plan-task-v1`, in `schema/task-contract.md`.

114 canonical examples are enough to try the tuning path, compare it with the deterministic baseline, and see where the dataset should grow. They are not a basis for calling the model clinically deployed. Do not add real approved patient proposals to this dataset.

Google Cloud supervised fine-tuning currently documents Gemini 3.5 Flash, Gemini 3.1 Flash-Lite, Gemini 2.5 Pro, Gemini 2.5 Flash, and Gemini 2.5 Flash-Lite. The development default is `gemini-3.5-flash`. `gemini-3.8-flash` stays the extraction, notes, and wording model. It is not the plan tuning base.

Plan generation has its own settings: `CAREVOICE_PLAN_AI_ENABLED`, `CAREVOICE_PLAN_MODEL_PROVIDER`, `CAREVOICE_PLAN_BASE_MODEL`, `CAREVOICE_PLAN_TUNED_MODEL`, `CAREVOICE_PLAN_PROJECT_ID`, `CAREVOICE_PLAN_LOCATION`, `CAREVOICE_PLAN_AI_TIMEOUT_MS`, and `CAREVOICE_PLAN_FALLBACK_TO_BASE`. Leave the plan switch off to keep the deterministic generator. Set the provider to `gemini-base` for the temporary base model, or `vertex-tuned` plus an immutable tuned model resource after a manual promotion. `latest` is rejected. Vertex calls use Application Default Credentials. Do not put a service account file in this repository.

Commands, from the repository root:

```
mvn -q -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.carevoice.training.PlanTuningCommand -Dexec.args="validate"
mvn -q -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.carevoice.training.PlanTuningCommand -Dexec.args="export"
```

`export` and `submit` without confirmation validate the dataset, write `provider-export/train.jsonl` and `validation.jsonl`, and stop. Those files and `runs/` are gitignored. The training file contains the train split only. The validation file contains the validation split only. Test and challenge examples are not exported.

Upload those JSONL files to Cloud Storage yourself, then submit only with both the flag and the environment gate:

```
$env:CAREVOICE_PLAN_TUNING_SUBMIT="true"
$env:CAREVOICE_PLAN_PROJECT_ID="your-project-id"
$env:CAREVOICE_PLAN_LOCATION="us-central1"
$env:CAREVOICE_PLAN_BASE_MODEL="gemini-3.5-flash"
$env:CAREVOICE_PLAN_TRAINING_GCS_URI="gs://your-bucket/train.jsonl"
$env:CAREVOICE_PLAN_VALIDATION_GCS_URI="gs://your-bucket/validation.jsonl"
mvn -q -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.carevoice.training.PlanTuningCommand -Dexec.args="submit --submit"
```

A submitted run writes `runs/<run-id>/run.json` with dataset version, task-contract version, base model, counts, project, location, and the job resource. It does not store an access token or API key. `status` reads local files unless `CAREVOICE_PLAN_TUNING_STATUS=true` is set on a deliberate refresh. `evaluate` writes the deterministic comparison and does not call a tuned model. To score a tuned model on the test split and, separately, the challenge split, set `CAREVOICE_LIVE_PLAN_EVAL_ENABLED=true` plus `CAREVOICE_PLAN_TUNED_MODEL`, `CAREVOICE_PLAN_PROJECT_ID`, and `CAREVOICE_PLAN_LOCATION`. That command uses Application Default Credentials and the same `PlanGenerationEvaluator`. It does not upload data or submit a job. `mvn test` does not set that flag.

Promotion is manual. A development pass requires unknown-field rate 0, unsupported-field rate 0, structured validity 1, and duplicate-field rate 0 on both the test split and the challenge split. The resulting sentence is "Passed CareVoice development evaluation gates." Field precision and recall are reported for review. They are not a clinical certification. Copy the approved resource into `CAREVOICE_PLAN_TUNED_MODEL` and set `CAREVOICE_PLAN_MODEL_PROVIDER=vertex-tuned` yourself. Completion of a job does not change the running generator.

Rollback is configuration. `CAREVOICE_PLAN_AI_ENABLED=false` returns to the deterministic generator. `CAREVOICE_PLAN_MODEL_PROVIDER=gemini-base` returns to the temporary base model. Approved monitoring plans stay in the database either way.

If the tuned model times out, returns invalid JSON, or selects an unknown or non-askable field, the patient's current plan is left unchanged and the clinician sees a generation error. Set `CAREVOICE_PLAN_FALLBACK_TO_BASE=true` to try the base plan model once after that failure. There is no silent switch to the deterministic generator while plan AI is enabled. The model call is outside the database transaction. The proposal stores the condition snapshot that was sent to the model. If those conditions change before or after save, the clinician UI reports: "Patient conditions have changed since this plan was generated."

The first tuning run was not submitted by the test suite.

## Clinician review

These targets are development supervision for field selection. They need clinician review before any fine-tune. They are not a protocol for cardiac, orthopedic, diabetes, or respiratory care.

## Later catalog growth

When runtime support exists, a new dataset version may add `INCISION_STATUS`, `SWELLING`, `MOBILITY`, `ACTIVITY_TOLERANCE`, `BLOOD_PRESSURE`, `GLUCOSE`, `WEIGHT`, `HEART_RATE`, or `OXYGEN_SATURATION` to `allowedFields` and to targets. v1 executable outputs do not depend on them.
