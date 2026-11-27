"""Rebuild the NIN-only source-serving catalog with auditable estimates.

Source observations always win. Missing values need explicit ingredient evidence;
a null observation is never silently converted to zero. No legacy dish is used.
"""
from __future__ import annotations

import argparse
from collections import Counter
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import json
from pathlib import Path

from nin_ingredient_model import LINKS

REPO_ROOT = Path(__file__).resolve().parents[3]
ROOT = REPO_ROOT / "BackendNutriMenu"
FOLDER = ROOT / "foodcatalogservice/src/main/resources/catalog/nin-real-20261123"
NIN_FOODS = REPO_ROOT / "ai-service/data/structured/nin_foods_2026-10-06/foods_full.json"
NIN_URL = "https://viendinhduong.vn/vi/cong-cu-va-tien-ich/gia-tri-dinh-duong-thuc-pham"
GI_URL = "https://pmc.ncbi.nlm.nih.gov/articles/PMC2584181/"
RECIPE_URL = "https://www.fao.org/4/y4705e/y4705e23.htm"
GI_FORMULA_URL = "https://www.fao.org/4/w8079e/w8079e0a.htm"
FIELDS = {"totalCarbG": "carbohydrate-by-difference", "fiberG": "fibre-total-dietary",
          "proteinG": "protein", "fatG": "total-lipid-fat", "saturatedFatG": "fatty-acids-total-saturated",
          "cholesterolMg": "cholesterol", "sodiumMg": "na", "potassiumMg": "k"}
REQUIRED = ("caloriesKcal", "netCarbG", "fiberG", "proteinG", "fatG", "estimatedGi",
            "glycemicLoad", "sodiumMg", "cholesterolMg")
# Table 1 means or the authors' explicitly suggested low-carbohydrate assignments.
# These are recipe model inputs, never measurements of the NIN dishes.
GI = {"rice": (73, "BOILED_WHITE_RICE_MEAN_PROXY"), "sugar": (65, "SUCROSE_MEAN_PROXY"),
      "carrot": (39, "BOILED_CARROT_MEAN_PROXY"), "pumpkin": (64, "BOILED_PUMPKIN_MEAN_PROXY"),
      "pineapple": (59, "RAW_PINEAPPLE_MEAN_PROXY"), "orange": (43, "RAW_ORANGE_MEAN_PROXY"),
      "apple": (36, "RAW_APPLE_MEAN_PROXY"), "soy": (16, "SOYBEAN_MEAN_FOOD_PROXY"),
      "vegetable": (40, "AUTHORS_LOW_CARBOHYDRATE_VEGETABLE_IMPUTATION"),
      "dairy": (30, "AUTHORS_LOW_CARBOHYDRATE_DAIRY_IMPUTATION")}
# Unquantified recipe rows are annotations, not additional zero-gram ingredients.
ANNOTATIONS = {"Muối trong dưa cải bẹ*", "Nước canh", "Gia vị*"}
BONE_SHELL = {"Cá lóc (cả xương)", "Cá bống (nguyên con)", "Trai (cả vỏ)",
              "Thịt gà (cả xương)", "Sườn lợn (cả xương)"}
ROLE_OVERRIDES = {
    **{code: "COMPOSITE_DISH" for code in ("000008", "000010", "000011", "000012", "000013",
                                           "000014", "000015", "000016")},
    **{code: "VEGGIE_DISH" for code in ("000071", "000077", "000089", "000155")},
    "000398": "SNACK", "000423": "SNACK",
}


def load(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def rounded(value):
    return float(Decimal(str(value)).quantize(Decimal(".01"), rounding=ROUND_HALF_UP))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ingredient_models(pending, references):
    foods = {x["code"]: x for x in load(NIN_FOODS)}
    overrides = {x["sourceName"]: x for x in references["ingredients"]}
    if len(overrides) != len(references["ingredients"]):
        raise ValueError("Duplicate ingredient reference name")
    models = {}
    for row in pending["ingredients"]:
        name = row["nameVi"]
        if name not in LINKS:
            raise ValueError(f"Unreviewed ingredient identity: {name}")
        code, group, gi_key, limitation = LINKS[name]
        override = overrides.get(name, {})
        code = override.get("ninFoodCode", code)
        food = foods.get(code) if code else None
        if code and food is None:
            raise ValueError(f"Unknown NIN food code {code}: {name}")
        nutrients, evidence = {}, {}
        if food:
            values = {x["key"]: x for x in food.get("nutrition", [])}
            for field, key in FIELDS.items():
                value = values.get(key, {}).get("value")
                if value is not None:
                    nutrients[field] = value
                    evidence[field] = {"method": "NIN_INGREDIENT_COMPOSITION", "sourceCode": code,
                                       "sourceRecordId": food["_id"], "sourceUrl": NIN_URL,
                                       "sourceName": food["name_vi"], "basis": "PER_100G_EDIBLE"}
        for field, observation in override.get("nutrients", {}).items():
            if nutrients.get(field) is not None:
                continue
            if observation.get("value") is None:
                continue
            reference_id = observation["referenceId"]
            if reference_id not in references["references"]:
                raise ValueError(f"Unknown reference {reference_id}: {name}/{field}")
            value = observation["value"]
            if value < 0:
                raise ValueError(f"Negative ingredient observation: {name}/{field}")
            # The explicit snapshot can select a better matching prepared form;
            # original dish totals still always take priority below.
            nutrients[field] = value
            evidence[field] = {**observation, "method": observation.get("method", "REFERENCED_INGREDIENT_PROXY"),
                               "reference": references["references"][reference_id], "basis": "PER_100G_EDIBLE"}
        fraction = override.get("edibleFraction", None if name in BONE_SHELL else 1)
        if fraction is not None and not 0 < fraction <= 1:
            raise ValueError(f"Invalid edible fraction: {name}")
        models[row["vfcCode"]] = {"code": row["vfcCode"], "sourceName": name,
            "ninFoodCode": code, "ninFoodName": food["name_vi"] if food else None,
            "group": override.get("group", group), "giModelKey": gi_key,
            "identityLimitation": limitation, "nutrients": nutrients, "perFieldEvidence": evidence,
            "sourceFoodEnergyKcalPer100g": food.get("energy") if food else None,
            "sourceQualityWarnings": ["NIN_WEB_4139_SEAWEED_COMPOSITION_ANOMALY: source energy/fat inconsistent with the dried nori proxy; seaweed form/species unknown. Original source ingredient observations retained; only missing fields use explicit MEXT proxy."] if code == "4139" else [],
            "edibleFraction": fraction,
            "edibleFractionEvidence": override.get("edibleFractionEvidence", {
                "method": "RECIPE_INGREDIENT_AMOUNT_ASSUMED_EDIBLE", "sourceUrl": RECIPE_URL,
                "note": "Estimate: named edible recipe amounts used as edible grams; trimming/cooking retention not measured."
            } if fraction is not None else {"method": "UNKNOWN_BONE_OR_SHELL_DISCARD"})}
    return models


def recipe_sum(items, models, field):
    total, components, missing = Decimal(0), [], []
    for item in items:
        model = models[item["ingredientCode"]]
        value = model["nutrients"].get(field)
        fraction = model["edibleFraction"]
        if value is None or (value != 0 and fraction is None):
            missing.append(model["sourceName"])
            continue
        # An observed zero remains zero regardless of unknown edible fraction.
        contribution = Decimal(0) if value == 0 else Decimal(str(value)) * Decimal(str(item["rawWeightG"])) * Decimal(str(fraction)) / 100
        total += contribution
        components.append({"ingredientCode": model["code"], "sourceName": model["sourceName"],
                           "recipeWeightG": item["rawWeightG"], "edibleFraction": fraction,
                           "per100g": value, "contribution": rounded(contribution),
                           "evidence": model["perFieldEvidence"][field]})
    if missing:
        return None, {"method": "UNRESOLVED_RECIPE_ESTIMATE", "missingIngredients": missing}
    return rounded(total), {"method": "INGREDIENT_RECIPE_SUM_ESTIMATE", "sourceUrl": RECIPE_URL,
                           "formula": "sum(per100g * source grams * edible fraction / 100)",
                           "retentionAssumption": "1.0; not measured; no cooked mass or nutrient retention claim",
                           "components": components}


def estimate_gi(nutrition, items, models):
    carb = nutrition.get("netCarbG")
    if carb is None:
        return None, None, {"method": "UNKNOWN_CARBOHYDRATE"}
    if carb == 0:
        return 0, 0, {"method": "NOT_APPLICABLE_ZERO_CARBOHYDRATE", "giStatus": "NOT_APPLICABLE",
                       "note": "Operational sentinel 0, not measured GI zero."}
    if len(items) == 1 and models[items[0]["ingredientCode"]]["giModelKey"] == "trace_animal":
        return 0, rounded(carb), {"method": "NOT_APPLICABLE_ANIMAL_FOOD", "giStatus": "NOT_APPLICABLE",
            "sourceUrl": "https://glycemicindex.com/2006/06/feedback-your-faqs-answered-17/",
            "note": "Unprocessed meat/fish/egg has no measurable GI. Sentinel 0 is not a measurement; GL uses the model screening assumption GI=100 on all source carbohydrate. Not a guaranteed physiological upper bound: measured food GI can exceed 100.",
            "glMethod": "GL_UPPER_BOUND_TRACE_CARBOHYDRATE", "glAssumedGi": 100}
    weighted, carbs, components, unknown = Decimal(0), Decimal(0), [], []
    for item in items:
        model = models[item["ingredientCode"]]
        amount = model["nutrients"].get("totalCarbG")
        fraction = model["edibleFraction"]
        if amount is None or (amount != 0 and fraction is None):
            unknown.append(model["sourceName"])
            continue
        if amount == 0:
            continue
        weight = Decimal(str(amount)) * Decimal(str(item["rawWeightG"])) * Decimal(str(fraction)) / 100
        key = model["giModelKey"]
        value, method = GI.get(key, (100, "UNMEASURED_COMPONENT_CONSERVATIVE_GI100_BOUND"))
        weighted += weight * value
        carbs += weight
        components.append({"ingredientCode": model["code"], "sourceName": model["sourceName"],
                           "carbohydrateWeightG": rounded(weight), "giModelValue": value,
                           "method": method, "sourceUrl": GI_URL if key in GI else GI_FORMULA_URL})
    if unknown:
        # No invented composition for unresolved ingredients. A conservative model
        # bound is explicitly distinct from a measured or weighted estimate.
        value = 100
        method = "UNRESOLVED_COMPONENT_RECIPE_GI100_BOUND_ESTIMATE"
    elif carbs:
        value = rounded(weighted / carbs)
        method = "CARBOHYDRATE_WEIGHTED_INGREDIENT_GI_ESTIMATE"
    else:
        value = 100
        method = "UNRESOLVED_CARBOHYDRATE_GI100_BOUND_ESTIMATE"
    return value, rounded(Decimal(str(value)) * Decimal(str(carb)) / 100), {
        "method": method, "giStatus": "ESTIMATED_NOT_MEASURED", "sourceUrl": GI_FORMULA_URL,
        "ingredientGiReference": GI_URL, "components": components, "unknownIngredients": unknown,
        "screeningAssumption": "Unmeasured components use GI100 within this model's 0..100 input range. This is not a guaranteed physiological upper bound; some measured food GI values exceed 100.",
        "carbohydrateDefinition": "Full source Glucid retained conservatively; ingredient carbohydrate used only for relative weights.",
        "limitations": "Not a measured mixed-meal GI; cultivar, preparation, cooking/retention, protein/fat interactions and unquantified source annotations uncertain."}


def build_outputs(folder=FOLDER):
    pending = load(folder / "food-catalog-nin-pending.json")
    provenance = load(folder / "nin-catalog-provenance.json")
    readiness = load(folder / "nin-catalog-readiness.json")
    references = load(folder / "ingredient-estimation-reference.json")
    models = ingredient_models(pending, references)
    by_code = {x["code"]: x for x in pending["dishes"]}
    sources = {x["code"]: x for x in provenance["dishes"]}
    dishes = []
    for row in readiness["dishes"]:
        code = row["code"]
        seed, source = by_code[code], sources[code]
        recipe = source["imageExtractedRecipe"]
        nutrition = dict(row["sourceNumericFieldsUnscaled"])
        evidence = {key: {"method": "NIN_SOURCE_AMOUNT", "sourceRecordId": source["sourceRecordId"],
                          "basis": "SOURCE_SERVING_INFERRED_FROM_MATCHING_IMAGE_ENERGY"}
                    for key, value in nutrition.items() if value is not None}
        items, annotations = [], []
        for item, original in zip(seed["recipeItems"], recipe["ingredients"], strict=True):
            model = models[item["ingredientCode"]]
            if item["rawWeightG"] is None and model["sourceName"] in ANNOTATIONS:
                annotations.append({**original, "reason": "SOURCE_UNQUANTIFIED_ANNOTATION_NOT_ZERO_GRAMS"})
                continue
            if item["rawWeightG"] is None or item["rawWeightG"] <= 0:
                raise ValueError(f"Missing quantitative ingredient amount {code}/{model['sourceName']}")
            items.append({"ingredientCode": item["ingredientCode"], "rawWeightG": item["rawWeightG"],
                          "mainIngredient": item["mainIngredient"], "sourceAmountText": original.get("source_text")})
        for field in ("totalCarbG", "fiberG", "fatG", "cholesterolMg"):
            if nutrition.get(field) is None:
                estimate, detail = recipe_sum(items, models, field)
                nutrition[field] = estimate
                evidence[field] = detail
        if nutrition.get("totalCarbG") is not None:
            nutrition["netCarbG"] = nutrition["totalCarbG"]
            evidence["netCarbG"] = {"method": "CONSERVATIVE_CARBOHYDRATE_ESTIMATE" if row["sourceNumericFieldsUnscaled"]["totalCarbG"] is None else "CONSERVATIVE_SOURCE_CARBOHYDRATE",
                "note": "Source Glucid definition unspecified: never subtract fibre or replace source carbohydrate."}
        if nutrition.get("estimatedGi") is None:
            gi, gl, detail = estimate_gi(nutrition, items, models)
            nutrition["estimatedGi"], nutrition["glycemicLoad"] = gi, gl
            evidence["estimatedGi"] = detail
            evidence["glycemicLoad"] = {"method": detail.get("glMethod", "CALCULATED_ESTIMATED_GI_X_CONSERVATIVE_CARB"),
                "formula": "GI * source/estimated carbohydrate / 100" if detail.get("glMethod") is None else "100 * source carbohydrate / 100",
                "giEvidenceField": "estimatedGi", "notMeasured": True}
        missing = [key for key in REQUIRED if nutrition.get(key) is None]
        suffix = code.removeprefix("NIN_REAL_VPF_")
        role = ROLE_OVERRIDES.get(suffix, seed["dishRole"])
        fruit = role == "SNACK"
        sushi = suffix in {"000014", "000015", "000016"}
        slots = ["MORNING_SNACK", "AFTERNOON_SNACK"] if fruit else seed["mealSlots"]
        if role == "COMPOSITE_DISH":
            slots = ["BREAKFAST", "LUNCH", "DINNER"]
        quality = {"carbohydrateDefinition": "UNSPECIFIED_SOURCE_GLUCID_CONSERVATIVE_NET_CARB",
            "perFieldEvidence": evidence, "cookingMethodStatus": "INFERRED_FROM_DISH_NAME_NOT_SOURCE_INSTRUCTIONS",
            "sourceNutritionUnmodified": source["sourceNutritionUnmodified"], "imageSha256": source["image"]["sha256"],
            "servingBasis": source["servingBasis"], "cookedWeightStatus": "UNKNOWN_NOT_RAW_SUM",
            "unknownFields": [key for key, value in nutrition.items() if value is None],
            "solverMissingRequiredFields": missing, "originalRecipe": recipe["ingredients"],
            "nonQuantifiedSourceAnnotations": annotations, "shoppingListCompleteness": "QUANTIFIED_SOURCE_ITEMS_ONLY" if annotations else "SOURCE_RECIPE_ITEMS",
            "ingredientModelSnapshot": "ingredient-model-links.json", "clinicalCompletenessClaimed": False}
        quality["ingredientSourceQualityWarnings"] = [warning for item in items for warning in models[item["ingredientCode"]]["sourceQualityWarnings"]]
        quality["portionScaling"] = {"method": "LINEAR_SOURCE_SERVING_MULTIPLIERS_NOT_NEW_GRAM_BASIS",
            "minimumSourceServings": 1 if sushi else 0.5, "maximumSourceServings": 12 if sushi else 4 if fruit else 3,
            "stepSourceServings": 1 if sushi else 0.5,
            "note": "Original nutrition and recipe remain per photographed source portion. Multipliers scale each quantity proportionally; cooked weight remains unknown. Sushi is counted in source pieces, not silently redefined as one large meal."}
        dishes.append({"code": code, "nameVi": seed["nameVi"], "imageUrl": source["imageUrl"],
            "sourceRecordId": source["sourceRecordId"], "sourceUrl": source["sourceUrl"],
            "nutritionBasis": "SOURCE_SERVING_INFERRED_WITH_INGREDIENT_ESTIMATES",
            "qualityJson": json.dumps(quality, ensure_ascii=False),
            "cookingMethod": "RAW" if fruit else seed["cookingMethod"] or ("BOILED" if role == "MAIN_STARCH" else "STEAMED"),
            "dishRole": role, "mealSlots": slots, "householdUnitLabel": "1 miếng sushi theo ảnh nguồn (có ước tính)" if sushi else "1 khẩu phần theo ảnh nguồn (có ước tính)",
            "minServingScaledX2": 2 if sushi else 1, "maxServingScaledX2": 24 if sushi else 8 if fruit else 6,
            "stepServingScaledX2": 2 if sushi else 1,
            "solverEligible": not missing, "allergenEvidenceComplete": fruit,
            "portionEvidenceComplete": row["imageEnergyMatchesSource"],
            "sourceRawPortionWeightG": items[0]["rawWeightG"] if fruit else None,
            "nutrition": nutrition, "recipeItems": items})
    ingredients = [{"code": x["code"], "nameVi": x["sourceName"], "group": x["group"]} for x in models.values()]
    count = lambda fields: sum(all(d["nutrition"].get(k) is not None for k in fields) for d in dishes)
    core = ("caloriesKcal", "proteinG", "fatG", "totalCarbG")
    eligible = [d for d in dishes if d["solverEligible"]]
    gi50 = [d for d in eligible if d["nutrition"]["estimatedGi"] <= 50]
    report = {"importedSourceDishCount": len(dishes), "publicVisibleSourceCount": len(dishes),
        "coreCompleteSourceCount": count(core), "coreIncompleteSourceCount": len(dishes) - count(core),
        "solverEligibleSourceCount": len(eligible), "solverPendingSourceCount": len(dishes) - len(eligible),
        "solverEligibleSourceCodes": [d["code"] for d in eligible], "qaBackfillRetained": False,
        "databaseAppliedInThisSession": False, "estimatedGiMeasuredDishCount": 0,
        "gi50CandidateCount": len(gi50), "gi50RoleCounts": dict(Counter(d["dishRole"] for d in gi50)),
        "allSourceRoleCounts": dict(Counter(d["dishRole"] for d in dishes)),
        "note": "Source totals retained; only missing core/fibre/cholesterol estimated from explicit ingredient observations. GI values are proxies/imputations or disclosed conservative bounds; N/A sentinels have separate GL bounds. All-source eligibility does not imply a feasible clinical menu. Purines/saturated fat remain unknown, disease/allergy exclusions remain enforced. No legacy QA dishes or fallback.",
        "dishes": [{"code": d["code"], "publicVisible": True,
                    "coreComplete": all(d["nutrition"].get(k) is not None for k in core),
                    "solverEligible": d["solverEligible"], "dishRole": d["dishRole"],
                    "estimatedGi": d["nutrition"]["estimatedGi"],
                    "giStatus": json.loads(d["qualityJson"])["perFieldEvidence"]["estimatedGi"].get("giStatus"),
                    "unknownFields": json.loads(d["qualityJson"])["unknownFields"],
                    "missingSolverFields": json.loads(d["qualityJson"])["solverMissingRequiredFields"]} for d in dishes]}
    return {"nin-source-import.json": {"schemaVersion": 1, "datasetId": "nin-real-20261123-source-serving-v2",
                                       "ingredients": ingredients, "dishes": dishes},
            "ingredient-model-links.json": {"schemaVersion": 1, "sourceFiles": {"ninFoodsSha256": digest(NIN_FOODS),
                "referenceSnapshotSha256": digest(folder / "ingredient-estimation-reference.json")},
                "ingredients": list(models.values())}, "integration-readiness.json": report}


def build(folder=FOLDER, check=False):
    outputs = build_outputs(folder)
    for name, value in outputs.items():
        serialized = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
        path = folder / name
        if check:
            if not path.is_file() or path.read_text(encoding="utf-8") != serialized:
                raise ValueError(f"Generated artifact stale: {path}")
        else:
            path.write_text(serialized, encoding="utf-8")
    if not check:
        from prepare_nin_catalog import refresh_manifest
        refresh_manifest(folder)
    report = outputs["integration-readiness.json"]
    print(json.dumps({k: report[k] for k in ("importedSourceDishCount", "solverEligibleSourceCount", "solverPendingSourceCount", "gi50RoleCounts")}))
    return outputs


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", type=Path, default=FOLDER)
    parser.add_argument("--check", action="store_true", help="Fail on stale output without rewriting files")
    args = parser.parse_args()
    build(args.folder, args.check)
