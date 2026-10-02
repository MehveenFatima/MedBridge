#!/usr/bin/env python3
"""
Generate MedBridge test data: the same synthetic patients in three feed formats.

    LAB       -> sample-data/lab_export.csv    (CSV)
    CLINIC    -> sample-data/clinic_feed.json  (JSON)
    HOSPITAL  -> sample-data/hospital_adt.hl7  (HL7 v2: MSH + PID segments)

Everything here is invented. No real patient data is used or needed.

What the data is designed to exercise:
  * Each patient is registered with 1, 2 or 3 sources, so cross-source
    deduplication has overlapping records to match.
  * ~15% of records in every feed carry one deliberate defect
    (blank MRN, blank last name, impossible date, future date of birth,
    unknown gender code, short phone number, truncated HL7 segment),
    so validation and error logging have something to catch.
  * A few LAB rows are repeated with a different phone number or a different
    date of birth (same source, same MRN), and a few CLINIC rows disagree with
    the LAB phone number (different source, same person), so the
    keep-existing-and-log conflict path runs.

Usage:
    python3 tools/generate_test_data.py              # writes to ./sample-data
    python3 tools/generate_test_data.py --out other/ # custom output folder
"""
import argparse
import csv
import json
import random
from datetime import date, timedelta
from pathlib import Path

SEED = 7
N_PATIENTS = 200
DEFECT_RATE = 0.15
LAB_DUPLICATES = 5
CLINIC_PHONE_CONFLICTS = 5
LAB_DOB_CONFLICTS = 2

FIRST = ["Zoya", "Aarav", "Priya", "Rohan", "Sneha", "Vikram", "Ananya",
         "Karthik", "Divya", "Arjun", "Fatima", "Imran", "Nisha", "Rahul",
         "Pooja", "Sameer", "Zara", "Aditya", "Kavya", "Farhan", "Meera",
         "Siddharth", "Ayesha", "Harsha"]
LAST = ["Fatima", "Sharma", "Reddy", "Patel", "Nair", "Iyer", "Khan",
        "Gupta", "Rao", "Desai", "Menon", "Joshi", "Verma", "Pillai",
        "Siddiqui", "Kulkarni", "Chowdary", "Banerjee", "Mehta", "Hussain"]
CITIES = [("Hyderabad", "TS", "500001"), ("Bengaluru", "KA", "560001"),
          ("Chennai", "TN", "600001"), ("Mumbai", "MH", "400001"),
          ("Pune", "MH", "411001"), ("Delhi", "DL", "110001")]

COMMON_DEFECTS = ["blank_mrn", "blank_last", "bad_date", "future_dob",
                  "bad_gender", "short_phone"]
HL7_DEFECTS = COMMON_DEFECTS + ["truncated"]

BAD_DATE = "BAD_DATE"            # marker, rendered per format below
FUTURE_DOB = date(2030, 5, 12)


def random_phone(rng):
    return "9" + "".join(rng.choice("0123456789") for _ in range(9))


def build_patients(rng):
    patients = []
    for i in range(N_PATIENTS):
        city, state, zipc = rng.choice(CITIES)
        patients.append({
            "mrn": str(10000 + i),
            "first": rng.choice(FIRST),
            "last": rng.choice(LAST),
            "dob": date(1950, 1, 1) + timedelta(days=rng.randint(0, 26000)),
            "gender": rng.choice(["M", "F"]),
            "phone": random_phone(rng),
            "city": city, "state": state, "zip": zipc,
        })
    # 70% of patients appear in one source, 25% in two, 5% in all three
    for p in patients:
        k = rng.choices([1, 2, 3], weights=[70, 25, 5])[0]
        p["sources"] = rng.sample(["LAB", "CLINIC", "HOSPITAL"], k)
    return patients


def apply_defect(record, defect):
    r = dict(record)
    r["defect"] = defect
    if defect == "blank_mrn":
        r["mrn"] = ""
    elif defect == "blank_last":
        r["last"] = ""
    elif defect == "bad_date":
        r["dob"] = BAD_DATE
    elif defect == "future_dob":
        r["dob"] = FUTURE_DOB
    elif defect == "bad_gender":
        r["gender"] = "Z"
    elif defect == "short_phone":
        r["phone"] = "12345"
    # "truncated" is applied when the HL7 line is rendered
    return r


def make_view(rng, patient, source):
    """The record as one source sees it: clean, or carrying one defect."""
    if rng.random() < DEFECT_RATE:
        pool = HL7_DEFECTS if source == "HOSPITAL" else COMMON_DEFECTS
        return apply_defect(patient, rng.choice(pool))
    r = dict(patient)
    r["defect"] = None
    return r


def fmt_date(d, pattern, bad_value):
    return bad_value if d == BAD_DATE else d.strftime(pattern)


def csv_row(r):
    return [r["mrn"], r["first"], r["last"],
            fmt_date(r["dob"], "%Y-%m-%d", "1998-13-45"),
            r["gender"], r["phone"], r["city"], r["state"], r["zip"]]


def json_obj(r):
    sex = {"M": "Male", "F": "Female"}.get(r["gender"], r["gender"])
    return {
        "patientId": r["mrn"],
        "given_name": r["first"],
        "family_name": r["last"],
        "dob": fmt_date(r["dob"], "%d/%m/%Y", "45/13/1998"),
        "sex": sex,
        "contact": "+91-" + r["phone"],
        "address": {"city": r["city"], "state": r["state"], "postalCode": r["zip"]},
    }


def hl7_message(r, n):
    msh = (f"MSH|^~\\&|CITYHOSP|CENTRAL|MEDBRIDGE|CLINIC|20260920120000||"
           f"ADT^A01|MSG{n:05d}|P|2.5")
    name = f"{r['last'].upper()}^{r['first'].upper()}^"
    if r["defect"] == "truncated":
        # segment cut off after the name: no DOB, gender, address or phone
        pid = f"PID|1||{r['mrn']}^^^HOSP^MR||{name}"
    else:
        dob = fmt_date(r["dob"], "%Y%m%d", "19981345")
        pid = (f"PID|1||{r['mrn']}^^^HOSP^MR||{name}||{dob}|{r['gender']}|||"
               f"{r['city']}^{r['state']}^{r['zip']}||{r['phone']}")
    return [msh, pid, ""]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    default_out = Path(__file__).resolve().parent.parent / "sample-data"
    parser.add_argument("--out", type=Path, default=default_out)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    rng = random.Random(SEED)
    patients = build_patients(rng)

    views = {"LAB": [], "CLINIC": [], "HOSPITAL": []}
    for p in patients:
        for source in ("LAB", "CLINIC", "HOSPITAL"):
            if source in p["sources"]:
                views[source].append(make_view(rng, p, source))

    # Same source, same MRN, different phone -> exercises "same source" matching + conflict log
    clean_lab = [v for v in views["LAB"] if v["defect"] is None]
    for v in rng.sample(clean_lab, LAB_DUPLICATES):
        dup = dict(v)
        dup["phone"] = random_phone(rng)
        views["LAB"].append(dup)

    # Same person in LAB and CLINIC with a different phone -> cross-source conflict log
    clean_lab_mrns = {v["mrn"] for v in clean_lab}
    candidates = [v for v in views["CLINIC"]
                  if v["defect"] is None and v["mrn"] in clean_lab_mrns]
    for v in rng.sample(candidates, min(CLINIC_PHONE_CONFLICTS, len(candidates))):
        v["phone"] = random_phone(rng)

    # Same source, same MRN, DIFFERENT DATE OF BIRTH (the lab re-sends a record with a
    # changed birth date) -> the existing date must be kept and the conflict logged
    for v in rng.sample(clean_lab, LAB_DOB_CONFLICTS):
        dup = dict(v)
        dup["dob"] = v["dob"] + timedelta(days=rng.randint(1, 400))
        views["LAB"].append(dup)

    # ---------- write the three feeds ----------
    with open(args.out / "lab_export.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["mrn", "first_name", "last_name", "date_of_birth",
                    "gender", "phone", "city", "state", "postal_code"])
        for r in views["LAB"]:
            w.writerow(csv_row(r))

    with open(args.out / "clinic_feed.json", "w") as f:
        json.dump([json_obj(r) for r in views["CLINIC"]], f, indent=2)

    lines = []
    for n, r in enumerate(views["HOSPITAL"], start=1):
        lines.extend(hl7_message(r, n))
    with open(args.out / "hospital_adt.hl7", "w") as f:
        f.write("\n".join(lines))

    # ---------- summary ----------
    total = sum(len(v) for v in views.values())
    defective = sum(1 for v in views.values() for r in v if r["defect"])
    multi = sum(1 for p in patients if len(p["sources"]) > 1)
    print(f"Patients generated        : {N_PATIENTS}")
    print(f"Patients in 2+ sources    : {multi}")
    for source, file in (("LAB", "lab_export.csv"), ("CLINIC", "clinic_feed.json"),
                         ("HOSPITAL", "hospital_adt.hl7")):
        bad = sum(1 for r in views[source] if r["defect"])
        print(f"{source:<9} {file:<18}: {len(views[source]):>3} records, {bad:>2} with a defect")
    print(f"Total records             : {total}")
    print(f"Records with a defect     : {defective} ({defective / total:.1%})")
    print(f"Written to                : {args.out}")


if __name__ == "__main__":
    main()
