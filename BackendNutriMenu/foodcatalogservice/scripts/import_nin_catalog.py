"""Validate and import a reviewed NIN catalog without changing application code.

The import contract requires complete, evidenced dish nutrition. Unknown values
stay null in staging and nullable ingredient columns, never replaced with zero.
Running with no --apply validates and writes SQL only. Per-serving source totals
are inserted directly; no ingredient recipe recalculation is invoked.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import unicodedata

REPO_ROOT = Path(__file__).resolve().parents[3]
ROOT = REPO_ROOT / "BackendNutriMenu"
SEED = ROOT / "foodcatalogservice/src/main/resources/catalog/food-catalog-vi-qa.json"
INGREDIENT_NUTRIENTS = {
    "caloriesPer100g": "calories_per_100g", "totalCarbG": "total_carb_g",
    "fiberG": "fiber_g", "proteinG": "protein_g", "fatG": "fat_g",
    "saturatedFatG": "saturated_fat_g", "cholesterolMg": "cholesterol_mg",
    "sodiumMg": "sodium_mg", "potassiumMg": "potassium_mg",
    "purineMg": "purine_mg", "giValue": "gi_value",
}
# These five ingredient columns are NOT NULL in the unchanged application DB.
# Optional nutrient columns may retain genuine unknowns in this direct SQL path.
INGREDIENT_REQUIRED = {"caloriesPer100g", "totalCarbG", "fiberG", "proteinG", "fatG"}
DISH_NUTRIENTS = {
    "caloriesKcal": "calories_kcal", "totalCarbG": "total_carb_g",
    "fiberG": "fiber_g", "netCarbG": "net_carb_g", "proteinG": "protein_g",
    "fatG": "fat_g", "saturatedFatG": "saturated_fat_g",
    "cholesterolMg": "cholesterol_mg", "sodiumMg": "sodium_mg",
    "potassiumMg": "potassium_mg", "purineMg": "purine_mg",
    "estimatedGi": "estimated_gi", "glycemicLoad": "glycemic_load",
}
SCALED = {"calories_scaled_x10": "caloriesKcal", "net_carb_scaled_x10": "netCarbG",
          "fiber_scaled_x10": "fiberG", "protein_scaled_x10": "proteinG",
          "fat_scaled_x10": "fatG", "gl_scaled_x10": "glycemicLoad",
          "sodium_scaled_x10": "sodiumMg", "cholesterol_scaled_x10": "cholesterolMg"}
GROUPS = set("RICE_GRAIN NOODLE_TUBER PORK BEEF POULTRY FISH_SEAFOOD EGG SOY_TOFU LEAFY_VEG ROOT_FRUIT_VEG FRUIT MILK_DAIRY NUT_SEED OIL_FAT CONDIMENT MIXED".split())
METHODS = set("RAW BOILED STEAMED STIR_FRIED BRAISED PAN_FRIED GRILLED SOUP_SIMMERED".split())
ROLES = set("MAIN_STARCH PROTEIN_DISH VEGGIE_DISH SOUP COMPOSITE_DISH SNACK".split())
SLOTS = set("BREAKFAST MORNING_SNACK LUNCH AFTERNOON_SNACK DINNER".split())
TAGS = set("HIGH_GI HIGH_GL HIGH_SODIUM HIGH_SATURATED_FAT HIGH_PURINE HIGH_POTASSIUM ALLERGEN_SEAFOOD ALLERGEN_PEANUT ALLERGEN_EGG ALLERGEN_DAIRY ALLERGEN_SOY VEGETARIAN".split())


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def number(value, label, maximum=None, positive=False):
    if value is None or isinstance(value, bool):
        raise ValueError(f"{label}: missing/invalid number; unknown is not zero")
    try:
        result = Decimal(str(value))
    except InvalidOperation as exc:
        raise ValueError(f"{label}: invalid number") from exc
    if not result.is_finite() or result < 0 or (positive and result == 0):
        raise ValueError(f"{label}: must be finite and {'positive' if positive else 'nonnegative'}")
    if maximum is not None and result > maximum:
        raise ValueError(f"{label}: exceeds {maximum}")
    return result


def evidence(value, label):
    if not isinstance(value, dict) or not value.get("sourceUrl") or not value.get("method"):
        raise ValueError(f"{label}: sourceUrl and method required")
    if value["method"] not in {"source", "calculated", "reviewed_proxy"}:
        raise ValueError(f"{label}: unknown evidence method")
    if value["method"] != "source" and not value.get("explanation"):
        raise ValueError(f"{label}: explain calculation/proxy")


def validate(data, base_dir):
    if data.get("metadata", {}).get("schemaVersion") != 1:
        raise ValueError("metadata.schemaVersion must be 1")
    ingredients, dishes = data.get("ingredients", []), data.get("dishes", [])
    if not ingredients or not dishes:
        raise ValueError("Import needs nonempty ingredients and dishes; unextracted sources are staging data")
    by_code = {}
    for row in ingredients:
        code = row.get("vfcCode", "")
        if not re.fullmatch(r"NIN_REAL_[A-Za-z0-9_]+", code) or len(code) > 30 or code in by_code:
            raise ValueError(f"Invalid/duplicate dedicated ingredient code {code!r}")
        by_code[code] = row
        if not row.get("nameVi") or row.get("ingredientGroup") not in GROUPS:
            raise ValueError(f"{code}: nameVi/ingredientGroup required")
        if row.get("ediblePortionPct") is not None:
            number(row["ediblePortionPct"], code + ".ediblePortionPct", 100, True)
        for key in INGREDIENT_NUTRIENTS:
            if row.get(key) is None and key not in INGREDIENT_REQUIRED:
                continue
            number(row.get(key), code + "." + key, 100 if key == "giValue" else None)
            evidence(row.get("nutrientEvidence", {}).get(key), code + "." + key)
        if "allergenTags" not in row or not isinstance(row["allergenTags"], list) or not set(row["allergenTags"]) <= TAGS:
            raise ValueError(f"{code}: invalid allergen tag")
        evidence(row.get("allergenEvidence"), code + ".allergenEvidence")
    codes = set()
    for dish in dishes:
        code = dish.get("code", "")
        if not re.fullmatch(r"NIN_REAL_[A-Za-z0-9_]+", code) or len(code) > 50 or code in codes:
            raise ValueError(f"Invalid/duplicate dedicated dish code {code!r}")
        codes.add(code)
        if not dish.get("nameVi") or dish.get("dishRole") not in ROLES or dish.get("cookingMethod") not in METHODS:
            raise ValueError(f"{code}: name/role/cooking method required")
        if dish.get("primaryIngredientGroup") not in GROUPS or not dish.get("householdUnitLabel"):
            raise ValueError(f"{code}: primaryIngredientGroup/householdUnitLabel required")
        if not dish.get("mealSlots") or not set(dish["mealSlots"]) <= SLOTS:
            raise ValueError(f"{code}: invalid/empty mealSlots")
        number(dish.get("standardServingCookedG"), code + ".standardServingCookedG", positive=True)
        evidence(dish.get("servingEvidence"), code + ".servingEvidence")
        if not str(dish.get("imageUrl", "")).startswith(("https://", "/")) or len(dish["imageUrl"]) > 500:
            raise ValueError(f"{code}: valid imageUrl required")
        image = dish.get("imageEvidence", {})
        image_file = (base_dir / image.get("localPath", "")).resolve()
        if not image_file.is_file() or hashlib.sha256(image_file.read_bytes()).hexdigest() != image.get("sha256"):
            raise ValueError(f"{code}: downloaded image missing or hash mismatch")
        if not image.get("sourceUrl") or not dish.get("recipeVerifiedFromImage"):
            raise ValueError(f"{code}: image source + verified actual ingredient grams required")
        nutrition = dish.get("nutritionPerServing", {})
        for key in DISH_NUTRIENTS:
            number(nutrition.get(key), code + "." + key, 100 if key == "estimatedGi" else None)
            evidence(dish.get("nutrientEvidence", {}).get(key), code + "." + key)
        if abs(number(nutrition["netCarbG"], code) - max(Decimal(0), number(nutrition["totalCarbG"], code) - number(nutrition["fiberG"], code))) > Decimal("0.02"):
            raise ValueError(f"{code}: net carbohydrate inconsistent")
        if abs(number(nutrition["glycemicLoad"], code) - number(nutrition["estimatedGi"], code) * number(nutrition["netCarbG"], code) / 100) > Decimal("0.02"):
            raise ValueError(f"{code}: glycemic load inconsistent")
        recipe = dish.get("recipeItems", [])
        if not recipe or not any(x.get("mainIngredient") is True for x in recipe):
            raise ValueError(f"{code}: recipe and main ingredient required")
        seen = set()
        for item in recipe:
            ing = item.get("ingredientCode")
            if ing not in by_code or ing in seen:
                raise ValueError(f"{code}: missing/duplicate ingredient {ing}")
            seen.add(ing)
            for key in ("rawWeightG", "cookedWeightG", "appliedYieldFactor"):
                number(item.get(key), code + "." + key, positive=True)
            if abs(number(item["rawWeightG"], code) * number(item["appliedYieldFactor"], code) - number(item["cookedWeightG"], code)) > Decimal("0.02"):
                raise ValueError(f"{code}: inconsistent ingredient yield")
            evidence(item.get("gramEvidence"), code + ".ingredientGrams")
        if abs(sum(number(x["cookedWeightG"], code) for x in recipe) - number(dish["standardServingCookedG"], code)) > Decimal("0.05"):
            raise ValueError(f"{code}: cooked recipe weight does not equal declared serving")
        rules = [dish.get(k) for k in ("minServingScaledX2", "maxServingScaledX2", "stepServingScaledX2")]
        if not all(isinstance(x, int) and not isinstance(x, bool) and x > 0 for x in rules) or rules[0] > rules[1] or (rules[1] - rules[0]) % rules[2]:
            raise ValueError(f"{code}: invalid serving rules")
        if dish["dishRole"] == "COMPOSITE_DISH" and rules != [2, 2, 2]:
            raise ValueError(f"{code}: composite dish serving rules must be [2,2,2]")
        if not set(dish.get("medicalTags", [])) <= TAGS:
            raise ValueError(f"{code}: invalid medicalTags")
    return ingredients, dishes


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float, Decimal)):
        return str(number(value, "SQL number"))
    if "\x00" in str(value):
        raise ValueError("NUL character cannot be stored in PostgreSQL text")
    return "'" + str(value).replace("'", "''") + "'"


def normalized(value):
    return "".join(x for x in unicodedata.normalize("NFD", value.lower().replace("đ", "d")) if unicodedata.category(x) != "Mn")


def gi_level(value):
    if value is None:
        # Database enum has no UNKNOWN; nullable ingredient gi_value retains the
        # distinction from measured numeric zero. Dish GI cannot be missing.
        return "NONE"
    value = Decimal(str(value))
    return "NONE" if value == 0 else "LOW" if value <= 55 else "MEDIUM" if value <= Decimal("69.9") else "HIGH"


def medical_tags(dish, ingredients):
    """Preserve reviewed/allergen tags and apply existing application thresholds."""
    n = dish["nutritionPerServing"]
    tags = set(dish.get("medicalTags", []))
    rows = [ingredients[x["ingredientCode"]] for x in dish["recipeItems"]]
    for row in rows:
        tags.update(row.get("allergenTags", []))
    for key, limit, tag in [("estimatedGi", 70, "HIGH_GI"), ("glycemicLoad", 20, "HIGH_GL")]:
        if Decimal(str(n[key])) >= limit:
            tags.add(tag)
    for key, limit, tag in [("sodiumMg", 600, "HIGH_SODIUM"), ("purineMg", 150, "HIGH_PURINE"), ("potassiumMg", 400, "HIGH_POTASSIUM")]:
        if Decimal(str(n[key])) > limit:
            tags.add(tag)
    if Decimal(str(n["saturatedFatG"])) > 5 or Decimal(str(n["cholesterolMg"])) > 100 or dish["cookingMethod"] == "PAN_FRIED":
        tags.add("HIGH_SATURATED_FAT")
    if not any(x["ingredientGroup"] in {"PORK", "BEEF", "POULTRY", "FISH_SEAFOOD", "EGG"} for x in rows):
        tags.add("VEGETARIAN")
    return sorted(tags)


def insert(table, values):
    return f"INSERT INTO {table} ({','.join(values)}) VALUES ({','.join(literal(v) for v in values.values())});"


def sql_for(data, retired_codes):
    ingredients, dishes = data["ingredients"], data["dishes"]
    by_code = {x["vfcCode"]: x for x in ingredients}
    imported_codes = ",".join(literal(x["code"]) for x in dishes)
    ingredient_codes = ",".join(literal(x["vfcCode"]) for x in ingredients)
    lines = ["BEGIN;", "SET LOCAL standard_conforming_strings=on;", "SELECT pg_advisory_xact_lock(8132,1);",
             "LOCK TABLE dishes IN SHARE ROW EXCLUSIVE MODE;",
             f"DO $$ BEGIN IF EXISTS (SELECT 1 FROM dishes WHERE code IN ({imported_codes})) OR EXISTS (SELECT 1 FROM ingredients WHERE vfc_code IN ({ingredient_codes})) THEN RAISE EXCEPTION 'Dedicated NIN import codes already exist; no rows changed'; END IF; END $$;"]
    for row in ingredients:
        values = {"vfc_code": row["vfcCode"], "name_vi": row["nameVi"], "name_normalized": normalized(row["nameVi"]),
                  "ingredient_group": row["ingredientGroup"], "edible_portion_pct": row.get("ediblePortionPct"),
                  **{column: row.get(key) for key, column in INGREDIENT_NUTRIENTS.items()}, "gi_level": gi_level(row.get("giValue"))}
        lines.append(insert("ingredients", values))
        for tag in row.get("allergenTags", []):
            lines.append(f"INSERT INTO ingredient_allergens(ingredient_id,allergen_tag) SELECT id,{literal(tag)} FROM ingredients WHERE vfc_code={literal(row['vfcCode'])};")
    for dish in dishes:
        n = dish["nutritionPerServing"]
        values = {"code": dish["code"], "name_vi": dish["nameVi"], "name_normalized": normalized(dish["nameVi"]),
                  "image_url": dish["imageUrl"], "diabetic_cooking_tip": dish.get("diabeticCookingTip"),
                  "cooking_method": dish["cookingMethod"], "dish_role": dish["dishRole"],
                  "primary_ingredient_group": dish["primaryIngredientGroup"], "standard_serving_cooked_g": dish["standardServingCookedG"],
                  "household_unit_label": dish["householdUnitLabel"], **{column: n[key] for key, column in DISH_NUTRIENTS.items()},
                  **{column: int((Decimal(str(n[key])) * 10).quantize(Decimal(1), rounding="ROUND_HALF_UP")) for column, key in SCALED.items()},
                  "gi_level": gi_level(n["estimatedGi"]), "min_serving_scaled_x2": dish["minServingScaledX2"],
                  "max_serving_scaled_x2": dish["maxServingScaledX2"], "step_serving_scaled_x2": dish["stepServingScaledX2"],
                  "is_active": True, "popularity_score": dish.get("popularityScore", 100), "lock_manual_tags": True}
        lines.append(insert("dishes", values))
        for slot in dish["mealSlots"]:
            lines.append(f"INSERT INTO dish_meal_slots(dish_id,meal_slot) SELECT id,{literal(slot)} FROM dishes WHERE code={literal(dish['code'])};")
        for tag in medical_tags(dish, by_code):
            lines.append(f"INSERT INTO dish_medical_tags(dish_id,medical_tag) SELECT id,{literal(tag)} FROM dishes WHERE code={literal(dish['code'])};")
        for item in dish["recipeItems"]:
            lines.append("INSERT INTO dish_ingredients(dish_id,ingredient_id,raw_weight_g,applied_yield_factor,cooked_weight_g,is_main_ingredient) "
                         f"SELECT d.id,i.id,{literal(item['rawWeightG'])},{literal(item['appliedYieldFactor'])},{literal(item['cookedWeightG'])},{literal(item['mainIngredient'])} FROM dishes d CROSS JOIN ingredients i WHERE d.code={literal(dish['code'])} AND i.vfc_code={literal(item['ingredientCode'])};")
    if retired_codes:
        lines.append(f"UPDATE dishes SET is_active=FALSE WHERE code IN ({','.join(literal(x) for x in retired_codes)});")
    lines.append("COMMIT;")
    return "\n".join(lines) + "\n"


def run(command, **kwargs):
    return subprocess.run(command, check=True, capture_output=True, text=True, encoding="utf-8", **kwargs).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--database", default="diabetes_food_catalog_db")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", default="5432")
    parser.add_argument("--username", default="postgres")
    parser.add_argument("--psql", default="psql")
    parser.add_argument("--pg-dump", default="pg_dump")
    args = parser.parse_args()
    data = read_json(args.catalog)
    validate(data, args.catalog.parent)
    retired_codes = [x["code"] for x in read_json(SEED)["dishes"]]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    seed_backup = output / "food-catalog-vi-qa.original.json"
    shutil.copyfile(SEED, seed_backup)
    source_hash = hashlib.sha256(args.catalog.read_bytes()).hexdigest()
    (output / "reviewed-catalog.json").write_bytes(args.catalog.read_bytes())
    sql_path = output / "apply.sql"
    sql_path.write_text(sql_for(data, retired_codes), encoding="utf-8")
    report = {"validatedAt": datetime.now(timezone.utc).isoformat(), "sourceSha256": source_hash,
              "originalSeedSha256": hashlib.sha256(seed_backup.read_bytes()).hexdigest(),
              "dishCount": len(data["dishes"]), "ingredientCount": len(data["ingredients"]),
              "applied": False, "retiredCodes": retired_codes,
              "dishRoles": dict(Counter(x["dishRole"] for x in data["dishes"])),
              "mealSlots": dict(Counter(slot for dish in data["dishes"] for slot in dish["mealSlots"])),
              "unknownIngredientNutrients": {row["vfcCode"]: [key for key in INGREDIENT_NUTRIENTS if row.get(key) is None] for row in data["ingredients"] if any(row.get(key) is None for key in INGREDIENT_NUTRIENTS)},
              "nutritionRecalculationWarning": "Stored per-serving source nutrition is preserved by startup. Later recipe/ingredient edits or recalculate API calls use the existing engine and can replace source totals or fail on preserved null ingredient nutrients. Do not recalculate this source-total dataset without first reviewing complete ingredient data."}
    if args.apply:
        # pg_dump must succeed before any mutation. Credentials use normal libpq
        # PGPASSFILE/PGPASSWORD; neither passwords nor command output are logged.
        connection = ["--host", args.host, "--port", args.port, "--username", args.username, "--dbname", args.database, "--no-password"]
        run([args.pg_dump, *connection, "--format=custom", "--file", str(output / "catalog.before.dump")])
        raw = run([args.psql, *connection, "-XAt", "--set=ON_ERROR_STOP=1", "--command", "SELECT COALESCE(json_agg(json_build_object('code',code,'isActive',is_active)), '[]'::json) FROM dishes;"])
        statuses = json.loads(raw.strip())
        (output / "statuses.before.json").write_text(json.dumps(statuses, ensure_ascii=False, indent=2), encoding="utf-8")
        rollback = ["BEGIN;", "SET LOCAL standard_conforming_strings=on;", "SELECT pg_advisory_xact_lock(8132,1);"]
        rollback.extend(f"UPDATE dishes SET is_active={literal(x['isActive'])} WHERE code={literal(x['code'])};" for x in statuses if x["code"] in retired_codes)
        rollback.append(f"UPDATE dishes SET is_active=FALSE WHERE code IN ({','.join(literal(x['code']) for x in data['dishes'])});")
        rollback.append("COMMIT;")
        (output / "rollback.sql").write_text("\n".join(rollback) + "\n", encoding="utf-8")
        run([args.psql, *connection, "-X", "--set=ON_ERROR_STOP=1", "--file", str(sql_path)])
        report["applied"] = True
        report["note"] = "Old IDs, rows, recipes and plans retained; rollback.sql restores prior QA statuses and disables imported rows."
    (output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"validated": True, "dishCount": report["dishCount"], "applied": report["applied"]}))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        raise SystemExit(f"Import stopped: {exc}")
