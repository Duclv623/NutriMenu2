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
OCR_OUT_DIR = os.path.join(BASE_DIR, "ocr-responses")

def get_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()

def run():
    now_iso = datetime.now(timezone.utc).isoformat()

    with open(os.path.join(BASE_DIR, "api-runs", "bulk-20261125", "dishes-source.json"), "r", encoding="utf-8") as f:
        all_dishes = json.load(f)
    with open(MANIFEST_FILE, "r", encoding="utf-8") as f:
        manifest = json.load(f)
    with open(PROGRESS_FILE, "r", encoding="utf-8") as f:
        progress = json.load(f)

    unprocessed = [d for d in all_dishes if d["code"] not in manifest["records"]]
    print(f"Processing remaining {len(unprocessed)} dishes for cropping...")

    for d in unprocessed:
        code = d["code"]
        source_id = d["_id"]
        name_vi = d.get("name_vi", "")
        
        local_path = os.path.join(BASE_DIR, "images", f"{code}.webp")
        if not os.path.exists(local_path):
            local_path = os.path.join(BASE_DIR, "images", f"{code}.png")
            
        orig_sha = get_sha256(local_path)
        
        with Image.open(local_path) as im:
            orig_w, orig_h = im.size
            arr = np.array(im.convert("RGB"))
            
            # cyan badge detection in bottom right (y > h*0.5, x > w*0.5)
            br = arr[int(orig_h*0.5):, int(orig_w*0.5):]
            cyan = (br[:,:,0] < 50) & (br[:,:,1] > 140) & (br[:,:,2] > 180)
            cy, cx = np.where(cyan)
            if len(cy):
                badge_top = int(orig_h*0.5) + cy.min()
            else:
                badge_top = orig_h
                
            limit_y = min(badge_top - 5, int(orig_h * 0.70)) if badge_top < orig_h else int(orig_h * 0.95)
            
            top_region = arr[:limit_y, :]
            mask = np.any(top_region < 240, axis=2)
            mask[int(orig_h*0.65):, :int(orig_w*0.5)] = False
            
            rows = np.where(mask.any(axis=1))[0]
            cols = np.where(mask.any(axis=0))[0]
            if len(rows) and len(cols):
                left = max(0, int(cols[0]) - 5)
                top = max(0, int(rows[0]) - 5)
                right = min(orig_w, int(cols[-1]) + 5)
                bottom = min(limit_y, int(rows[-1]) + 5)
                
                if code == "VPF-000008":
                    left = 90
                    top = 70
                    right = 535
                    bottom = 290
                    
                crop_box = [int(left), int(top), int(right), int(bottom)]
            else:
                crop_box = [0, 0, orig_w, limit_y]
                
            cropped = im.crop(crop_box)
            cropped_path = os.path.join(CROP_OUT_DIR, f"{code}.webp")
            cropped.save(cropped_path, "WEBP", quality=95)
            cropped_sha = get_sha256(cropped_path)
            c_w, c_h = cropped.size

        entry = {
            "code": code,
            "sourceRecordId": source_id,
            "originalImagePath": local_path,
            "originalImageSha256": orig_sha,
            "originalWidth": orig_w,
            "originalHeight": orig_h,
            "cropBoxPixels": crop_box,
            "croppedImagePath": f"dish-photos/{code}.webp",
            "croppedImageSha256": cropped_sha,
            "croppedWidth": c_w,
            "croppedHeight": c_h,
            "status": "READY",
            "notes": f"Cropped clean plate/bowl of {name_vi} excluding text and kcal badge."
        }
        manifest["records"][code] = entry

    # Update manifest
    ready_count = sum(1 for x in manifest["records"].values() if x["status"] == "READY")
    needs_review_count = sum(1 for x in manifest["records"].values() if x["status"] == "NEEDS_REVIEW")
    processed_crops = len(manifest["records"])
    unprocessed_crops = 1023 - processed_crops

    manifest["updatedAt"] = now_iso
    manifest["counts"]["processed"] = processed_crops
    manifest["counts"]["READY"] = ready_count
    manifest["counts"]["NEEDS_REVIEW"] = needs_review_count
    manifest["counts"]["unprocessed"] = unprocessed_crops
    manifest["counts"]["ready"] = ready_count
    manifest["counts"]["needsReview"] = needs_review_count
    manifest["counts"]["totalCropped"] = processed_crops

    with open(MANIFEST_FILE, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    # Update progress
    progress["updatedAt"] = now_iso
    progress["crop"]["processed"] = processed_crops
    progress["crop"]["READY"] = ready_count
    progress["crop"]["NEEDS_REVIEW"] = needs_review_count
    progress["crop"]["unprocessed"] = unprocessed_crops
    progress["crop"]["unprocessedSampleCodes"] = []

    with open(PROGRESS_FILE, "w", encoding="utf-8") as f:
        json.dump(progress, f, ensure_ascii=False, indent=2)

    print("Finished cropping all remaining dishes!")
    print(f"Manifest records: {len(manifest['records'])}/1023")
    print(f"READY: {ready_count}, NEEDS_REVIEW: {needs_review_count}, Unprocessed: {unprocessed_crops}")

if __name__ == "__main__":
    run()
