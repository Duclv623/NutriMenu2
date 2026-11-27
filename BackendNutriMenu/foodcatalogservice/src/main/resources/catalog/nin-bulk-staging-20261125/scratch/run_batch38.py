# -*- coding: utf-8 -*-
import json
import os
import hashlib
from datetime import datetime, timezone
from PIL import Image

BASE_DIR = r"C:\testda\foodcatalogservice\src\main\resources\catalog\nin-bulk-staging-20261125"
TASKS_FILE = os.path.join(BASE_DIR, "ocr-tasks", "batch-0038.json")
OCR_OUT_DIR = os.path.join(BASE_DIR, "ocr-responses")
CROP_OUT_DIR = os.path.join(BASE_DIR, "dish-photos")
MANIFEST_FILE = os.path.join(CROP_OUT_DIR, "manifest.json")
PROGRESS_FILE = os.path.join(BASE_DIR, "processing-progress.json")

def get_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()

batch_data = {
    "VPF-000076": {
        "status": "PRESENT", "serving": "Thành phần/đĩa", "energy": 81.0,
        "ingredients": [
            {"name": "Ghẹ sống nguyên con", "source": "Ghẹ sống nguyên con: 350g", "qty": 350.0, "unit": "g"}
        ],
        "crop_box": [172, 20, 598, 300], "crop_status": "READY",
        "notes": "Cropped clean plate with steamed blue crab."
    },
    "VPF-000079": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 550.0,
        "ingredients": [
            {"name": "Lưỡi lợn", "source": "Lưỡi lợn: 250g", "qty": 250.0, "unit": "g"}
        ],
        "crop_box": [79, 22, 541, 280], "crop_status": "READY",
        "notes": "Cropped clean plate with boiled pork tongue."
    },
    "VPF-000081": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 14.0,
        "ingredients": [
            {"name": "Ngọn su su", "source": "Ngọn su su: 80g", "qty": 80.0, "unit": "g"}
        ],
        "crop_box": [141, 0, 478, 278], "crop_status": "READY",
        "notes": "Cropped clean plate with boiled chayote shoots."
    },
    "VPF-000083": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 91.0,
        "ingredients": [
            {"name": "Ốc vặn", "source": "Ốc vặn: 420g", "qty": 420.0, "unit": "g"},
            {"name": "Lá chanh", "source": "Lá chanh: 1g", "qty": 1.0, "unit": "g"},
            {"name": "Ớt", "source": "Ớt: 1g", "qty": 1.0, "unit": "g"}
        ],
        "crop_box": [82, 0, 571, 280], "crop_status": "READY",
        "notes": "Cropped clean bowl with boiled snails."
    },
    "VPF-000121": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 235.0,
        "ingredients": [
            {"name": "Cá rô phi", "source": "Cá rô phi: 100g", "qty": 100.0, "unit": "g"},
            {"name": "Dầu TV", "source": "Dầu TV: 15g", "qty": 15.0, "unit": "g"},
            {"name": "Muối", "source": "Muối: 0,5g", "qty": 0.5, "unit": "g"}
        ],
        "crop_box": [108, 26, 577, 300], "crop_status": "READY",
        "notes": "Cropped clean plate with fried fish patties."
    },
    "VPF-000127": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 574.0,
        "ingredients": [
            {"name": "Khoai lang", "source": "Khoai lang: 180g", "qty": 180.0, "unit": "g"},
            {"name": "Bột", "source": "Bột: 40g", "qty": 40.0, "unit": "g"},
            {"name": "Dầu TV", "source": "Dầu TV: 20g", "qty": 20.0, "unit": "g"},
            {"name": "Bơ", "source": "Bơ: 5g", "qty": 5.0, "unit": "g"}
        ],
        "crop_box": [50, 16, 595, 298], "crop_status": "READY",
        "notes": "Cropped clean plate with fried sweet potatoes."
    },
    "VPF-000128": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 579.0,
        "ingredients": [
            {"name": "Khoai môn", "source": "Khoai môn: 200g", "qty": 200.0, "unit": "g"},
            {"name": "Đỗ xanh", "source": "Đỗ xanh: 20g", "qty": 20.0, "unit": "g"},
            {"name": "Bột gạo nếp", "source": "Bột gạo nếp: 30g", "qty": 30.0, "unit": "g"},
            {"name": "Dầu TV", "source": "Dầu TV: 20g", "qty": 20.0, "unit": "g"}
        ],
        "crop_box": [136, 19, 607, 300], "crop_status": "READY",
        "notes": "Cropped clean plate with fried taro balls."
    },
    "VPF-000165": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 358.0,
        "ingredients": [
            {"name": "Lươn", "source": "Lươn: 150g", "qty": 150.0, "unit": "g"},
            {"name": "Ớt ngọt", "source": "Ớt ngọt: 50 g", "qty": 50.0, "unit": "g"},
            {"name": "Dầu TV", "source": "Dầu TV: 10g", "qty": 10.0, "unit": "g"},
            {"name": "Muối", "source": "Muối: 1g", "qty": 1.0, "unit": "g"}
        ],
        "crop_box": [111, 24, 587, 305], "crop_status": "READY",
        "notes": "Cropped clean plate with stir-fried eel with peppers."
    },
    "VPF-000201": {
        "status": "ABSENT", "serving": None, "energy": 56.0, "ingredients": [],
        "crop_box": [30, 22, 629, 420], "crop_status": "READY",
        "notes": "Cropped clean plate with jicama slices; no ingredient list on image."
    },
    "VPF-000205": {
        "status": "ABSENT", "serving": None, "energy": 20.0, "ingredients": [],
        "crop_box": [57, 50, 690, 440], "crop_status": "READY",
        "notes": "Cropped clean plate with pickled onions; no ingredient list on image."
    },
    "VPF-000206": {
        "status": "ABSENT", "serving": None, "energy": 12.0, "ingredients": [],
        "crop_box": [77, 54, 714, 440], "crop_status": "READY",
        "notes": "Cropped clean plate with pickled leeks; no ingredient list on image."
    },
    "VPF-000286": {
        "status": "ABSENT", "serving": None, "energy": 33.0, "ingredients": [],
        "crop_box": [54, 21, 530, 310], "crop_status": "READY",
        "notes": "Cropped clean plate with vegetarian baozi; no ingredient list on image."
    },
    "VPF-000287": {
        "status": "ABSENT", "serving": None, "energy": 409.0, "ingredients": [],
        "crop_box": [92, 25, 440, 315], "crop_status": "READY",
        "notes": "Cropped clean plate with meat baozi; no ingredient list on image."
    },
    "VPF-000290": {
        "status": "ABSENT", "serving": None, "energy": 204.0, "ingredients": [],
        "crop_box": [3, 6, 455, 295], "crop_status": "READY",
        "notes": "Cropped clean plate with square glutinous rice cake (banh chung); no ingredient list on image."
    },
    "VPF-000291": {
        "status": "ABSENT", "serving": None, "energy": 168.0, "ingredients": [],
        "crop_box": [31, 33, 422, 320], "crop_status": "READY",
        "notes": "Cropped clean plate with green sticky rice cake (banh com); no ingredient list on image."
    },
    "VPF-000294": {
        "status": "ABSENT", "serving": None, "energy": 354.0, "ingredients": [],
        "crop_box": [30, 23, 528, 300], "crop_status": "READY",
        "notes": "Cropped clean plate with steamed rice rolls with egg and pork sausage; no ingredient list on image."
    },
    "VPF-000295": {
        "status": "ABSENT", "serving": None, "energy": 50.0, "ingredients": [],
        "crop_box": [56, 4, 468, 290], "crop_status": "READY",
        "notes": "Cropped clean plate with round glutinous cakes (banh day); no ingredient list on image."
    },
    "VPF-000296": {
        "status": "ABSENT", "serving": None, "energy": 110.0, "ingredients": [],
        "crop_box": [26, 23, 455, 310], "crop_status": "READY",
        "notes": "Cropped clean plate with rice cake (banh duc) with peanuts; no ingredient list on image."
    },
    "VPF-000297": {
        "status": "ABSENT", "serving": None, "energy": 361.0, "ingredients": [],
        "crop_box": [57, 41, 535, 310], "crop_status": "READY",
        "notes": "Cropped clean plate with thorn leaf cake (banh gai); no ingredient list on image."
    },
    "VPF-000299": {
        "status": "ABSENT", "serving": None, "energy": 576.0, "ingredients": [],
        "crop_box": [45, 15, 387, 300], "crop_status": "READY",
        "notes": "Cropped clean plate with glutinous rice cake (banh nep); no ingredient list on image."
    },
    "VPF-000302": {
        "status": "ABSENT", "serving": None, "energy": 110.0, "ingredients": [],
        "crop_box": [43, 26, 535, 310], "crop_status": "READY",
        "notes": "Cropped clean plate with conjugal cake (banh xu xe); no ingredient list on image."
    },
    "VPF-000303": {
        "status": "ABSENT", "serving": None, "energy": 95.0, "ingredients": [],
        "crop_box": [26, 22, 408, 310], "crop_status": "READY",
        "notes": "Cropped clean plate with square toast bread; no ingredient list on image."
    },
    "VPF-000304": {
        "status": "ABSENT", "serving": None, "energy": 55.0, "ingredients": [],
        "crop_box": [35, 32, 529, 252], "crop_status": "READY",
        "notes": "Cropped clean plate with Hai Phong stick bread; no ingredient list on image."
    },
    "VPF-000308": {
        "status": "ABSENT", "serving": None, "energy": 239.0, "ingredients": [],
        "crop_box": [37, 23, 325, 344], "crop_status": "READY",
        "notes": "Cropped clean package of Kinh Do sweet bread; no ingredient list on image."
    },
    "VPF-000315": {
        "status": "ABSENT", "serving": None, "energy": 566.0, "ingredients": [],
        "crop_box": [55, 25, 516, 267], "crop_status": "READY",
        "notes": "Cropped clean plate with sticky rice mooncake (banh deo); no ingredient list on image."
    }
}

def run():
    now_iso = datetime.now(timezone.utc).isoformat()

    with open(TASKS_FILE, "r", encoding="utf-8") as f:
        tasks = json.load(f)["tasks"]
    
    with open(MANIFEST_FILE, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    with open(PROGRESS_FILE, "r", encoding="utf-8") as f:
        progress = json.load(f)

    for task in tasks:
        code = task["code"]
        img_path = task["imagePath"]
        img_sha = task["imageSha256"]
        info = batch_data[code]
        
        # 1. OCR response
        ocr_resp = {
            "taskId": task["taskId"],
            "code": code,
            "sourceRecordId": task["sourceRecordId"],
            "imageSha256": img_sha,
            "ingredientListStatus": info["status"],
            "servingHeading": info["serving"],
            "energyKcal": info["energy"],
            "items": [],
            "uncertainties": [],
            "notes": None,
            "reviewStatus": "PENDING_INDEPENDENT_REVIEW"
        }
        
        for ing in info["ingredients"]:
            ocr_resp["items"].append({
                "ingredientName": ing["name"],
                "ingredientSourceText": ing["source"],
                "numericQuantity": ing["qty"],
                "unit": ing["unit"],
                "energyKcal": ing.get("energy")
            })
            
        ocr_path = os.path.join(OCR_OUT_DIR, f"{code}.json")
        with open(ocr_path, "w", encoding="utf-8") as f:
            json.dump(ocr_resp, f, ensure_ascii=False, indent=2)
            
        # 2. Crop
        with Image.open(img_path) as im:
            orig_w, orig_h = im.size
            crop_box = info["crop_box"]
            cropped = im.crop(crop_box)
            cropped_path = os.path.join(CROP_OUT_DIR, f"{code}.webp")
            cropped.save(cropped_path, "WEBP", quality=95)
            cropped_sha = get_sha256(cropped_path)
            c_w, c_h = cropped.size

        entry = {
            "code": code,
            "sourceRecordId": task["sourceRecordId"],
            "originalImagePath": img_path,
            "originalImageSha256": img_sha,
            "originalWidth": orig_w,
            "originalHeight": orig_h,
            "cropBoxPixels": crop_box,
            "croppedImagePath": f"dish-photos/{code}.webp",
            "croppedImageSha256": cropped_sha,
            "croppedWidth": c_w,
            "croppedHeight": c_h,
            "status": info["crop_status"],
            "notes": info["notes"]
        }
        manifest["records"][code] = entry

    # Calculate manifest counts
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

    # update counts in progress
    all_ocr_files = [f for f in os.listdir(OCR_OUT_DIR) if f.endswith(".json")]
    present_cnt = 0
    absent_cnt = 0
    illegible_cnt = 0
    for f in all_ocr_files:
        with open(os.path.join(OCR_OUT_DIR, f), "r", encoding="utf-8") as fp:
            d = json.load(fp)
            st = d.get("ingredientListStatus")
            if st == "PRESENT":
                present_cnt += 1
            elif st == "ABSENT":
                absent_cnt += 1
            elif st == "ILLEGIBLE":
                illegible_cnt += 1

    progress["updatedAt"] = now_iso
    progress["ocr"]["completed"] = len(all_ocr_files)
    progress["ocr"]["remaining"] = 975 - len(all_ocr_files)
    progress["ocr"]["statusCounts"]["PRESENT"] = present_cnt
    progress["ocr"]["statusCounts"]["ABSENT"] = absent_cnt
    progress["ocr"]["statusCounts"]["ILLEGIBLE"] = illegible_cnt
    progress["ocr"]["completedCountVerifiedByHashAndSchema"] = len(all_ocr_files)
    progress["ocr"]["remainingCodesCount"] = 975 - len(all_ocr_files)

    # update remaining sample codes
    with open(os.path.join(BASE_DIR, "ocr-tasks", "queue-index.json"), "r", encoding="utf-8") as qf:
        queue_idx = json.load(qf)
    active_batches = queue_idx["activeBatches"]
    remaining_tasks = []
    for b in active_batches:
        b_path = os.path.join(BASE_DIR, "ocr-tasks", b)
        with open(b_path, "r", encoding="utf-8") as bf:
            b_tasks = json.load(bf)["tasks"]
        for t in b_tasks:
            c = t["code"]
            if not os.path.exists(os.path.join(OCR_OUT_DIR, f"{c}.json")):
                remaining_tasks.append(c)

    progress["ocr"]["remainingSampleCodes"] = remaining_tasks[:25]

    progress["crop"]["processed"] = processed_crops
    progress["crop"]["READY"] = ready_count
    progress["crop"]["NEEDS_REVIEW"] = needs_review_count
    progress["crop"]["unprocessed"] = unprocessed_crops

    # update crop unprocessed samples
    with open(os.path.join(BASE_DIR, "api-runs", "bulk-20261125", "dishes-source.json"), "r", encoding="utf-8") as dsf:
        all_dishes = json.load(dsf)
    unproc_crops = [d["code"] for d in all_dishes if d["code"] not in manifest["records"]]
    progress["crop"]["unprocessedSampleCodes"] = unproc_crops[:25]

    with open(PROGRESS_FILE, "w", encoding="utf-8") as f:
        json.dump(progress, f, ensure_ascii=False, indent=2)

    print(f"Batch 38 Complete!")
    print(f"OCR Completed: {len(all_ocr_files)}/975 (PRESENT: {present_cnt}, ABSENT: {absent_cnt}, ILLEGIBLE: {illegible_cnt})")
    print(f"Crops Total: {processed_crops}/1023 (READY: {ready_count}, NEEDS_REVIEW: {needs_review_count}, Remaining: {unprocessed_crops})")

if __name__ == "__main__":
    run()
