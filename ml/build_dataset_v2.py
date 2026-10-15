"""
Build CareVoice Dataset v2 from the v1 data, reproducibly.

    python ml/build_dataset_v2.py      # reads ml/data/, writes ml/data_v2/

What changes from v1, and why
-----------------------------
1. COPD relabel. Every example containing COPD gets WHEEZING and
   RESCUE_INHALER_USE (decision recorded in label_rules.py).

2. CABG moved into training. v1's only CABG scenario (CV-SME-008) sat in the
   validation split, so the model never saw "CABG" or "bypass" while
   training. In v2 that group moves to train.

3. New scenario groups (5 paraphrases each, like v1):
     CABG-focused     CABG + other conditions, teaching WEIGHT and
                      SHORTNESS_OF_BREATH and no MOBILITY
     COPD-focused     COPD in new combinations, with the new v2 COPD labels
     counterexamples  post-op recoveries other than knee/hip, so MOBILITY isn't
                      learned as "any surgery"

   One new COPD group goes to validation, replacing the CABG group that moved
   to train, so validation stays at 3 groups / 15 examples.

4. Test split: same 3 groups as v1, COPD-relabelled only.

Safety checks (the script stops if any fail)
--------------------------------------------
- Every label in every split equals the rule in label_rules.py.
- No scenario group appears in more than one split.
- No new group repeats a condition combination from either challenge set,
  and no example text matches a challenge case word-for-word.
"""

import json
import shutil
import sys

from carevoice_features import conditions_to_text, load_field_codes, ml_dir, read_json, read_jsonl
from label_rules import FAMILIES, WORDINGS, family_of, fields_for

BASE = ml_dir()
SRC = BASE / "data"
DST = BASE / "data_v2"

# The group moved from validation to train (see point 2 above).
MOVE_TO_TRAIN = {"CV-SME-008"}

# New groups: (groupId, split, [families, primary first], purpose)
NEW_GROUPS = [
    # CABG-focused (plus CV-SME-008 moved to train = 5 CABG groups)
    ("CV-V2-031", "train", ["CABG", "HYPERTENSION"], "CABG + hypertension"),
    ("CV-V2-032", "train", ["CABG", "CHRONIC_CARDIAC"], "CABG + chronic cardiac"),
    ("CV-V2-033", "train", ["CABG", "ASTHMA"], "CABG + asthma"),
    ("CV-V2-034", "train", ["CABG", "HYPERTENSION", "CHRONIC_CARDIAC"], "CABG triple"),
    # COPD-focused (on top of the 4 relabelled v1 COPD groups)
    ("CV-V2-035", "train", ["COPD", "KNEE_REPLACEMENT"], "COPD + knee"),
    ("CV-V2-036", "train", ["COPD", "SHOULDER_SURGERY"], "COPD + shoulder"),
    ("CV-V2-037", "validation", ["COPD", "CHRONIC_CARDIAC", "HYPERTENSION"], "COPD triple (validation)"),
    # Counterexamples: surgery recovery WITHOUT mobility
    ("CV-V2-038", "train", ["ABDOMINAL_SURGERY", "TYPE_2_DIABETES"], "abdominal + diabetes, no mobility"),
    ("CV-V2-039", "train", ["OPEN_HEART_SURGERY", "CHRONIC_CARDIAC"], "open-heart + cardiac, no mobility"),
    ("CV-V2-040", "train", ["HEART_VALVE_SURGERY", "HYPERTENSION"], "valve + hypertension, no mobility"),
    ("CV-V2-041", "train", ["SHOULDER_SURGERY", "CHRONIC_CARDIAC"], "shoulder + cardiac, no mobility"),
]


def record_parts(record):
    """Pull the user payload and selected fields out of a chat-format record."""
    user = next(m for m in record["messages"] if m["role"] == "user")
    assistant = next(m for m in record["messages"] if m["role"] == "assistant")
    return json.loads(user["content"]), json.loads(assistant["content"])["selectedFields"]


def set_fields(record, fields):
    for message in record["messages"]:
        if message["role"] == "assistant":
            message["content"] = json.dumps({"selectedFields": fields})


def make_record(template, group_id, variant, families, field_order):
    """
    Build one example in exactly the v1 format, reusing a v1 record's system
    prompt and allowedFields. Condition i in variant k uses wording (k + i) % 5,
    the same rotation v1 used for its multi-condition paraphrases.
    """
    user_payload, _ = record_parts(template)
    conditions = [
        {"name": WORDINGS[f][(variant + i) % 5], "category": FAMILIES[f]["category"], "primary": i == 0}
        for i, f in enumerate(families)
    ]
    system = next(m for m in template["messages"] if m["role"] == "system")
    return {
        "exampleId": f"{group_id}-V{variant + 1}",
        "groupId": group_id,
        "messages": [
            dict(system),
            {"role": "user", "content": json.dumps({"conditions": conditions,
                                                     "allowedFields": user_payload["allowedFields"]})},
            {"role": "assistant", "content": json.dumps({"selectedFields": fields_for(families, field_order)})},
        ],
    }


def main():
    field_order = load_field_codes(SRC / "question_catalog.json")
    splits = {name: read_jsonl(SRC / f"{name}.jsonl") for name in ("train", "validation", "test")}
    template = splits["train"][0]

    # --- 1. Relabel COPD + 2. move CABG ---------------------------------
    relabelled = 0
    out = {"train": [], "validation": [], "test": []}
    for name, records in splits.items():
        for record in records:
            payload, old_fields = record_parts(record)
            families = [family_of(c["name"]) for c in payload["conditions"]]
            new_fields = fields_for(families, field_order)
            if set(new_fields) != set(old_fields):
                # Only the COPD change is expected; anything else is a bug.
                added, removed = set(new_fields) - set(old_fields), set(old_fields) - set(new_fields)
                if "COPD" not in families or removed or added != {"WHEEZING", "RESCUE_INHALER_USE"}:
                    sys.exit(f"Unexpected label change in {record['exampleId']}: +{added} -{removed}")
                set_fields(record, new_fields)
                relabelled += 1
            target = "train" if record["groupId"] in MOVE_TO_TRAIN else name
            out[target].append(record)

    # --- 3. Contamination checks against both challenge sets ------------
    challenge_sets, challenge_texts = [], set()
    for path in (BASE / "challenge_cases.json", BASE / "challenge_cases_fresh.json"):
        for case in read_json(path)["cases"]:
            challenge_sets.append(frozenset(family_of_any(c["name"], case) for c in case["conditions"]))
            challenge_texts.add(conditions_to_text(case["conditions"]))
    for group_id, _, families, _ in NEW_GROUPS:
        if frozenset(families) in challenge_sets:
            sys.exit(f"{group_id} repeats a challenge condition combination: {families}")

    # --- 3b. Add new groups ---------------------------------------------
    for group_id, split, families, _ in NEW_GROUPS:
        for variant in range(5):
            out[split].append(make_record(template, group_id, variant, families, field_order))

    # --- Final checks ---------------------------------------------------
    groups = {name: {r["groupId"] for r in recs} for name, recs in out.items()}
    for a, b in [("train", "validation"), ("train", "test"), ("validation", "test")]:
        if groups[a] & groups[b]:
            sys.exit(f"Group leak {a}/{b}: {groups[a] & groups[b]}")
    for name, records in out.items():
        for record in records:
            payload, fields = record_parts(record)
            if conditions_to_text(payload["conditions"]) in challenge_texts:
                sys.exit(f"{record['exampleId']} matches a challenge case word-for-word")
            expected = fields_for([family_of(c["name"]) for c in payload["conditions"]], field_order)
            if set(fields) != set(expected):
                sys.exit(f"{record['exampleId']} breaks the labelling rule")

    # --- Write ------------------------------------------------------------
    DST.mkdir(exist_ok=True)
    for name, records in out.items():
        with open(DST / f"{name}.jsonl", "w", encoding="utf-8", newline="\n") as file:
            for record in records:
                file.write(json.dumps(record, ensure_ascii=False) + "\n")
    shutil.copyfile(SRC / "question_catalog.json", DST / "question_catalog.json")

    manifest = {
        "datasetVersion": "carevoice-training-v2",
        "builtFrom": "carevoice-training-v1 (ml/data) by ml/build_dataset_v2.py",
        "labelRules": "ml/label_rules.py",
        "changes": [
            f"COPD relabel: +WHEEZING +RESCUE_INHALER_USE on {relabelled} existing examples",
            "CV-SME-008 (CABG alone) moved from validation to train",
            f"{len(NEW_GROUPS)} new scenario groups x 5 paraphrases",
        ],
        "newGroups": [{"groupId": g, "split": s, "families": f, "purpose": p} for g, s, f, p in NEW_GROUPS],
        "counts": {name: len(records) for name, records in out.items()},
        "groupCounts": {name: len(g) for name, g in groups.items()},
        "synthetic": True,
        "clinicallyValidated": False,
    }
    with open(DST / "manifest.json", "w", encoding="utf-8") as file:
        json.dump(manifest, file, indent=2)

    print(f"Relabelled COPD examples: {relabelled}")
    for name in ("train", "validation", "test"):
        print(f"{name:10} {len(out[name]):4} examples  {len(groups[name]):3} groups")
    print(f"All labels follow label_rules.py; no leaks; no challenge overlap.\nWritten to {DST}")


def family_of_any(name, case):
    """Challenge cases use some wordings that aren't in the training lists. For
    those, the fresh file records the family explicitly."""
    try:
        return family_of(name)
    except KeyError:
        index = [c["name"] for c in case["conditions"]].index(name)
        return case["families"][index]


if __name__ == "__main__":
    main()
