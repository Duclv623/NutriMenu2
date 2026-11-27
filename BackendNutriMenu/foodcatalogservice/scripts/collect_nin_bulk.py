"""Audit and resumably stage public NIN dishes. Never activates runtime recipes.

Only standard-library dependencies. All cached source values and units are kept.
The OCR export is a task queue, not an LLM call or a claim of completed review.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import csv
import hashlib
import html
import json
import math
from pathlib import Path
import re
import shutil
import struct
import time
import unicodedata
from urllib.error import HTTPError
from urllib.parse import urlencode, urljoin, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

REPO_ROOT = Path(__file__).resolve().parents[3]
ROOT = REPO_ROOT / "BackendNutriMenu"
ORIGIN = "https://viendinhduong.vn"
PAGE = ORIGIN + "/vi/cong-cu-va-tien-ich/gia-tri-dinh-duong-mon-an"
API = ORIGIN + "/api/fe/tool/getPageFoodData"
CACHE = REPO_ROOT / "ai-service/data/structured/nin_dishes_2026-10-06/dishes_full.json"
REVIEWED = ROOT / "foodcatalogservice/src/main/resources/catalog/nin-real-20261123"
OUTPUT = ROOT / "foodcatalogservice/src/main/resources/catalog/nin-bulk-staging-20261125"
FIELDS = {
    "caloriesKcal": ({"nang-luong"}, "kcal"), "proteinG": ({"chat-dam"}, "g"),
    "fatG": ({"chat-beo", "lipid"}, "g"),
    "totalCarbG": ({"chat-bot-duong", "carbohydrate", "carbglucid"}, "g"),
    "fiberG": ({"xo", "chat-xo"}, "g"), "sodiumMg": ({"natri"}, "mg"),
    "cholesterolMg": ({"cholesterol"}, "mg"), "potassiumMg": ({"kali", "potassium"}, "mg"),
    "estimatedGi": ({"gi", "glycemic-index"}, ""),
    "purineMg": ({"purine"}, "mg"), "saturatedFatG": ({"saturated-fat", "chat-beo-bao-hoa"}, "g"),
}
CORE = ("caloriesKcal", "proteinG", "fatG", "totalCarbG", "sodiumMg")
BASE = (*CORE, "fiberG", "cholesterolMg", "estimatedGi")
PRIORITY = {"MAIN_STARCH": 0, "COMPOSITE_DISH": 1, "VEGGIE_DISH": 2, "SOUP": 3,
            "PROTEIN_DISH": 4, "SNACK": 5, "REVIEW_REQUIRED": 6}


def now():
    return datetime.now(timezone.utc).isoformat()


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for attempt in range(6):
        try:
            temp.replace(path)
            break
        except PermissionError:
            if attempt == 5:
                raise
            time.sleep(.05 * (attempt + 1))  # Windows indexer may briefly hold the checkpoint.


def source_url(value):
    if not isinstance(value, str) or not value.strip():
        raise ValueError("Missing source URL")
    value = urljoin(ORIGIN, value)
    parsed = urlparse(value)
    if parsed.scheme != "https" or parsed.hostname != "viendinhduong.vn" or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError("Only official HTTPS source URLs are permitted")
    return value


def safe_code(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]+", value):
        raise ValueError("Unsafe or missing source code")
    return value


def validate_rows(rows):
    if not isinstance(rows, list) or not rows:
        raise ValueError("Nonempty source row list required")
    codes, ids = set(), set()
    for row in rows:
        code = safe_code(row.get("code"))
        identity = row.get("_id")
        if not identity or identity in ids or code in codes or not isinstance(row.get("nutritional_components"), list):
            raise ValueError("Duplicate identity/code or invalid nutrient array")
        codes.add(code); ids.add(identity)


def reconcile_source_rows(rows, occurrences=None):
    """Collapse only byte-equivalent canonical JSON; quarantine whole identity/code conflicts.

    Raw rows/pages remain immutable. A same-code/different-ID pair, a changed
    payload for the same ID, and all records transitively connected to either
    conflict are withheld together. No field merge or preferred version exists.
    """
    occurrences = occurrences or [{"rawRowIndex": i} for i in range(len(rows))]
    if len(occurrences) != len(rows):
        raise ValueError("Source row/provenance count differs")
    parents = list(range(len(rows)))
    seen = {}

    def root(index):
        while parents[index] != index:
            parents[index] = parents[parents[index]]
            index = parents[index]
        return index

    for index, row in enumerate(rows):
        if not isinstance(row, dict):
            continue
        for field in ("_id", "code"):
            identity = row.get(field)
            if not isinstance(identity, str) or not identity:
                continue
            key = (field, identity)
            if key in seen:
                parents[root(index)] = root(seen[key])
            else:
                seen[key] = index
    groups = defaultdict(list)
    for index in range(len(rows)):
        groups[root(index)].append(index)
    accepted, accepted_evidence, duplicates, conflicts = [], [], [], []
    duplicate_rows = 0
    for indices in groups.values():
        variants = defaultdict(list)
        errors = []
        for index in indices:
            row = rows[index]
            serialized = json.dumps(row, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
            variants[sha(serialized.encode("utf-8"))].append(index)
            try:
                validate_rows([row])
            except (AttributeError, ValueError, TypeError) as exc:
                errors.append({"sourceOccurrence": occurrences[index], "reason": str(exc)})
        if errors or len(variants) != 1:
            conflicts.append({"reason": "INVALID_SOURCE_ROW" if errors else "IDENTITY_OR_CODE_PAYLOAD_CONFLICT",
                "validationIssues": errors, "variantCount": len(variants),
                "records": [{"sourceOccurrence": occurrences[index], "rawSourceRow": rows[index],
                    "canonicalJsonSha256": digest} for digest, members in variants.items() for index in members],
                "disposition": "QUARANTINED_ALL_CONNECTED_ROWS_NO_MERGE_OR_PROMOTION"})
            continue
        first = indices[0]
        row = rows[first]
        evidence = {"code": row["code"], "sourceRecordId": row["_id"],
            "canonicalJsonSha256": next(iter(variants)), "sourceOccurrences": [occurrences[index] for index in indices]}
        accepted.append(row)
        accepted_evidence.append(evidence)
        if len(indices) > 1:
            duplicate_rows += len(indices) - 1
            duplicates.append({**evidence, "extraIdenticalRows": len(indices) - 1,
                "rawSourceRow": row, "disposition": "ONE_UNIQUE_ROW_FROM_IDENTICAL_JSON_ALL_RAW_OCCURRENCES_RETAINED"})
    quarantined_rows = sum(len(group["records"]) for group in conflicts)
    if accepted:
        validate_rows(accepted)
    if len(accepted) + duplicate_rows + quarantined_rows != len(rows):
        raise ValueError("Reconciled source row counts do not balance")
    return accepted, {"policy": "EXACT_CANONICAL_JSON_DUPLICATES_ONLY_NO_FIELD_MERGE",
        "rawRowCount": len(rows), "acceptedUniqueRowCount": len(accepted),
        "extraIdenticalRowCount": duplicate_rows, "exactDuplicateGroupCount": len(duplicates),
        "quarantinedRawRowCount": quarantined_rows, "conflictGroupCount": len(conflicts),
        "acceptedSourceRecords": accepted_evidence, "exactDuplicates": duplicates, "quarantinedGroups": conflicts}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class PublicClient:
    def __init__(self, delay=1.0, timeout=30):
        self.delay, self.timeout, self.last_request = delay, timeout, 0.0
        self.opener = build_opener(NoRedirect())

    def get(self, url, accept, maximum):
        url = source_url(url)
        for _ in range(4):
            pause = self.delay - (time.monotonic() - self.last_request)
            if pause > 0:
                time.sleep(pause)
            self.last_request = time.monotonic()
            request = Request(url, headers={"User-Agent": "testda-public-nutrition-collector/2.0",
                                           "Accept": accept, "Referer": PAGE})
            try:
                with self.opener.open(request, timeout=self.timeout) as response:
                    data = response.read(maximum + 1)
                    if len(data) > maximum:
                        raise ValueError("Source response exceeds size limit")
                    return data, response.headers.get_content_type()
            except HTTPError as exc:
                if exc.code in (301, 302, 303, 307, 308):
                    url = source_url(urljoin(url, exc.headers.get("Location", "")))
                    continue
                raise  # 401/403/429 are never retried or bypassed.
        raise ValueError("Too many redirects")


def image_container(data):
    """Validate signatures/container boundaries, not claim a visual/full decode."""
    if len(data) < 32:
        raise ValueError("Truncated image")
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        if data[12:16] != b"IHDR" or not data.endswith(b"IEND\xaeB`\x82"):
            raise ValueError("Malformed PNG container")
        width, height = struct.unpack(">II", data[16:24]); extension = ".png"
    elif data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        if int.from_bytes(data[4:8], "little") + 8 != len(data):
            raise ValueError("Truncated WebP RIFF")
        offset, frames = 12, []
        while offset + 8 <= len(data):
            tag = data[offset:offset+4]; length = int.from_bytes(data[offset+4:offset+8], "little")
            if offset + 8 + length > len(data):
                raise ValueError("Malformed WebP chunk")
            frames.append(tag); offset += 8 + length + (length % 2)
        if offset != len(data) or not any(tag in frames for tag in (b"VP8 ", b"VP8L", b"VP8X")):
            raise ValueError("Missing WebP image data")
        width = height = None; extension = ".webp"
    elif data.startswith(b"\xff\xd8\xff") and data.endswith(b"\xff\xd9"):
        width = height = None; extension = ".jpg"
    elif data.startswith((b"GIF87a", b"GIF89a")) and data.endswith(b";"):
        width, height = struct.unpack("<HH", data[6:10]); extension = ".gif"
    else:
        raise ValueError("Not a supported complete image container")
    if width is not None and (width <= 0 or height <= 0 or width * height > 100_000_000):
        raise ValueError("Invalid/oversized image dimensions")
    return {"extension": extension, "width": width, "height": height,
            "validation": "SIGNATURE_AND_CONTAINER_CHECK_NOT_VISUAL_OR_FULL_DECODE"}


def normalize(text):
    return " ".join("".join(c for c in unicodedata.normalize("NFD", text.lower().replace("đ", "d")) if unicodedata.category(c) != "Mn").split())


def tentative_role(row):
    name, category = normalize(row.get("name_vi", "")), normalize(row.get("category_name", ""))
    if name.startswith("canh "):
        return "SOUP"
    if name.startswith(("com suat", "com rang", "com chien", "com hen", "com suon", "sushi", "pho ", "bun ", "chao ", "mien ", "mi ", "hu tieu", "banh canh")):
        return "COMPOSITE_DISH"
    if name.startswith(("com te", "com gao", "com trang", "com nam", "com nep", "xoi trang")):
        return "MAIN_STARCH"
    if name.startswith(("rau ", "cai ", "bap cai", "su hao", "su su", "gia do", "dau cove", "dau co ve", "bi do xao", "nam xao")):
        return "VEGGIE_DISH"
    if "trai cay" in category or name.startswith(("cam", "tao", "chuoi", "oi ", "du du", "buoi")):
        return "SNACK"
    if name.startswith(("thit ", "ca ", "tom ", "trung ", "dau phu", "ga ", "bo ", "suon ")):
        return "PROTEIN_DISH"
    return "REVIEW_REQUIRED"


def numeric(value):
    if value is None or value == "" or isinstance(value, bool):
        return None
    try:
        result = float(value)
        return result if math.isfinite(result) and result >= 0 else None
    except (TypeError, ValueError):
        return None


def nutrient_audit(row):
    values, evidence, issues = {}, {}, []
    for field, (aliases, unit) in FIELDS.items():
        matches = [(i, x) for i, x in enumerate(row["nutritional_components"]) if x.get("key") in aliases]
        if len(matches) > 1:
            issues.append({"field": field, "reason": "DUPLICATE_NUTRIENT_ALIAS", "indices": [i for i, _ in matches]})
        value = None
        if len(matches) == 1:
            index, observation = matches[0]
            actual_unit = str(observation.get("unit_name", "")).strip().lower()
            if actual_unit != unit:
                issues.append({"field": field, "reason": "UNEXPECTED_UNIT", "sourceUnit": observation.get("unit_name")})
            else:
                value = numeric(observation.get("amount"))
                if value is None and observation.get("amount") not in (None, ""):
                    issues.append({"field": field, "reason": "INVALID_NUMERIC_VALUE", "sourceAmount": observation.get("amount")})
            evidence[field] = {"arrayIndex": index, "sourceKey": observation.get("key"),
                               "rawAmount": observation.get("amount"), "rawUnit": observation.get("unit_name")}
        values[field] = value
    # total_energy is independently retained; do not silently substitute aliases.
    if numeric(row.get("total_energy")) is not None and values["caloriesKcal"] is not None and abs(numeric(row["total_energy"]) - values["caloriesKcal"]) > .01:
        issues.append({"field": "caloriesKcal", "reason": "ENERGY_SOURCE_FIELDS_DISAGREE"})
    return values, evidence, issues


def previous_evidence():
    extracted = load(REVIEWED / "extracted-recipes.json")
    rows = [*extracted.get("recipes", []), *extracted.get("omitted_from_ingredient_extraction", [])]
    return {r["code"]: r for r in rows}


def image_records(output):
    path = output / "image-collection.json"
    return load(path).get("records", {}) if path.is_file() else {}


def verify_record(record, output, url):
    if record.get("imageUrl") != url or not record.get("localPath") or not record.get("sha256"):
        raise ValueError("Image source identity/hash missing or changed")
    path = (output / record["localPath"]).resolve()
    if not path.is_relative_to((output / "images").resolve()):
        raise ValueError("Image path escapes staging image directory")
    data = path.read_bytes()
    if sha(data) != record["sha256"]:
        raise ValueError("Image hash mismatch")
    image_container(data)
    return path


def collect_images(rows, output, client=None, offline=False, limit=None):
    validate_rows(rows); output.mkdir(parents=True, exist_ok=True)
    records = image_records(output); prior = previous_evidence()
    selected = sorted(rows, key=lambda r: (PRIORITY[tentative_role(r)], r["code"]))
    summary = {"startedAt": now(), "offline": offline, "stopReason": None, "networkRequests": 0,
               "freshDownloadsThisRun": 0, "reusedThisRun": 0, "verifiedExistingThisRun": 0}
    failures = 0
    for row in selected[:limit] if limit else selected:
        code = row["code"]
        result = {"code": code, "sourceRecordId": row["_id"], "checkedAt": now()}
        try:
            url = source_url(row.get("image")); result["imageUrl"] = url
            if code in records and records[code].get("localPath"):
                verify_record(records[code], output, url)
                result = {**records[code], "checkedAt": now(), "status": "VERIFIED_EXISTING"}
                summary["verifiedExistingThisRun"] += 1
            else:
                old = prior.get(code, {})
                image = old.get("image", {})
                content = None
                if old.get("source_id") == row["_id"] and image.get("source_url") == url and image.get("local_path"):
                    old_path = (REVIEWED / image["local_path"]).resolve()
                    if old_path.is_relative_to((REVIEWED / "images").resolve()) and old_path.is_file():
                        candidate = old_path.read_bytes()
                        if sha(candidate) == image.get("sha256"):
                            image_container(candidate); content = candidate
                            result.update({"status": "REUSED_VERIFIED_PRIOR_SOURCE", "reusedFrom": str(old_path),
                                           "priorSourceEvidence": "nin-real-20261123/extracted-recipes.json"})
                            summary["reusedThisRun"] += 1
                if content is None and offline:
                    result["status"] = "PENDING_DOWNLOAD_OFFLINE"
                elif content is None:
                    summary["networkRequests"] += 1
                    content, media = client.get(url, "image/webp,image/*;q=0.9", 20_000_000)
                    if not media.startswith("image/"):
                        raise ValueError("Source returned non-image Content-Type")
                    image_container(content)
                    result.update({"status": "DOWNLOADED", "retrievedAt": now()})
                    summary["freshDownloadsThisRun"] += 1
                if content is not None:
                    info = image_container(content)
                    target = output / "images" / (code + info["extension"])
                    target.parent.mkdir(parents=True, exist_ok=True)
                    if target.exists() and target.read_bytes() != content:
                        raise ValueError("Different original already exists; manual version review required")
                    if not target.exists():
                        temporary = target.with_suffix(target.suffix + ".part")
                        temporary.write_bytes(content); temporary.replace(target)
                    result.update({"localPath": target.relative_to(output).as_posix(), "sha256": sha(content),
                                   "bytes": len(content), "imageValidation": info})
            failures = 0
        except (OSError, ValueError) as exc:
            result.update({"status": "FAILED", "error": str(exc), "httpStatus": getattr(exc, "code", None)})
            failures += 1
            if getattr(exc, "code", None) in (401, 403, 429):
                summary["stopReason"] = f"HTTP_{exc.code}_STOP_NO_BYPASS"
            elif not offline and failures >= 3:
                summary["stopReason"] = "THREE_CONSECUTIVE_FAILURES_STOP"
        records[code] = result
        write(output / "image-collection.json", {"lastRun": summary, "records": records})
        if len(records) % 25 == 0 or summary["stopReason"]:
            print(json.dumps({"checkpointRecords": len(records), "lastCode": code, "lastStatus": result["status"], "stopReason": summary["stopReason"]}), flush=True)
        if summary["stopReason"]:
            break
    summary["finishedAt"] = now()
    write(output / "image-collection.json", {"lastRun": summary, "records": records})
    return summary


def refresh(output, client, page_size=100, run_id=None):
    """Resume only an explicit checkpoint; publish source snapshot after full validation."""
    run_id = run_id or datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    safe_code(run_id)
    folder = output / "api-runs" / run_id
    folder.mkdir(parents=True, exist_ok=True)
    checkpoint = folder / "checkpoint.json"
    state = load(checkpoint) if checkpoint.is_file() else {"runId": run_id, "apiUrl": API,
        "startedAt": now(), "pageSizeRequested": page_size, "pages": [], "complete": False}
    if state["pageSizeRequested"] != page_size:
        raise ValueError("Resume page size differs from checkpoint")
    rows, occurrences = [], []
    try:
        page = 1
        while True:
            url = API + "?" + urlencode({"page": page, "pageSize": page_size})
            cached = next((p for p in state["pages"] if p["page"] == page), None)
            if cached:
                data = (folder / cached["file"]).read_bytes()
                if sha(data) != cached["sha256"] or cached["url"] != url:
                    raise ValueError("API checkpoint hash or URL changed")
            else:
                data, media = client.get(url, "application/json", 20_000_000)
                if media not in ("application/json", "text/json"):
                    raise ValueError("Source returned non-JSON Content-Type")
            payload = json.loads(data)
            current, total, size = (int(payload[k]) for k in ("current_page", "total", "per_page"))
            if current != page or total <= 0 or size <= 0 or total > 100_000:
                raise ValueError("Invalid API pagination")
            if page == 1:
                if state.get("total") is not None and (state["total"], state["perPage"]) != (total, size):
                    raise ValueError("Pagination changed from checkpoint; start a new run")
                state.update({"total": total, "perPage": size})
            elif (state["total"], state["perPage"]) != (total, size):
                raise ValueError("Pagination changed during collection; start a new run")
            entries = payload.get("data")
            if not isinstance(entries, list) or len(entries) != min(size, total - (page - 1) * size):
                raise ValueError("API page is incomplete")
            if not cached:
                name = f"page_{page:04d}.json"
                (folder / name).write_bytes(data)
                state["pages"].append({"page": page, "url": url, "file": name, "sha256": sha(data),
                                       "rows": len(entries), "retrievedAt": now()})
                write(checkpoint, state)
                print(json.dumps({"apiPage": page, "rows": len(entries), "expectedTotal": total}), flush=True)
            rows.extend(entries)
            page_evidence = cached or state["pages"][-1]
            occurrences.extend({"apiPage": page, "rowIndexZeroBased": index,
                "pageFile": page_evidence["file"], "pageSha256": page_evidence["sha256"],
                "pageRetrievedAt": page_evidence["retrievedAt"]} for index in range(len(entries)))
            if page * size >= total:
                break
            page += 1
        if len(rows) != state["total"]:
            raise ValueError("Incomplete API cohort")
        unique_rows, reconciliation = reconcile_source_rows(rows, occurrences)
        raw_snapshot = folder / "dishes-source-raw.json"
        write(raw_snapshot, rows)
        write(folder / "source-reconciliation.json", reconciliation)
        write(folder / "quarantined-source-records.json", {"apiReportedTotal": state["total"],
            "rawRowCount": len(rows), "quarantinedGroups": reconciliation["quarantinedGroups"]})
        if not unique_rows:
            raise ValueError("No unambiguous valid source rows remain after quarantine")
        snapshot = folder / "dishes-source.json"
        write(snapshot, unique_rows)
        state.update({"complete": True, "paginationComplete": True, "completedAt": now(),
            "publicationStatus": "COMPLETE_WITH_QUARANTINE" if reconciliation["conflictGroupCount"] else "COMPLETE_WITH_AUDITED_EXACT_DUPLICATES" if reconciliation["extraIdenticalRowCount"] else "COMPLETE_UNIQUE_SOURCE",
            "apiReportedTotal": state["total"], "rawRowCount": len(rows),
            "acceptedUniqueRowCount": len(unique_rows), "extraIdenticalRowCount": reconciliation["extraIdenticalRowCount"],
            "sourceEnumerationComplete": len(unique_rows) == state["total"] and not reconciliation["conflictGroupCount"],
            "sourceCompletenessWarning": "All reported API pages were received, but API total and accepted unique identities differ. Pagination drift or source duplicate storage may have omitted an identity; complete enumeration is not established. No missing recipe is invented." if len(unique_rows) != state["total"] or reconciliation["conflictGroupCount"] else None,
            "quarantinedRawRowCount": reconciliation["quarantinedRawRowCount"], "conflictGroupCount": reconciliation["conflictGroupCount"],
            "rawSourceSnapshotSha256": sha(raw_snapshot.read_bytes()),
            "reconciliationSha256": sha((folder / "source-reconciliation.json").read_bytes()),
            "sourceSha256": sha(snapshot.read_bytes()), "error": None})
        write(checkpoint, state)
        write(output / "latest-complete-source.json", {"path": snapshot.relative_to(output).as_posix(),
              "sha256": state["sourceSha256"], "retrievedAt": state["completedAt"], "records": len(unique_rows),
              "apiReportedTotal": state["total"], "rawRowCount": len(rows),
              "extraIdenticalRowCount": reconciliation["extraIdenticalRowCount"],
              "quarantinedRawRowCount": reconciliation["quarantinedRawRowCount"],
              "sourceEnumerationComplete": state["sourceEnumerationComplete"],
              "sourceCompletenessWarning": state["sourceCompletenessWarning"],
              "reconciliationPath": (folder / "source-reconciliation.json").relative_to(output).as_posix(),
              "rawSourcePath": raw_snapshot.relative_to(output).as_posix(), "apiRunId": run_id})
    except (OSError, ValueError, KeyError, TypeError) as exc:
        state.update({"complete": False, "error": str(exc), "httpStatus": getattr(exc, "code", None),
                      "stoppedAt": now(), "resumeCommand": f"refresh --run-id {run_id} --page-size {page_size}"})
        write(checkpoint, state)
    return state


def audit(source, output):
    rows = load(source); validate_rows(rows)
    prior = previous_evidence(); images = image_records(output)
    active = load(REVIEWED / "nin-source-import.json")["dishes"]
    active = {r["sourceRecordId"]: r for r in active}
    records = []
    for row in sorted(rows, key=lambda r: r["code"]):
        code = row["code"]; values, observations, issues = nutrient_audit(row)
        try:
            url = source_url(row.get("image")); valid_url = True
        except ValueError:
            url = row.get("image"); valid_url = False
        local = False; image_error = None
        if code in images and valid_url:
            try:
                verify_record(images[code], output, url); local = True
            except (OSError, ValueError) as exc:
                image_error = str(exc)
        reviewed = prior.get(code, {})
        matched = reviewed.get("source_id") == row["_id"] and reviewed.get("image", {}).get("source_url") == url
        recipe = matched and bool(reviewed.get("ingredients")) and local
        basis = reviewed.get("serving_basis") if recipe else None
        runtime = active.get(row["_id"])
        runtime_matched = bool(runtime and runtime["imageUrl"] == url and local and runtime["code"] == "NIN_REAL_" + code.replace("-", "_"))
        role = runtime["dishRole"] if runtime_matched else tentative_role(row)
        missing_core = [f for f in CORE if values[f] is None]
        missing_base = [f for f in BASE if values[f] is None]
        reasons = []
        if not valid_url: reasons.append("INVALID_OR_MISSING_IMAGE_URL")
        if not local: reasons.append("IMAGE_NOT_AVAILABLE_LOCALLY")
        if missing_core: reasons.append("MISSING_OR_INVALID_CORE_NUMERIC_FIELDS")
        if issues: reasons.append("SOURCE_NUMERIC_QUALITY_REVIEW")
        if not recipe: reasons.append("NO_VERIFIED_INGREDIENT_RECIPE")
        if not basis: reasons.append("PORTION_BASIS_UNVERIFIED")
        if not runtime_matched: reasons.extend(["ROLE_REQUIRES_REVIEW", "NO_APPROVED_SOURCE_NUTRITION_MODEL"])
        if values["estimatedGi"] is None: reasons.append("SOURCE_GI_UNKNOWN_NOT_ZERO")
        records.append({"code": code, "sourceRecordId": row["_id"], "nameVi": row.get("name_vi"),
            "sourceRecordSha256": sha(json.dumps(row, sort_keys=True, ensure_ascii=False).encode()),
            "sourceUrl": PAGE, "sourceImageUrl": url, "imageUrlPresent": bool(row.get("image")),
            "imageUrlValid": valid_url, "imageAvailableLocally": local, "imageError": image_error,
            "imageEvidence": images.get(code), "sourceUpdatedAt": row.get("updated_at"),
            "sourceNumericFields": values, "perFieldSourceObservations": observations,
            "rawSourceTotalEnergy": row.get("total_energy"), "rawSourceNutritionalComponents": row["nutritional_components"],
            "carbohydrateDefinition": "UNKNOWN_SOURCE_GLUCID_NO_FIBRE_SUBTRACTION",
            "sourceNutritionBasis": "UNVERIFIED_FOR_THIS_RECORD_DO_NOT_ASSUME_100G_OR_SOURCE_SERVING",
            "cachedOwner100gClaim": "Historical cache manifest claims 100g by owner confirmation; source images show portion headings. Per-record reconciliation required; not applied here.",
            "coreNumericComplete": not missing_core, "missingCoreFields": missing_core, "missingBaseFields": missing_base,
            "sourceNumericIssues": issues, "recipePreviouslyExtracted": bool(recipe),
            "priorRecipeEvidence": {"path": "nin-real-20261123/extracted-recipes.json", "imageSha256": reviewed.get("image", {}).get("sha256")} if recipe else None,
            "sourceImagePreviouslyHadNoRecipe": bool(matched and reviewed.get("extraction_status") == "no_image_ingredient_list"),
            "priorServingBasis": basis, "tentativeRole": role,
            "roleEvidence": "APPROVED_40_SOURCE_FIXTURE" if runtime_matched else "NAME_HEURISTIC_REVIEW_REQUIRED",
            "reviewPriority": PRIORITY[role], "existingApprovedRuntimeRow": runtime_matched,
            "approvedRuntimeBaseEligible": bool(runtime_matched and runtime["solverEligible"]),
            "newBulkSolverEligible": False, "clinicalCompletenessClaimed": False,
            "reviewReasons": reasons, "bulkDisposition": "ALREADY_IN_APPROVED_40" if runtime_matched else "PENDING_REVIEW_NOT_ACTIVATED"})
    role_rows = []
    for role in PRIORITY:
        subset = [r for r in records if r["tentativeRole"] == role]
        role_rows.append({"role": role, "tentativeTotal": len(subset), "coreNumericAndUrl": sum(r["coreNumericComplete"] and r["imageUrlValid"] for r in subset),
                          "localImages": sum(r["imageAvailableLocally"] for r in subset), "previousRecipes": sum(r["recipePreviouslyExtracted"] for r in subset),
                          "alreadyApproved": sum(r["existingApprovedRuntimeRow"] for r in subset)})
    cache_manifest = source.parent / "manifest.json"
    provenance = load(cache_manifest) if cache_manifest.is_file() else None
    checkpoint_path = source.parent / "checkpoint.json"
    crawl = load(checkpoint_path) if checkpoint_path.is_file() else None
    fresh_metadata = bool(crawl and crawl.get("complete") and crawl.get("sourceSha256") == sha(source.read_bytes()))
    image_run = load(output / "image-collection.json").get("lastRun") if (output / "image-collection.json").is_file() else None
    source_numeric_any_unit = {f: sum(any(n.get("key") in aliases and numeric(n.get("amount")) is not None
        for n in row["nutritional_components"]) for row in rows) for f, (aliases, _) in FIELDS.items()}
    report = {"schemaVersion": 1, "auditedAt": now(), "sourcePath": str(source.resolve()),
        "sourceSha256": sha(source.read_bytes()), "sourceCacheManifest": provenance,
        "completedApiCrawlEvidence": crawl, "freshMetadataCrawlClaimed": fresh_metadata,
        "sourceEnumerationComplete": crawl.get("sourceEnumerationComplete", False) if fresh_metadata else False,
        "apiReportedTotal": crawl.get("apiReportedTotal") if fresh_metadata else None,
        "apiRawRowCount": crawl.get("rawRowCount") if fresh_metadata else None,
        "acceptedUniqueApiRowCount": crawl.get("acceptedUniqueRowCount") if fresh_metadata else None,
        "extraIdenticalApiRowCount": crawl.get("extraIdenticalRowCount") if fresh_metadata else None,
        "quarantinedApiRowCount": crawl.get("quarantinedRawRowCount") if fresh_metadata else None,
        "sourceCompletenessWarning": crawl.get("sourceCompletenessWarning") if fresh_metadata else "Historical cache audit, not a fresh complete source enumeration.",
        "freshImageDownloadsClaimed": bool(image_run and image_run.get("freshDownloadsThisRun")),
        "rows": len(records), "imageUrlCount": sum(r["imageUrlPresent"] for r in records),
        "validImageUrlCount": sum(r["imageUrlValid"] for r in records),
        "coreNumericAndImageUrlCount": sum(r["coreNumericComplete"] and r["imageUrlValid"] for r in records),
        "sourceFieldPresentCounts": {f: sum(r["sourceNumericFields"][f] is not None for r in records) for f in FIELDS},
        "rawNumericObservationsRegardlessOfUnit": source_numeric_any_unit,
        "countDefinition": "sourceFieldPresentCounts requires one nonnegative finite value, expected unit and no duplicate aliases. Raw numeric counts include wrong units and are not admissible grams/mg.",
        "verifiedLocalImageCount": sum(r["imageAvailableLocally"] for r in records),
        "previouslyExtractedRecipeCount": sum(r["recipePreviouslyExtracted"] for r in records),
        "imagesWithNoRecipeListPreviouslyObserved": sum(r["sourceImagePreviouslyHadNoRecipe"] for r in records),
        "existingApprovedRuntimeCount": sum(r["existingApprovedRuntimeRow"] for r in records),
        "newBulkSolverEligibleCount": 0, "runtimeCatalogModified": False,
        "reviewReasonCounts": dict(Counter(reason for r in records for reason in r["reviewReasons"])),
        "roleCoverage": role_rows, "latestImageCollection": image_run,
        "warning": "URLs are not downloads. Numeric completeness is not recipe, portion, role, GI or clinical completeness. No pending bulk recipe activated. Most staple GI is unknown; expansion alone does not solve the GI50 medical template."}
    write(output / "audit-all-dishes.json", {"report": report, "dishes": records})
    candidates = [r for r in records if r["coreNumericComplete"] and r["imageUrlValid"] and not r["sourceNumericIssues"]]
    write(output / "numeric-review-candidates.json", {"meaning": "Review candidates only; not planner-ready", "dishes": candidates})
    write(output / "numeric-review-rejected.json", {"meaning": "Excluded from first numeric-complete review pass; retained for correction", "dishes": [r for r in records if r not in candidates]})
    write(output / "collection-report.json", report)
    with (output / "audit-summary.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        fields = ["code", "nameVi", "tentativeRole", "roleEvidence", "coreNumericComplete", "imageAvailableLocally", "recipePreviouslyExtracted", "existingApprovedRuntimeRow", "newBulkSolverEligible", "missingCoreFields", "reviewReasons"]
        writer = csv.DictWriter(stream, fieldnames=fields); writer.writeheader()
        writer.writerows({f: ";".join(r[f]) if isinstance(r[f], list) else r[f] for f in fields} for r in records)
    return report


OCR_INSTRUCTION = """Read only the attached original source image. Copy every heading, serving label, ingredient name, quantity and unit exactly. Do not invent recipes or infer missing grams from photographs/nutrition. Do not convert ml, pieces, bone-in or shell-on amounts to edible grams. Distinguish no ingredient list from illegible text. Record every uncertain token with its source text. Source API nutrition basis is unverified: do not assume 100g or source serving. Compare image energy to API energy without changing either. Return JSON matching the supplied schema. This is extraction pending independent review, not permission to activate a clinical dish."""


def review(output, batch_size=25):
    audit_data = load(output / "audit-all-dishes.json")
    records = sorted(audit_data["dishes"], key=lambda r: (r["reviewPriority"], not r["coreNumericComplete"], r["code"]))
    task_dir = output / "ocr-tasks"; task_dir.mkdir(parents=True, exist_ok=True)
    schema = {"type": "object", "additionalProperties": False, "required": ["code", "imageSha256", "ingredientListStatus", "servingHeadingText", "ingredients", "imageEnergyText", "uncertainties", "reviewStatus"],
        "properties": {"code": {"type": "string"}, "imageSha256": {"type": "string"},
            "ingredientListStatus": {"enum": ["PRESENT", "ABSENT", "ILLEGIBLE"]},
            "servingHeadingText": {"type": ["string", "null"]}, "imageEnergyText": {"type": ["string", "null"]},
            "ingredients": {"type": "array", "items": {"type": "object", "additionalProperties": False,
                "required": ["nameSourceText", "amountSourceText", "numericQuantity", "unitSourceText", "preparationSourceText", "certainty"],
                "properties": {"nameSourceText": {"type": "string"}, "amountSourceText": {"type": ["string", "null"]},
                    "numericQuantity": {"type": ["number", "null"], "minimum": 0}, "unitSourceText": {"type": ["string", "null"]},
                    "preparationSourceText": {"type": ["string", "null"]}, "certainty": {"enum": ["CLEAR", "UNCERTAIN", "ILLEGIBLE"]}}}},
            "uncertainties": {"type": "array", "items": {"type": "string"}}, "reviewStatus": {"const": "PENDING_INDEPENDENT_REVIEW"}}}
    write(task_dir / "response-schema.json", schema)
    tasks, pending, known_photo_only = [], [], []
    prior = previous_evidence()
    for row in records:
        if row["existingApprovedRuntimeRow"]:
            continue
        if not row["imageAvailableLocally"]:
            pending.append({"code": row["code"], "nameVi": row["nameVi"], "sourceImageUrl": row["sourceImageUrl"], "status": "BLOCKED_IMAGE_NOT_DOWNLOADED", "priority": row["reviewPriority"]})
            continue
        evidence = row["imageEvidence"]
        verify_record(evidence, output, row["sourceImageUrl"])
        previous = prior.get(row["code"], {})
        if (row.get("sourceImagePreviouslyHadNoRecipe")
                and evidence["sha256"] == previous.get("image", {}).get("sha256")):
            known_photo_only.append({"code": row["code"], "imageSha256": evidence["sha256"],
                "imagePath": evidence["localPath"], "sourceRecordId": row["sourceRecordId"],
                "ingredientListStatus": "ABSENT", "evidence": "PRIOR_VISUAL_REVIEW_SAME_SOURCE_IMAGE_HASH",
                "sourceEvidencePath": "nin-real-20261123/extracted-recipes.json",
                "priorReason": previous.get("reason"), "newOcrExecutionRequired": False,
                "disposition": "DISH_PHOTO_RETAINED_NO_INGREDIENT_RECIPE_DO_NOT_INVENT"})
            continue
        tasks.append({"taskId": "nin-image-" + row["code"], "code": row["code"], "sourceRecordId": row["sourceRecordId"],
            "nameVi": row["nameVi"], "imagePath": str((output / evidence["localPath"]).resolve()), "imageSha256": evidence["sha256"],
            "sourceImageUrl": row["sourceImageUrl"], "priority": row["reviewPriority"], "tentativeRole": row["tentativeRole"],
            "userPreferredWorkerLabel": "GPT6Luna", "apiModelIdentifier": None, "executionStatus": "NOT_EXECUTED",
            "instruction": OCR_INSTRUCTION, "responseSchema": "response-schema.json",
            "rawSourceTotalEnergy": row["rawSourceTotalEnergy"], "rawSourceNutritionalComponents": row["rawSourceNutritionalComponents"],
            "priorExtraction": row["priorRecipeEvidence"], "priorNoRecipeList": row["sourceImagePreviouslyHadNoRecipe"]})
    expected = set()
    for start in range(0, len(tasks), batch_size):
        name = f"batch-{start // batch_size + 1:04d}.json"; expected.add(name)
        write(task_dir / name, {"instructions": "Attach each task's exact local image; no execution/model availability implied. Save responses separately under ocr-responses/; never overwrite reviewed source recipes.", "tasks": tasks[start:start+batch_size]})
    # Keep old batches, but explicitly mark which belong to the latest queue.
    write(task_dir / "queue-index.json", {"generatedAt": now(), "readyTaskCount": len(tasks), "blockedImageCount": len(pending),
        "knownUnchangedPhotoOnlySkippedCount": len(known_photo_only),
        "completedNewOcrCount": 0, "apiCallsMade": 0, "activeBatches": sorted(expected),
        "staleBatchesExcluded": sorted(p.name for p in task_dir.glob("batch-*.json") if p.name not in expected),
        "preferredWorkerIsNotAnApiModelId": True, "runtimeActivationAllowed": False})
    write(task_dir / "blocked-images.json", pending)
    write(task_dir / "known-photo-only-no-ingredient-list.json", known_photo_only)
    cards = []
    for row in records:
        if not row["imageAvailableLocally"]: continue
        image = html.escape(row["imageEvidence"]["localPath"], quote=True)
        cards.append(f'<article><a href="{image}"><img src="{image}" loading="lazy" alt="{html.escape(row["nameVi"] or "")}"></a><strong>{html.escape(row["code"])} — {html.escape(row["nameVi"] or "")}</strong><p>{html.escape(row["tentativeRole"])} / {html.escape(row["bulkDisposition"])}</p><code>{row["imageEvidence"]["sha256"]}</code></article>')
    gallery = '<!doctype html><html lang="vi"><meta charset="utf-8"><title>NIN source image review queue</title><style>body{font:15px system-ui;margin:24px;background:#f5f7fa}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:16px}article{background:white;padding:12px;border:1px solid #ccc}img{width:100%;height:220px;object-fit:contain}strong{display:block}code{font-size:10px;overflow-wrap:anywhere}</style><h1>Ảnh nguồn đã có — hàng đợi đối chiếu</h1><p>Ảnh thu nhỏ do trình duyệt hiển thị từ byte gốc; không tạo ảnh mới. Trạng thái không phải kết quả OCR mới hoặc xác nhận lâm sàng. Bấm ảnh để xem gốc.</p><main>' + ''.join(cards) + '</main></html>'
    (output / "review-gallery.html").write_text(gallery, encoding="utf-8")
    return {"readyOcrTasks": len(tasks), "blockedWithoutImage": len(pending),
            "knownUnchangedPhotoOnlySkipped": len(known_photo_only), "newOcrCompleted": 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("audit", "refresh", "images", "review"))
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--source-json", type=Path)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--delay", type=float, default=1.0)
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--page-size", type=int, default=100)
    parser.add_argument("--run-id")
    parser.add_argument("--batch-size", type=int, default=25)
    args = parser.parse_args()
    args.output = args.output.resolve()
    if args.output == REVIEWED.resolve() or args.output.is_relative_to(REVIEWED.resolve()):
        parser.error("Bulk output must not overwrite the reviewed runtime cohort")
    if args.delay < .25 or args.timeout < 1 or not 1 <= args.page_size <= 100 or not 1 <= args.batch_size <= 100 or (args.limit is not None and args.limit <= 0):
        parser.error("Invalid rate/timeout/page/batch/limit")
    args.output.mkdir(parents=True, exist_ok=True)
    source = args.source_json
    pointer = args.output / "latest-complete-source.json"
    if source is None and pointer.is_file():
        latest = load(pointer); source = (args.output / latest["path"]).resolve()
        if not source.is_relative_to(args.output) or sha(source.read_bytes()) != latest["sha256"]:
            raise ValueError("Latest source snapshot path/hash invalid")
    source = source or CACHE
    client = PublicClient(args.delay, args.timeout)
    if args.command == "refresh":
        if args.offline: parser.error("refresh needs network; use audit/images --offline instead")
        result = refresh(args.output, client, args.page_size, args.run_id)
    elif args.command == "images":
        result = collect_images(load(source), args.output, client, args.offline, args.limit)
    elif args.command == "audit":
        result = audit(source, args.output)
    else:
        result = review(args.output, args.batch_size)
    print(json.dumps({k: v for k, v in result.items() if k not in ("sourceCacheManifest", "roleCoverage", "pages")}, ensure_ascii=True))
    if (args.command == "refresh" and not result.get("complete")) or result.get("stopReason"):
        raise SystemExit(2)


if __name__ == "__main__":
    main()
