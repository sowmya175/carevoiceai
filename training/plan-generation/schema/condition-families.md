# Condition family vocabulary

Dataset version: `carevoice-plan-generation-v1`

Condition families are model-output labels that group similar monitoring context.
They are not diagnoses, ICD codes, or billing codes.
They do not change escalation rules. `EscalationEngine` stays authoritative.

| Label | Use |
| --- | --- |
| `GENERAL_WELLNESS` | Routine wellness check-in context |
| `CARDIAC_SURGERY_RECOVERY` | Open-heart, bypass, or valve surgery recovery wording |
| `ORTHOPEDIC_SURGERY_RECOVERY` | Knee, hip, or shoulder surgery recovery wording |
| `ABDOMINAL_SURGERY_RECOVERY` | Abdominal surgery recovery wording |
| `HYPERTENSION` | Hypertension or high blood pressure monitoring wording |
| `TYPE_1_DIABETES` | Type 1 diabetes monitoring wording |
| `TYPE_2_DIABETES` | Type 2 diabetes monitoring wording |
| `CARDIAC` | Cardiac monitoring context that is not a surgery-recovery example |
| `RESPIRATORY` | COPD, asthma, or other chronic respiratory monitoring wording |
| `OTHER` | Underspecified context, or a challenge example whose free-text name and clinician category disagree |

When a challenge example marks a category/name mismatch, the target family is `OTHER`.
The target questions still follow the clinician-supplied category.
The dataset does not teach the model to correct or diagnose that mismatch.
