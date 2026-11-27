"""Audit locally cached NIN evidence without guessing portions or nutrients."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
ROOT = REPO_ROOT / "BackendNutriMenu"
DEFAULT = ROOT / "foodcatalogservice/src/main/resources/catalog/nin-real-20261123"


def load(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT / "source-readiness.json")
    args = parser.parse_args()
    report = {"auditedAt": datetime.now(timezone.utc).isoformat(), "inferencePolicy": "No missing nutrients replaced with zero; image recipe grams and portion basis require source verification.", "datasets": {}}
    for dataset, filename, components in [("dishes", "dishes_full.json", "nutritional_components"), ("foods", "foods_full.json", "nutrition")]:
        folder = ROOT / f"chatbot/data/structured/nin_{dataset}_2026-10-06"
        path = folder / filename
        rows = load(path)
        nutrient_names = Counter()
        populated = Counter()
        for row in rows:
            for nutrient in row.get(components, []):
                key = nutrient.get("key", "")
                nutrient_names[key] += 1
                value = nutrient.get("amount" if dataset == "dishes" else "value")
                if value is not None and value != "":
                    populated[key] += 1
        entry = {"path": str(path.relative_to(ROOT)).replace("\\", "/"), "sha256": sha(path), "rowCount": len(rows),
                 "nutrientPopulatedCounts": dict(sorted(populated.items())),
                 "giFieldPresent": any("glycemic" in key or "glycaemic" in key or key == "gi" for key in nutrient_names),
                 "purineFieldPresent": any("purin" in key for key in nutrient_names)}
        if dataset == "dishes":
            entry.update({"rowsWithImagePath": sum(bool(x.get("image")) for x in rows),
                          "rowsWithNonemptyDishComponents": sum(bool(x.get("dish_components")) for x in rows),
                          "portionWarning": "Cached normalized manifest includes an owner-confirmed 100 g basis. This audit does not treat it as verified image recipe serving weight or gram ingredients."})
        report["datasets"][dataset] = entry
    recipes = DEFAULT / "extracted-recipes.json"
    if recipes.is_file():
        extracted = load(recipes)
        rows = extracted if isinstance(extracted, list) else extracted.get("recipes", [])
        report["imageRecipeExtraction"] = {"path": str(recipes.relative_to(ROOT)).replace("\\", "/"), "sha256": sha(recipes), "rowCount": len(rows)}
    else:
        report["imageRecipeExtraction"] = {"rowCount": 0, "state": "No image-extracted recipes available"}
    report["activeImportReady"] = False
    report["blockers"] = [
        "Raw cached dish_components are not a substitute for ingredient grams read from each source recipe image.",
        "Cached NIN fields do not contain GI or purine; absent values cannot be imported as zero.",
        "Current application requires numeric ingredient GI/fiber and numeric dish nutrition, and substitutes zero for several missing ingredient nutrients.",
        "No active dataset switch is permitted until reviewed complete nutrition, actual recipe grams, portion evidence and downloaded source images pass import validation.",
    ]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"dishes": report["datasets"]["dishes"]["rowCount"], "foods": report["datasets"]["foods"]["rowCount"], "activeImportReady": False}))


if __name__ == "__main__":
    main()
