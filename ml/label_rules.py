"""
CareVoice prototype labelling rules (Dataset v2).

Every label in the dataset follows one rule:

    a patient's fields = the fields of each active condition, combined

so this file is the single place where "which condition gets which fields"
is decided. build_dataset_v2.py uses it to label new examples and to check
that every existing example is still consistent.

These are prototype engineering rules, NOT clinician-validated protocols.

Change log
----------
v1 -> v2: COPD now includes WHEEZING and RESCUE_INHALER_USE (previously only
          asthma had them). Decided 2026-10-14 after the challenge review found
          the v1 data and the frozen challenge set disagreed on COPD.
"""

# ---------------------------------------------------------------------------
# Condition family -> clinician category + monitoring fields
# ---------------------------------------------------------------------------

FAMILIES = {
    "WELLNESS": {
        "category": "WELLNESS",
        "fields": ["OVERALL_WELLBEING", "MEDICATION_ADHERENCE", "SLEEP_QUALITY", "APPETITE", "PAIN_SCORE"],
    },
    "HYPERTENSION": {
        "category": "HYPERTENSION",
        "fields": ["MEDICATION_ADHERENCE", "BLOOD_PRESSURE", "OVERALL_WELLBEING"],
    },
    "TYPE_2_DIABETES": {
        "category": "DIABETES",
        "fields": ["MEDICATION_ADHERENCE", "BLOOD_GLUCOSE", "APPETITE", "OVERALL_WELLBEING"],
    },
    "ASTHMA": {
        "category": "RESPIRATORY",
        "fields": ["MEDICATION_ADHERENCE", "SHORTNESS_OF_BREATH", "WHEEZING", "RESCUE_INHALER_USE",
                   "ACTIVITY_TOLERANCE"],
    },
    "COPD": {
        "category": "RESPIRATORY",
        # v2: WHEEZING and RESCUE_INHALER_USE added.
        "fields": ["MEDICATION_ADHERENCE", "SHORTNESS_OF_BREATH", "COUGH_MUCUS_CHANGE", "OXYGEN_SATURATION",
                   "ACTIVITY_TOLERANCE", "WHEEZING", "RESCUE_INHALER_USE"],
    },
    "CHRONIC_CARDIAC": {
        "category": "CARDIAC",
        "fields": ["MEDICATION_ADHERENCE", "SHORTNESS_OF_BREATH", "SWELLING", "WEIGHT", "FATIGUE",
                   "ACTIVITY_TOLERANCE"],
    },
    "OPEN_HEART_SURGERY": {
        "category": "POST_OPERATIVE",
        "fields": ["MEDICATION_ADHERENCE", "PAIN_SCORE", "INCISION_STATUS", "TEMPERATURE", "SHORTNESS_OF_BREATH",
                   "SWELLING", "ACTIVITY_TOLERANCE"],
    },
    "CABG": {
        "category": "POST_OPERATIVE",
        # Note: no MOBILITY. That is for knee/hip recovery only.
        "fields": ["MEDICATION_ADHERENCE", "PAIN_SCORE", "INCISION_STATUS", "TEMPERATURE", "SHORTNESS_OF_BREATH",
                   "SWELLING", "ACTIVITY_TOLERANCE", "WEIGHT"],
    },
    "HEART_VALVE_SURGERY": {
        "category": "POST_OPERATIVE",
        "fields": ["MEDICATION_ADHERENCE", "PAIN_SCORE", "INCISION_STATUS", "TEMPERATURE", "SHORTNESS_OF_BREATH",
                   "SWELLING", "ACTIVITY_TOLERANCE", "WEIGHT"],
    },
    "KNEE_REPLACEMENT": {
        "category": "POST_OPERATIVE",
        "fields": ["MEDICATION_ADHERENCE", "PAIN_SCORE", "INCISION_STATUS", "TEMPERATURE", "SWELLING", "MOBILITY",
                   "ACTIVITY_TOLERANCE"],
    },
    "HIP_REPLACEMENT": {
        "category": "POST_OPERATIVE",
        "fields": ["MEDICATION_ADHERENCE", "PAIN_SCORE", "INCISION_STATUS", "TEMPERATURE", "SWELLING", "MOBILITY",
                   "ACTIVITY_TOLERANCE"],
    },
    "SHOULDER_SURGERY": {
        "category": "POST_OPERATIVE",
        "fields": ["MEDICATION_ADHERENCE", "PAIN_SCORE", "INCISION_STATUS", "TEMPERATURE", "SWELLING",
                   "SHOULDER_ARM_FUNCTION"],
    },
    "ABDOMINAL_SURGERY": {
        "category": "POST_OPERATIVE",
        "fields": ["MEDICATION_ADHERENCE", "PAIN_SCORE", "APPETITE", "NAUSEA_VOMITING", "BOWEL_FUNCTION",
                   "INCISION_STATUS", "TEMPERATURE"],
    },
}

# ---------------------------------------------------------------------------
# Training wordings: the 5 phrasings v1 used for each family.
# Variant k of an example uses wording k, so the 5 paraphrases of a scenario
# look the same as v1's.
# ---------------------------------------------------------------------------

WORDINGS = {
    "WELLNESS": ["General wellness", "Routine wellness monitoring", "Daily wellness check",
                 "General health follow-up", "Wellness check-in"],
    "HYPERTENSION": ["Hypertension", "High blood pressure", "Chronic hypertension", "Blood pressure monitoring",
                     "Hypertension follow-up"],
    "TYPE_2_DIABETES": ["Type 2 diabetes", "Type 2 diabetes mellitus", "T2D", "Type 2 diabetes follow-up",
                        "Ongoing type 2 diabetes monitoring"],
    "ASTHMA": ["Asthma", "Chronic asthma", "Asthma follow-up", "Ongoing asthma monitoring",
               "Asthma symptom monitoring"],
    "COPD": ["COPD", "Chronic obstructive pulmonary disease", "COPD follow-up", "Ongoing COPD monitoring",
             "Chronic lung disease - COPD"],
    "CHRONIC_CARDIAC": ["Chronic cardiac condition", "Chronic heart condition", "Long-term cardiac condition",
                        "Ongoing cardiac condition", "Cardiac condition follow-up"],
    "OPEN_HEART_SURGERY": ["Open-heart surgery recovery", "Recovering after open-heart surgery",
                           "Post-op open-heart surgery recovery", "Open-heart procedure recovery",
                           "Recovery following open-heart surgery"],
    "CABG": ["CABG recovery", "Recovering after bypass surgery", "Coronary artery bypass graft recovery",
             "Post-op CABG recovery", "Recovery after coronary bypass surgery"],
    "HEART_VALVE_SURGERY": ["Heart valve surgery recovery", "Recovering after heart valve surgery",
                            "Post-op valve surgery recovery", "Recovery following cardiac valve surgery",
                            "Heart valve procedure recovery"],
    "KNEE_REPLACEMENT": ["Total knee replacement recovery", "Recovering after knee replacement",
                         "Post-op total knee replacement", "TKR recovery",
                         "Recovery following total knee arthroplasty"],
    "HIP_REPLACEMENT": ["Total hip replacement recovery", "Recovering after hip replacement",
                        "Post-op total hip replacement", "THR recovery",
                        "Recovery following total hip arthroplasty"],
    "SHOULDER_SURGERY": ["Shoulder surgery recovery", "Recovering after shoulder surgery",
                         "Post-op shoulder procedure recovery", "Shoulder operation recovery",
                         "Recovery following shoulder surgery"],
    "ABDOMINAL_SURGERY": ["Abdominal surgery recovery", "Recovering after abdominal surgery",
                          "Post-op abdominal surgery recovery", "Recovery following abdominal surgery",
                          "Abdominal operation recovery"],
}


def family_of(condition_name):
    """
    Map a v1 condition wording back to its family. Used to re-check and
    relabel the existing v1 examples. Raises if a wording is not recognised,
    so nothing is silently mislabelled.
    """
    for family, names in WORDINGS.items():
        if condition_name in names:
            return family
    raise KeyError(f"Unknown condition wording: {condition_name!r}")


def fields_for(families, field_order):
    """
    The labelling rule: union of every condition's fields, returned in
    catalog order so files are easy to diff.
    """
    selected = set()
    for family in families:
        selected.update(FAMILIES[family]["fields"])
    return [f for f in field_order if f in selected]
