# -*- coding: utf-8 -*-
import json
import os

BASE_DIR = r"C:\testda\foodcatalogservice\src\main\resources\catalog\nin-bulk-staging-20261125"
with open(os.path.join(BASE_DIR, "api-runs", "bulk-20261125", "dishes-source.json"), "r", encoding="utf-8") as f:
    all_dishes = json.load(f)
with open(os.path.join(BASE_DIR, "dish-photos", "manifest.json"), "r", encoding="utf-8") as f:
    manifest = json.load(f)
with open(os.path.join(BASE_DIR, "image-collection.json"), "r", encoding="utf-8") as f:
    ic = json.load(f)

unprocessed = [d for d in all_dishes if d["code"] not in manifest["records"]]
print(f"Total unprocessed snapshot dishes: {len(unprocessed)}")
for i, d in enumerate(unprocessed):
    code = d["code"]
    img_info = ic["records"].get(code, {})
    img_path = img_info.get("localPath") or img_info.get("imagePath")
    # check if file exists in images/
    local_path = os.path.join(BASE_DIR, "images", f"{code}.webp")
    if not os.path.exists(local_path):
        local_path = os.path.join(BASE_DIR, "images", f"{code}.png")
    exists = os.path.exists(local_path)
    print(f"{i+1:2d}. {code:10s} | {d.get('name_vi'):35s} | exists: {exists} | path: {local_path} | status: {img_info.get('status')}")
