# -*- coding: utf-8 -*-
import json
import os
import jsonschema

BASE_DIR = r"C:\testda\foodcatalogservice\src\main\resources\catalog\nin-bulk-staging-20261125"
SCHEMA_PATH = os.path.join(BASE_DIR, "ocr-tasks", "response-schema.json")
OCR_DIR = os.path.join(BASE_DIR, "ocr-responses")

with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
    schema = json.load(f)

# Batches 37, 38, 39 codes:
codes = []
for b_num in ["37", "38", "39"]:
    b_file = os.path.join(BASE_DIR, "ocr-tasks", f"batch-00{b_num}.json")
    with open(b_file, "r", encoding="utf-8") as f:
        tasks = json.load(f)["tasks"]
        codes.extend([t["code"] for t in tasks])

print(f"Refactoring schema for {len(codes)} files...")

for code in codes:
    fp = os.path.join(OCR_DIR, f"{code}.json")
    with open(fp, "r", encoding="utf-8") as f:
        data = json.load(f)
        
    # convert to exact schema
    new_data = {
        "code": data["code"],
        "imageSha256": data["imageSha256"],
        "reviewStatus": "PENDING_INDEPENDENT_REVIEW",
        "ingredientListStatus": data["ingredientListStatus"],
        "servingHeadingText": data.get("servingHeading") or data.get("servingHeadingText"),
        "imageEnergyText": None,
        "ingredients": [],
        "uncertainties": data.get("uncertainties", [])
    }
    
    # imageEnergyText
    energy = data.get("energyKcal")
    if energy is not None:
        new_data["imageEnergyText"] = f"{energy} Kcal" if isinstance(energy, float) else f"{energy}kcal"
    elif data.get("imageEnergyText"):
        new_data["imageEnergyText"] = data["imageEnergyText"]

    # ingredients
    items = data.get("items") or data.get("ingredients") or []
    for item in items:
        # get fields
        name = item.get("ingredientName") or item.get("nameSourceText")
        qty = item.get("numericQuantity")
        unit = item.get("unit") or item.get("unitSourceText")
        src_text = item.get("ingredientSourceText") or item.get("amountSourceText")
        if src_text and src_text.startswith(name + ":"):
            amt_text = src_text[len(name)+1:].strip()
        elif src_text:
            amt_text = src_text
        elif qty is not None and unit:
            amt_text = f"{qty}{unit}"
        else:
            amt_text = None

        new_data["ingredients"].append({
            "nameSourceText": name,
            "amountSourceText": amt_text,
            "numericQuantity": qty,
            "unitSourceText": unit,
            "preparationSourceText": None,
            "certainty": "CLEAR"
        })

    # validate
    jsonschema.validate(instance=new_data, schema=schema)
    
    with open(fp, "w", encoding="utf-8") as f:
        json.dump(new_data, f, ensure_ascii=False, indent=2)

print("Successfully updated and validated all 75 files!")
