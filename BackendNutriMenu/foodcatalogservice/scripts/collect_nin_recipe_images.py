"""Download original NIN recipe images; never infer ingredient grams or portions.

Examples (from repository root):
  chatbot/.venv/Scripts/python.exe foodcatalogservice/scripts/collect_nin_recipe_images.py --codes VPF-000039
  chatbot/.venv/Scripts/python.exe foodcatalogservice/scripts/collect_nin_recipe_images.py --codes-file selected-codes.txt --limit 40

This uses only Python's standard library and public source URLs. It resumes valid
local image files, preserves original API nutrient arrays, records image hashes,
and writes a separate review scaffold without overwriting extracted-recipes.json.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen

REPO_ROOT = Path(__file__).resolve().parents[3]
ROOT = REPO_ROOT / "BackendNutriMenu"
ORIGIN = "https://viendinhduong.vn"
PAGE = ORIGIN + "/vi/cong-cu-va-tien-ich/gia-tri-dinh-duong-mon-an"
CACHE = REPO_ROOT / "ai-service/data/structured/nin_dishes_2026-10-06/dishes_full.json"
DESTINATION = ROOT / "foodcatalogservice/src/main/resources/catalog/nin-real-20261123"


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def image_type(data):
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return ".png"
    if data.startswith(b"\xff\xd8\xff"):
        return ".jpg"
    if data.startswith((b"GIF87a", b"GIF89a")):
        return ".gif"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return ".webp"
    raise ValueError("Response is not a supported original image (PNG/JPEG/GIF/WebP)")


def choose(rows, codes, limit):
    by_code = {x["code"]: x for x in rows}
    if len(by_code) != len(rows):
        raise ValueError("Duplicate source codes in cache")
    if codes:
        missing = set(codes) - set(by_code)
        if missing:
            raise ValueError("Codes absent from cache: " + ", ".join(sorted(missing)))
        selected = [by_code[x] for x in dict.fromkeys(codes)]
    else:
        # Numeric source codes give deterministic candidates; this is not a
        # claim that every selected image contains a recipe or has solver coverage.
        selected = sorted((x for x in rows if x.get("image") and x.get("total_energy") is not None), key=lambda x: x["code"])
    return selected[:limit]


def download(url, attempts, timeout):
    request = Request(url, headers={"User-Agent": "testda-nin-recipe-data-collector/1.0", "Referer": PAGE, "Accept": "image/webp,image/*;q=0.9"})
    for attempt in range(attempts):
        try:
            with urlopen(request, timeout=timeout) as response:
                # Reject HTML error documents even when a server returns HTTP200.
                if not response.headers.get_content_type().startswith("image/"):
                    raise ValueError("Source returned non-image Content-Type")
                content = response.read(20_000_001)
                if len(content) > 20_000_000:
                    raise ValueError("Image exceeds 20MB limit")
                image_type(content)
                return content
        except HTTPError as exc:
            if exc.code in (401, 403, 404, 429) or attempt + 1 == attempts:
                raise
        except (URLError, TimeoutError):
            if attempt + 1 == attempts:
                raise
        time.sleep(min(2 ** attempt, 4))
    raise RuntimeError("Unreachable retry state")


def collect(rows, output, attempts=3, timeout=30, delay=.3, offline=False):
    image_dir = output / "images"
    image_dir.mkdir(parents=True, exist_ok=True)
    manifest, scaffold = [], []
    for row in rows:
        code = row["code"]
        if not re.fullmatch(r"[A-Za-z0-9_-]+", code):
            raise ValueError(f"Unsafe source code {code!r}")
        url = urljoin(ORIGIN, row.get("image", ""))
        if urlparse(url).scheme != "https" or urlparse(url).hostname != "viendinhduong.vn":
            raise ValueError(f"Unexpected source image origin for {code}")
        result = {"code": code, "sourceRecordId": row.get("_id"), "nameVi": row.get("name_vi"), "sourceUrl": PAGE,
                  "imageUrl": url, "sourceUpdatedAt": row.get("updated_at"), "status": "pending"}
        try:
            existing = [p for p in image_dir.glob(code + ".*") if p.suffix.lower() in {".webp", ".png", ".jpg", ".jpeg", ".gif"}]
            if len(existing) > 1:
                raise ValueError("Multiple image files for source code; review before continuing")
            if existing:
                target, content = existing[0], existing[0].read_bytes()
                image_type(content)
                result["status"] = "existing"
            elif offline:
                result["status"] = "not_downloaded_offline"
                content = None
            else:
                content = download(url, attempts, timeout)
                target = image_dir / (code + image_type(content))
                temporary = target.with_suffix(target.suffix + ".part")
                temporary.write_bytes(content)
                temporary.replace(target)
                result["status"] = "downloaded"
            if content is not None:
                result.update({"localPath": str(target.relative_to(output)).replace("\\", "/"), "bytes": len(content),
                               "sha256": hashlib.sha256(content).hexdigest()})
        except (OSError, ValueError) as exc:
            result.update({"status": "failed", "error": str(exc)})
        result["checkedAt"] = datetime.now(timezone.utc).isoformat()
        manifest.append(result)
        scaffold.append({"code": code, "name_vi": row.get("name_vi"), "source_url": PAGE, "source_record_id": row.get("_id"),
                         "image": result, "source_nutritional_components": row.get("nutritional_components", []),
                         "source_total_energy": row.get("total_energy"),
                         "serving_basis": {"description": None, "quantity_g": None, "certainty": "unreviewed"},
                         "ingredients": [], "evidence": {"image_verified": False, "unresolved": ["Read serving heading and every ingredient/gram value from the original image; compare image energy to API energy; do not assume 100g."]}})
        # Checkpoint after each record; interruption can resume using originals.
        write_json(output / "image-download-manifest.json", {"sourceCache": str(CACHE), "records": manifest})
        write_json(output / "recipes-to-review.json", scaffold)
        print(json.dumps({"code": code, "status": result["status"]}), flush=True)
        if not offline:
            time.sleep(delay)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source-json", type=Path, default=CACHE)
    parser.add_argument("--output", type=Path, default=DESTINATION)
    parser.add_argument("--codes", nargs="*")
    parser.add_argument("--codes-file", type=Path, help="UTF-8 text file with one source code per line")
    parser.add_argument("--limit", type=int, default=40)
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--attempts", type=int, default=3)
    parser.add_argument("--delay", type=float, default=.3)
    parser.add_argument("--offline", action="store_true", help="Verify existing local files only; make no network requests")
    args = parser.parse_args()
    if not 1 <= args.limit <= 1250 or not 1 <= args.attempts <= 5 or args.timeout < 1 or args.delay < 0:
        parser.error("Invalid limits/retry/timeout/delay")
    codes = args.codes or []
    if args.codes_file:
        codes.extend(x.strip() for x in args.codes_file.read_text(encoding="utf-8-sig").splitlines() if x.strip() and not x.startswith("#"))
    rows = json.loads(args.source_json.read_text(encoding="utf-8-sig"))
    records = collect(choose(rows, codes, args.limit), args.output, args.attempts, args.timeout, args.delay, args.offline)
    failed = sum(x["status"] not in {"existing", "downloaded"} for x in records)
    print(json.dumps({"selected": len(records), "available": len(records) - failed, "unavailable": failed}))
    if failed:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
