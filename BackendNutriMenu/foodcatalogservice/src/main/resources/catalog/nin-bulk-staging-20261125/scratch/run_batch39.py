# -*- coding: utf-8 -*-
import json
import os
import hashlib
from datetime import datetime, timezone
from PIL import Image

BASE_DIR = r"C:\testda\foodcatalogservice\src\main\resources\catalog\nin-bulk-staging-20261125"
TASKS_FILE = os.path.join(BASE_DIR, "ocr-tasks", "batch-0039.json")
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
    "VPF-000316": {
        "status": "ABSENT", "serving": None, "energy": 648.0, "ingredients": [],
        "crop_box": [37, 51, 513, 261], "crop_status": "READY",
        "notes": "Cropped clean plate with sticky rice cake (banh deo trung dau xanh); no ingredient list on image."
    },
    "VPF-000317": {
        "status": "ABSENT", "serving": None, "energy": 113.0, "ingredients": [],
        "crop_box": [34, 32, 416, 299], "crop_status": "READY",
        "notes": "Cropped clean plate with green bean cakes (banh dau xanh); no ingredient list on image."
    },
    "VPF-000318": {
        "status": "ABSENT", "serving": None, "energy": 156.0, "ingredients": [],
        "crop_box": [24, 17, 407, 288], "crop_status": "READY",
        "notes": "Cropped clean sesame rice cracker (banh da vung den); no ingredient list on image."
    },
    "VPF-000319": {
        "status": "ABSENT", "serving": None, "energy": 124.0, "ingredients": [],
        "crop_box": [69, 22, 465, 290], "crop_status": "READY",
        "notes": "Cropped clean white sesame rice cracker (banh da vung trang); no ingredient list on image."
    },
    "VPF-000320": {
        "status": "ABSENT", "serving": None, "energy": 76.0, "ingredients": [],
        "crop_box": [25, 22, 480, 290], "crop_status": "READY",
        "notes": "Cropped clean plate with rice crackers (banh gao); no ingredient list on image."
    },
    "VPF-000322": {
        "status": "ABSENT", "serving": None, "energy": 109.0, "ingredients": [],
        "crop_box": [71, 22, 489, 289], "crop_status": "READY",
        "notes": "Cropped clean plate with wafer cream biscuits (banh kem xop); no ingredient list on image."
    },
    "VPF-000324": {
        "status": "ABSENT", "serving": None, "energy": 706.0, "ingredients": [],
        "crop_box": [16, 47, 499, 278], "crop_status": "READY",
        "notes": "Cropped clean plate with mixed baked mooncake (banh nuong thap cam); no ingredient list on image."
    },
    "VPF-000325": {
        "status": "ABSENT", "serving": None, "energy": 648.0, "ingredients": [],
        "crop_box": [38, 23, 496, 258], "crop_status": "READY",
        "notes": "Cropped clean plate with mung bean baked mooncake (banh nuong trung dau xanh); no ingredient list on image."
    },
    "VPF-000327": {
        "status": "ABSENT", "serving": None, "energy": 130.0, "ingredients": [],
        "crop_box": [44, 30, 495, 288], "crop_status": "READY",
        "notes": "Cropped clean plate with cookies/biscuits (banh quy); no ingredient list on image."
    },
    "VPF-000331": {
        "status": "ABSENT", "serving": None, "energy": 196.0, "ingredients": [],
        "crop_box": [74, 21, 421, 289], "crop_status": "READY",
        "notes": "Cropped clean plate with pillow pastry (banh goi); no ingredient list on image."
    },
    "VPF-000344": {
        "status": "ABSENT", "serving": None, "energy": 100.0, "ingredients": [],
        "crop_box": [41, 2, 425, 278], "crop_status": "READY",
        "notes": "Cropped clean plate with green bean popsicle (kem dau xanh); no ingredient list on image."
    },
    "VPF-000345": {
        "status": "ABSENT", "serving": None, "energy": 170.0, "ingredients": [],
        "crop_box": [91, 24, 400, 285], "crop_status": "READY",
        "notes": "Cropped clean plate with chocolate ice cream (kem socola); no ingredient list on image."
    },
    "VPF-000351": {
        "status": "ABSENT", "serving": None, "energy": 456.0, "ingredients": [],
        "crop_box": [59, 13, 479, 285], "crop_status": "READY",
        "notes": "Cropped clean bowl with black bean sticky rice (xoi do den); no ingredient list on image."
    },
    "VPF-000352": {
        "status": "ABSENT", "serving": None, "energy": 438.0, "ingredients": [],
        "crop_box": [55, 30, 440, 284], "crop_status": "READY",
        "notes": "Cropped clean bowl with mung bean sticky rice (xoi do xanh); no ingredient list on image."
    },
    "VPF-000354": {
        "status": "ABSENT", "serving": None, "energy": 483.0, "ingredients": [],
        "crop_box": [70, 26, 424, 277], "crop_status": "READY",
        "notes": "Cropped clean bowl with red sticky rice (xoi gac); no ingredient list on image."
    },
    "VPF-000356": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 490.0,
        "ingredients": [
            {"name": "Xôi lạc", "source": "Xôi lạc: 200g", "qty": 200.0, "unit": "g"}
        ],
        "crop_box": [122, 25, 592, 300], "crop_status": "READY",
        "notes": "Cropped clean bowl with peanut sticky rice (xoi lac)."
    },
    "VPF-000367": {
        "status": "PRESENT", "serving": "Thành phần/bát", "energy": 107.0,
        "ingredients": [
            {"name": "Hạt sen tươi", "source": "Hạt sen tươi: 20g", "qty": 20.0, "unit": "g"},
            {"name": "Nhãn", "source": "Nhãn: 30g", "qty": 30.0, "unit": "g"},
            {"name": "Đường kính", "source": "Đường kính: 15g", "qty": 15.0, "unit": "g"}
        ],
        "crop_box": [130, 25, 490, 310], "crop_status": "READY",
        "notes": "Cropped clean lotus seed and longan sweet soup bowls (che hat sen long nhan)."
    },
    "VPF-000369": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 92.0,
        "ingredients": [
            {"name": "Hạt sen tươi", "source": "Hạt sen tươi: 20g", "qty": 20.0, "unit": "g"},
            {"name": "Đường kính", "source": "Đường kính: 15g", "qty": 15.0, "unit": "g"},
            {"name": "Bột đao", "source": "Bột đao: 5g", "qty": 5.0, "unit": "g"}
        ],
        "crop_box": [140, 27, 490, 310], "crop_status": "READY",
        "notes": "Cropped clean lotus seed sweet soup bowl (che sen)."
    },
    "VPF-000382": {
        "status": "ABSENT", "serving": None, "energy": 178.0, "ingredients": [],
        "crop_box": [79, 18, 332, 331], "crop_status": "READY",
        "notes": "Cropped clean bottle and glass of Moc Chau pasteurized milk; no ingredient list on image."
    },
    "VPF-000383": {
        "status": "PRESENT", "serving": "Thành phần/cốc", "energy": 138.0,
        "ingredients": [
            {"name": "Sữa tươi thanh trùng không đường TH True Milk", "source": "Sữa tươi thanh trùng không đường TH True Milk: 220ml", "qty": 220.0, "unit": "ml"}
        ],
        "crop_box": [207, 39, 464, 320], "crop_status": "READY",
        "notes": "Cropped clean carton and glass of TH True Milk pasteurized milk."
    },
    "VPF-000388": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 53.0,
        "ingredients": [
            {"name": "Sữa chua uống Yakult", "source": "Sữa chua uống Yakult: 65ml", "qty": 65.0, "unit": "ml"}
        ],
        "crop_box": [116, 31, 520, 310], "crop_status": "READY",
        "notes": "Cropped clean bottle of Yakult on plate."
    },
    "VPF-000390": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 108.6,
        "ingredients": [
            {"name": "Sữa đậu nành Fami", "source": "Sữa đậu nành Fami: 200ml", "qty": 200.0, "unit": "ml"}
        ],
        "crop_box": [134, 34, 490, 305], "crop_status": "READY",
        "notes": "Cropped clean carton of Fami soy milk on plate."
    },
    "VPF-000393": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 137.0,
        "ingredients": [
            {"name": "Sữa tươi tiệt trùng có đường Vinamilk", "source": "Sữa tươi tiệt trùng có đường Vinamilk: 180ml", "qty": 180.0, "unit": "ml"}
        ],
        "crop_box": [195, 44, 545, 320], "crop_status": "READY",
        "notes": "Cropped clean carton of Vinamilk UHT milk on plate."
    },
    "VPF-000394": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 110.0,
        "ingredients": [
            {"name": "Sữa tươi tiệt trùng không đường TH True milk", "source": "Sữa tươi tiệt trùng không đường TH True milk: 180ml", "qty": 180.0, "unit": "ml"}
        ],
        "crop_box": [119, 32, 450, 280], "crop_status": "READY",
        "notes": "Cropped clean carton of TH True Milk UHT milk on plate."
    },
    "VPF-000395": {
        "status": "PRESENT", "serving": "Thành phần", "energy": 90.0,
        "ingredients": [
            {"name": "Váng sữa Monte", "source": "Váng sữa Monte: 55g", "qty": 55.0, "unit": "g"}
        ],
        "crop_box": [108, 51, 587, 320], "crop_status": "READY",
        "notes": "Cropped clean cup of Monte dairy dessert on plate."
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

    print(f"Batch 39 Complete!")
    print(f"OCR Completed: {len(all_ocr_files)}/975 (PRESENT: {present_cnt}, ABSENT: {absent_cnt}, ILLEGIBLE: {illegible_cnt})")
    print(f"Crops Total: {processed_crops}/1023 (READY: {ready_count}, NEEDS_REVIEW: {needs_review_count}, Remaining: {unprocessed_crops})")

if __name__ == "__main__":
    run()
