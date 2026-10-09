# Plan generation dataset review

Dataset version: carevoice-plan-generation-v1

The dataset is synthetic/curated development data and is not a medically validated clinical protocol.

## Counts

- Total canonical examples: 114
- Single-condition: 63
- Multi-condition: 51
- Challenge examples, held out of every split: 12

## Examples by number of conditions

- 1: 63
- 2: 44
- 3: 7

## Examples by category

An example is counted once for each category it contains.

- CARDIAC: 11
- DIABETES: 33
- HYPERTENSION: 41
- OTHER: 7
- POST_OPERATIVE: 51
- RESPIRATORY: 18
- WELLNESS: 11

## Output field frequency

- PAIN_SCORE: 60
- MEDICATION_TAKEN: 107
- APPETITE: 75
- SLEEP_QUALITY: 60
- TEMPERATURE: 31

## Condition family frequency

- GENERAL_WELLNESS: 11
- CARDIAC_SURGERY_RECOVERY: 18
- ORTHOPEDIC_SURGERY_RECOVERY: 24
- ABDOMINAL_SURGERY_RECOVERY: 9
- HYPERTENSION: 41
- TYPE_1_DIABETES: 5
- TYPE_2_DIABETES: 28
- CARDIAC: 11
- RESPIRATORY: 18
- OTHER: 7

## Review tags

- Duplicate-merging examples: 46
- Unsupported-field omission examples: 103
- No-good-fit examples: 8
- Sparse-catalog examples: 5
- Selective examples: 83
- Paraphrase examples: 113
- Primary-reversal examples: 20

Schema validation: pass. Unsupported field violations in canonical outputs: 0. Duplicate field violations: 0.

## Deterministic baseline on the test split

- Examples: 10
- Field precision: 1.0000
- Field recall: 1.0000
- Field F1: 1.0000
- Duplicate field rate: 0.0000
- Unknown field rate: 0.0000
- Unsupported field rate: 0.0000
- Structured output validity: 1.0000
- Condition family accuracy: 0.2000
- Required-flag accuracy: 1.0000
- Wording safety rate: 1.0000

The deterministic generator is the Phase 6.5B workflow stand-in. It is not the training target. Test-split field agreement can be high when a group's curated field union matches category-tag overlap. Condition-family labels still use the training vocabulary.

## Deterministic baseline on the challenge set

- Examples: 12
- Field precision: 0.7391
- Field recall: 1.0000
- Field F1: 0.8500
- Duplicate field rate: 0.0000
- Unknown field rate: 0.0000
- Unsupported field rate: 0.0000
- Structured output validity: 1.0000
- Condition family accuracy: 0.2500
- Required-flag accuracy: 1.0000
- Wording safety rate: 1.0000
