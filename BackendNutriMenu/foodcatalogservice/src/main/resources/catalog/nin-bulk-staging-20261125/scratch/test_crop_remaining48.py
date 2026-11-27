# -*- coding: utf-8 -*-
import json
import os
import hashlib
from datetime import datetime, timezone
from PIL import Image
import numpy as np

BASE_DIR = r"C:\testda\foodcatalogservice\src\main\resources\catalog\nin-bulk-staging-20261125"
MANIFEST_FILE = os.path.join(BASE_DIR, "dish-photos", "manifest.json")
PROGRESS_FILE = os.path.join(BASE_DIR, "processing-progress.json")
CROP_OUT_DIR = os.path.join(BASE_DIR, "dish-photos")

with open(os.path.join(BASE_DIR, "api-runs", "bulk-20261125", "dishes-source.json"), "r", encoding="utf-8") as f:
    all_dishes = json.load(f)
with open(MANIFEST_FILE, "r", encoding="utf-8") as f:
    manifest = json.load(f)
with open(os.path.join(BASE_DIR, "image-collection.json"), "r", encoding="utf-8") as f:
    ic = json.load(f)

unprocessed = [d for d in all_dishes if d["code"] not in manifest["records"]]

print(f"Testing crops for {len(unprocessed)} dishes...")

crop_boxes = {}
for d in unprocessed:
    code = d["code"]
    local_path = os.path.join(BASE_DIR, "images", f"{code}.webp")
    if not os.path.exists(local_path):
        local_path = os.path.join(BASE_DIR, "images", f"{code}.png")
        
    im = Image.open(local_path).convert("RGB")
    arr = np.array(im)
    h, w, _ = arr.shape
    
    # cyan badge detection in bottom right (y > h*0.5, x > w*0.5)
    br = arr[int(h*0.5):, int(w*0.5):]
    cyan = (br[:,:,0] < 50) & (br[:,:,1] > 140) & (br[:,:,2] > 180)
    cy, cx = np.where(cyan)
    if len(cy):
        badge_top = int(h*0.5) + cy.min()
    else:
        badge_top = h
        
    limit_y = min(badge_top - 5, int(h * 0.70)) if badge_top < h else int(h * 0.95)
    
    top_region = arr[:limit_y, :]
    mask = np.any(top_region < 240, axis=2)
    # mask bottom-left table if any
    mask[int(h*0.65):, :int(w*0.5)] = False
    
    rows = np.where(mask.any(axis=1))[0]
    cols = np.where(mask.any(axis=0))[0]
    if len(rows) and len(cols):
        # Specific tuning for dishes
        left = max(0, int(cols[0]) - 5)
        top = max(0, int(rows[0]) - 5)
        right = min(w, int(cols[-1]) + 5)
        bottom = min(limit_y, int(rows[-1]) + 5)
        
        # for VPF-000008:
        if code == "VPF-000008":
            left = 90
            top = 70
            right = 535
            bottom = 290
            
        box = [left, top, right, bottom]
    else:
        box = [0, 0, w, limit_y]
        
    crop = im.crop(box)
    c_arr = np.array(crop)
    c_cyan = (c_arr[:,:,0] < 50) & (c_arr[:,:,1] > 140) & (c_arr[:,:,2] > 180)
    crop_boxes[code] = (box, local_path, d.get("_id"), d.get("name_vi"))
    if np.sum(c_cyan) > 0:
        print(f"WARNING: {code} has cyan bleed: {np.sum(c_cyan)}")

print("All crop boxes computed!")
