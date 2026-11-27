"""Build the 40-source pending seed and provenance from reviewed image recipes.

This never overwrites food-catalog-vi-qa.json. Joins use exact normalized names or
explicit identity links in ingredient-source-links.json; other matches remain unresolved. Values
from the existing QA/FDC seed are labeled reviewed proxies, never NIN values.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import unicodedata

REPO_ROOT = Path(__file__).resolve().parents[3]
ROOT = REPO_ROOT / "BackendNutriMenu"
CATALOG = ROOT / "foodcatalogservice/src/main/resources/catalog"
DATA = CATALOG / "nin-real-20261123"
SELECTED = ["VPF-" + x for x in "000007 000008 000009 000010 000011 000012 000013 000014 000015 000016 000018 000020 000021 000025 000027 000033 000037 000039 000049 000056 000057 000058 000071 000077 000084 000086 000088 000089 000092 000094 000100 000105 000117 000144 000146 000155 000174 000175 000398 000423".split()]
SOURCE_URL = "https://viendinhduong.vn/vi/cong-cu-va-tien-ich/gia-tri-dinh-duong-mon-an"
FOOD_URL = "https://viendinhduong.vn/vi/cong-cu-va-tien-ich/gia-tri-dinh-duong-thuc-pham"
FIELDS = {"totalCarbG": "carbohydrate-by-difference", "fiberG": "fibre-total-dietary", "proteinG": "protein", "fatG": "total-lipid-fat", "saturatedFatG": "fatty-acids-total-saturated", "cholesterolMg": "cholesterol", "sodiumMg": "na", "potassiumMg": "k"}
REQUIRED = ["ingredientGroup", "caloriesPer100g", "totalCarbG", "fiberG", "proteinG", "fatG", "giValue"]
DISH_FIELDS = {"caloriesKcal": (["nang-luong"], ["kcal"]), "proteinG": (["chat-dam"], ["g"]),
               "fatG": (["chat-beo", "lipid"], ["g"]), "totalCarbG": (["chat-bot-duong", "carbohydrate", "carbglucid"], ["g"]),
               "fiberG": (["xo", "chat-xo"], ["g"]), "sodiumMg": (["natri"], ["mg"]),
               "potassiumMg": (["kali", "potassium"], ["mg"]), "cholesterolMg": (["cholesterol"], ["mg"]),
               "saturatedFatG": (["saturated-fat", "chat-beo-bao-hoa"], ["g"]), "purineMg": (["purine"], ["mg"]),
               "estimatedGi": (["gi", "glycemic-index"], []), "netCarbG": (["net-carbohydrate"], ["g"]),
               "glycemicLoad": (["glycemic-load"], [])}


def load(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def refresh_manifest(folder):
    folder = folder.resolve()
    artifacts = [p for p in folder.rglob("*") if p.is_file() and p.name != "data-change-manifest.json"]
    scripts = [ROOT / "foodcatalogservice/scripts" / name for name in ("collect_nin_recipe_images.py", "prepare_nin_catalog.py", "audit_nin_catalog.py", "import_nin_catalog.py", "build_nin_source_import.py", "build_nin_frontend_assets.py", "nin_ingredient_model.py", "build_nin_reference_snapshot.py")]
    application_paths = [
        "foodcatalogservice/src/main/java/com/example/foodcatalogservice/" + name for name in (
            "config/CatalogDataSeederConfig.java", "config/SourceCatalogSeederConfig.java", "config/CatalogRetirementConfig.java",
            "dto/RecipeIngredientBreakdownDto.java", "dto/response/DishDetailResponse.java",
            "dto/request/CandidateFilterRequest.java", "dto/request/SolverCoverageDiagnosticRequest.java",
            "controller/InternalSolverCatalogController.java",
            "entity/Dish.java", "entity/DishIngredient.java", "entity/Ingredient.java", "entity/enums/TrafficLightBadge.java",
            "service/CatalogNutritionQuality.java", "service/CatalogSourcePolicy.java", "service/CatalogRetirementService.java",
            "service/FoodCatalogService.java", "service/NutritionCalculationEngine.java",
            "service/PreparationGuide.java", "service/SourceCatalogImportService.java")
    ] + [
        "mealoptimizerservice/src/main/java/com/example/mealoptimizerservice/" + name for name in (
            "dto/DishSolverDto.java", "solver/CpSatMealOptimizationEngine.java", "solver/SolverInputDataValidator.java",
            "service/NewSourceCatalogPolicy.java", "client/FoodCatalogIntegrationClient.java",
            "controller/InternalOptimizerController.java", "service/MealOptimizerService.java", "service/CheckedMealChangeService.java",
            "entity/enums/PlanningMode.java", "service/GeneralReferencePolicy.java", "entity/MealPlan.java",
            "dto/request/GenerateDailyMealRequest.java", "dto/request/CheckedMealChangeRequest.java",
            "dto/response/MealPlanResponse.java")
    ] + [
        "foodcatalogservice/src/test/java/com/example/foodcatalogservice/service/PreparationGuideTest.java",
        "foodcatalogservice/src/test/java/com/example/foodcatalogservice/service/SourceCatalogImportServiceTest.java",
        "foodcatalogservice/src/test/java/com/example/foodcatalogservice/service/SourceCatalogCandidateHttpTest.java",
        "foodcatalogservice/src/test/java/com/example/foodcatalogservice/service/CatalogSourcePolicyTest.java",
        "foodcatalogservice/src/main/java/com/example/foodcatalogservice/repository/DishRepository.java",
        "foodcatalogservice/src/main/java/com/example/foodcatalogservice/repository/IngredientRepository.java",
        "mealoptimizerservice/src/test/java/com/example/mealoptimizerservice/solver/CpSatMealOptimizationEngineTest.java",
        "mealoptimizerservice/src/test/java/com/example/mealoptimizerservice/solver/SolverInputDataValidatorTest.java",
        "mealoptimizerservice/src/test/java/com/example/mealoptimizerservice/solver/NinSourceDatasetTest.java",
        "foodcatalogservice/scripts/tests/test_nin_source_import.py",
        "mealoptimizerservice/src/test/java/com/example/mealoptimizerservice/service/NewSourceCatalogPolicyTest.java",
        "mealoptimizerservice/src/test/java/com/example/mealoptimizerservice/service/CheckedMealChangeServiceTest.java",
        "mealoptimizerservice/src/test/java/com/example/mealoptimizerservice/service/GeneralReferencePolicyTest.java"
    ] + [
        "frontend/src/types.ts",
        "frontend/src/utils.ts",
        "frontend/src/components/ui.tsx",
        "frontend/src/components/FoodDetail.tsx",
        "frontend/src/components/MealChangeModal.tsx",
        "frontend/src/pages/FoodLibrary.tsx",
        "frontend/src/pages/MealPlanner.tsx",
        "frontend/src/pages/Dashboard.tsx",
        "frontend/src/pages/Tools.tsx",
        "frontend/src/services/api.ts",
        "frontend/src/styles.css",
        "frontend/src/test/source-catalog.test.tsx",
        "frontend/src/test/planning-mode.test.tsx",
        "frontend/src/test/meal-change.test.tsx",
        "frontend/src/test/integration.test.tsx",
        "frontend/src/test/food-images.test.tsx",
        "frontend/src/data/ninImages.ts",
        "frontend/src/foodImages.ts",
        "gateway/gateway_app/integration.py",
        "gateway/tests/test_team_bridge.py",
        "gateway/tests/test_new_source_only.py",
        "gateway/tests/test_checked_change.py",
        "gateway/tests/test_exclusions.py",
        "gateway/tests/test_planning_modes.py",
        "chatbot/rag_app/meal_models.py",
        "README.md",
        "mealoptimizerservice/src/main/java/com/example/mealoptimizerservice/entity/MealPlanRevision.java",
        "mealoptimizerservice/src/main/java/com/example/mealoptimizerservice/repository/MealPlanRevisionRepository.java",
        "mealoptimizerservice/src/main/java/com/example/mealoptimizerservice/service/MealPlanArchiveService.java",
        "mealoptimizerservice/src/main/java/com/example/mealoptimizerservice/controller/UserMealPlanController.java",
        "mealoptimizerservice/schema/20261125_meal_plan_regeneration.sql",
        "mealoptimizerservice/src/test/java/com/example/mealoptimizerservice/controller/GenerationApiContractTest.java",
        "mealoptimizerservice/src/test/java/com/example/mealoptimizerservice/service/MealRegenerationServiceTest.java",
        "mealoptimizerservice/src/test/java/com/example/mealoptimizerservice/service/MealExclusionPipelineTest.java",
        "frontend/src/test/regeneration.test.tsx",
        "frontend/src/test/flows.test.tsx",
        "frontend/API_CONTRACT.md",
        "gateway/tests/test_regeneration.py",
        "gateway/README.md",
    ]
    application_paths += [str(path.relative_to(ROOT)).replace("\\", "/") for path in sorted(
        (REPO_ROOT / "frontend/public/food-images/nin-real-20261123").glob("*.webp"))]
    application_files = [ROOT / name for name in application_paths]
    backup = folder / "backups/food-catalog-vi-qa.before-nin.json"
    integration_path = folder / "integration-readiness.json"
    integration = load(integration_path) if integration_path.is_file() else {}
    write(folder / "data-change-manifest.json", {
        "scope": "NIN-only catalog runtime and consumer guards; legacy QA recipes excluded from startup and new choices, rows retained for history; source quality, nutrition constraints and unknown values preserved",
        "fileManifestScope": "Dataset artifacts, reproducibility scripts and explicitly reviewed backend/frontend/gateway application/test files; this is not a claim of ownership of the whole repository diff; curated README is preserved on rebuild",
        "reviewedApplicationPaths": application_paths,
        "activeDatabaseApplied": False,
        "qaSeedChanged": hashlib.sha256(backup.read_bytes()).hexdigest() != hashlib.sha256((CATALOG / "food-catalog-vi-qa.json").read_bytes()).hexdigest(),
        "originalSeedBackupSha256": hashlib.sha256(backup.read_bytes()).hexdigest(),
        "currentQaSeedSha256": hashlib.sha256((CATALOG / "food-catalog-vi-qa.json").read_bytes()).hexdigest(),
        "runtimeSourceDishCount": integration.get("importedSourceDishCount"),
        "runtimePublicVisibleCount": integration.get("publicVisibleSourceCount"),
        "runtimeCoreCompleteCount": integration.get("coreCompleteSourceCount"),
        "runtimeCoreIncompleteCount": integration.get("coreIncompleteSourceCount"),
        "runtimeSolverPendingCount": integration.get("solverPendingSourceCount"),
        "runtimeSolverEligibleCount": integration.get("solverEligibleSourceCount"),
        "qaBackfillRetained": False,
        "files": [{"path": str(p.relative_to(ROOT)).replace("\\", "/"), "bytes": p.stat().st_size, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted([*artifacts, *scripts, *application_files])]
    })


def norm(text):
    return " ".join("".join(c for c in unicodedata.normalize("NFD", text.lower().replace("đ", "d")) if unicodedata.category(c) != "Mn").split())


def classification(name):
    name = norm(name)
    method = next((value for words, value in [(('luoc',), 'BOILED'), (('hap',), 'STEAMED'), (('xao',), 'STIR_FRIED'), (('kho', 'rim'), 'BRAISED'), (('chien', 'ran'), 'PAN_FRIED'), (('nuong',), 'GRILLED'), (('canh', 'chao', 'pho'), 'SOUP_SIMMERED')] if any(word in name.split() for word in words)), None)
    if name.startswith("canh "):
        role = "SOUP"
    elif name.startswith(("bun ", "pho ", "chao ", "mien ", "mi ", "banh canh", "com rang", "com chien")):
        role = "COMPOSITE_DISH"
    elif name.startswith(("com ", "bun tuoi")):
        role = "MAIN_STARCH"
    elif name.startswith(("rau ", "nam ", "cai ", "bap cai", "su su")):
        role = "VEGGIE_DISH"
    else:
        role = "PROTEIN_DISH"
    return method, role


def build(folder):
    old_seed = load(CATALOG / "food-catalog-vi-qa.json")
    backup = folder / "backups/food-catalog-vi-qa.before-nin.json"
    backup.parent.mkdir(parents=True, exist_ok=True)
    if not backup.exists():
        shutil.copyfile(CATALOG / "food-catalog-vi-qa.json", backup)
    raw_path = REPO_ROOT / "ai-service/data/structured/nin_dishes_2026-10-06/dishes_full.json"
    raw = {x["code"]: x for x in load(raw_path)}
    food_path = REPO_ROOT / "ai-service/data/structured/nin_foods_2026-10-06/foods_full.json"
    foods, qa = defaultdict(list), defaultdict(list)
    for row in load(food_path):
        foods[norm(row["name_vi"])].append(row)
    for row in old_seed["ingredients"]:
        qa[norm(row["nameVi"])].append(row)
    food_by_code = {x["code"]: x for matches in foods.values() for x in matches}
    qa_by_code = {x["vfcCode"]: x for x in old_seed["ingredients"]}
    links_path = folder / "ingredient-source-links.json"
    links = {norm(x["sourceName"]): x for x in load(links_path)["links"]} if links_path.is_file() else {}
    for link in links.values():
        if link.get("ninFoodCode") and link["ninFoodCode"] not in food_by_code:
            raise ValueError("Unknown NIN source code in ingredient-source-links.json")
        if link.get("qaProxyCode") and link["qaProxyCode"] not in qa_by_code:
            raise ValueError("Unknown QA proxy code in ingredient-source-links.json")
    extracted_path = folder / "extracted-recipes.json"
    extracted = load(extracted_path) if extracted_path.is_file() else {}
    recipes = extracted if isinstance(extracted, list) else extracted.get("recipes", [])
    by_code = {x["code"]: x for x in recipes}
    omitted_by_code = {x["code"]: x for x in extracted.get("omitted_from_ingredient_extraction", [])} if isinstance(extracted, dict) else {}
    ingredients, ingredient_evidence, dishes, provenance, readiness = {}, {}, [], [], []
    for source_code in SELECTED:
        source = raw[source_code]
        recipe = by_code.get(source_code, omitted_by_code.get(source_code, {}))
        blockers = []
        items = []
        for source_item in recipe.get("ingredients", []):
            name = source_item["name_vi"]
            key = norm(name)
            code = "NIN_REAL_I_" + hashlib.sha256(key.encode()).hexdigest()[:12].upper()
            matches, proxy_matches = foods.get(key, []), qa.get(key, [])
            food = matches[0] if len(matches) == 1 else None
            proxy = proxy_matches[0] if len(proxy_matches) == 1 else None
            link = links.get(key, {})
            food = food_by_code[link["ninFoodCode"]] if link.get("ninFoodCode") else food
            proxy = qa_by_code[link["qaProxyCode"]] if link.get("qaProxyCode") else proxy
            if code not in ingredients:
                nutrients = {x["key"]: x for x in food.get("nutrition", [])} if food else {}
                row = {"vfcCode": code, "nameVi": name, "ingredientGroup": link.get("group") or (proxy.get("ingredientGroup") if proxy else None),
                       "ediblePortionPct": None, "caloriesPer100g": food.get("energy") if food else None,
                       **{field: nutrients.get(source_key, {}).get("value") for field, source_key in FIELDS.items()},
                       "giValue": None, "purineMg": None, "allergenTags": proxy.get("allergenTags", []) if proxy else []}
                field_evidence = {}
                if food:
                    field_evidence.update({field: {"source": "NIN", "sourceRecordId": food["_id"], "sourceCode": food["code"], "sourceUrl": FOOD_URL, "unit": "kcal/100g" if field == "caloriesPer100g" else "mg/100g" if field.endswith("Mg") else "g/100g"} for field in ["caloriesPer100g", *FIELDS] if row.get(field) is not None})
                if proxy:
                    for field in ["caloriesPer100g", *FIELDS, "giValue", "purineMg"]:
                        if row.get(field) is None and proxy.get(field) is not None:
                            row[field] = proxy[field]
                            field_evidence[field] = {"source": "existing QA/FDC proxy", "sourceCode": proxy["vfcCode"], "sourceRecordId": proxy["vfcCode"].removeprefix("FDC_"), "sourceUrl": "https://glycemicindex.com/gi-search/" if field == "giValue" else "https://fdc.nal.usda.gov/download-datasets/", "method": "explicit ingredient identity link or exact normalized name; copied existing QA estimate", "unit": "GI" if field == "giValue" else "kcal/100g" if field == "caloriesPer100g" else "mg/100g" if field.endswith("Mg") else "g/100g"}
                ingredients[code] = row
                ingredient_evidence[code] = {"sourceNameVi": name, "ninFoodMatch": food["_id"] if food else None,
                                             "qaExactNameProxy": proxy["vfcCode"] if proxy else None, "nutrientEvidence": field_evidence,
                                             "identityLink": link or None,
                                             "unknownFields": [key for key, value in row.items() if value is None],
                                             "allergensVerified": proxy is not None,
                                             "rawWeightBasisWarning": "Image ingredient grams may include bones/peels; edible fraction not inferred."}
            quantity = source_item.get("quantity_g")
            if quantity is None:
                blockers.append(f"Ingredient grams unresolved: {name}")
            items.append({"ingredientCode": code, "rawWeightG": quantity, "mainIngredient": len(items) == 0})
            unknown = [field for field in REQUIRED if ingredients[code].get(field) is None]
            if unknown:
                blockers.append(f"{name}: missing {'/'.join(unknown)}")
        if not items:
            blockers.append(recipe.get("reason") or "Source image ingredient extraction not available")
        method, role = classification(source["name_vi"])
        if method is None:
            blockers.append("Cooking method requires source/manual classification")
        blockers.append("Cooked serving mass not stated in source; no raw-sum or 100g substitution allowed")
        blockers.append("Per-dish complete nutrition/GI/purine evidence not yet approved for unchanged DB requirements")
        code = "NIN_REAL_" + source_code.replace("-", "_")
        serving = recipe.get("serving_basis", {})
        label = serving.get("source_text") or "Khẩu phần theo ảnh nguồn (chờ xác minh)"
        dishes.append({"code": code, "nameVi": source["name_vi"], "cookingMethod": method, "dishRole": role,
                       "mealSlots": ["BREAKFAST"] if role == "COMPOSITE_DISH" else ["LUNCH", "DINNER"],
                       "householdUnitLabel": label, "minServingScaledX2": 2, "maxServingScaledX2": 2, "stepServingScaledX2": 2,
                       "isActive": False, "createdByUserId": None, "popularityScore": 100, "recipeItems": items})
        image_url = "https://viendinhduong.vn" + source["image"]
        local_images = list((folder / "images").glob(source_code + ".*"))
        image = {"url": image_url, "localPath": str(local_images[0].relative_to(folder)).replace("\\", "/") if len(local_images) == 1 else None,
                 "sha256": hashlib.sha256(local_images[0].read_bytes()).hexdigest() if len(local_images) == 1 else None}
        if not image["localPath"]:
            blockers.append("Source image not downloaded uniquely")
        provenance.append({"code": code, "sourceCode": source_code, "sourceRecordId": source["_id"], "sourceUrl": SOURCE_URL,
                           "imageUrl": image_url, "image": image, "servingBasis": serving,
                           "sourceNutritionUnmodified": source.get("nutritional_components", []), "sourceEnergy": source.get("total_energy"),
                           "imageExtractedRecipe": recipe, "classificationMethod": "provisional name-based classification for review",
                           "nutritionWarning": "Only image energy/portion heading is visually confirmed where extracted. Other source nutrient basis remains as recorded; source values are not recast as per100g or cooked recipe totals."})
        numeric_fields = {}
        for field, (keys, units) in DISH_FIELDS.items():
            matches = [x for x in source.get("nutritional_components", []) if x.get("key") in keys and (not units or str(x.get("unit_name", "")).lower() in units) and isinstance(x.get("amount"), (int, float))]
            numeric_fields[field] = matches[0]["amount"] if len(matches) == 1 else None
        image_kcal = recipe.get("source_nutrition", {}).get("energy_kcal_on_image", recipe.get("energy_kcal_on_image"))
        missing_fields = [key for key, value in numeric_fields.items() if value is None]
        readiness.append({"code": code, "sourceCode": source_code, "ingredientCount": len(items), "sourceImageAvailable": bool(image["localPath"]),
                          "sourceNumericFieldsUnscaled": numeric_fields,
                          "sourceNumericFieldsMissing": missing_fields,
                          "imageEnergyMatchesSource": image_kcal is not None and image_kcal == numeric_fields["caloriesKcal"],
                          "otherSourceNutrientServingBasisVerified": False,
                          "cookedServingWeightVerified": False,
                          "activeImportReady": False, "blockers": blockers})
    seed = {"metadata": {"datasetId": "nin-real-20261123-pending-review", "schemaVersion": 1,
                         "sources": [{"key": "NIN", "description": "Original NIN recipe images and unmodified source records; pending validation, inactive", "url": SOURCE_URL}, *old_seed["metadata"]["sources"]]},
            "ingredients": list(ingredients.values()), "yieldRetentionFactors": [], "dishes": dishes, "userFoodPreferences": []}
    report = {"generatedAt": datetime.now(timezone.utc).isoformat(), "sourceDishCount": len(dishes), "downloadedImages": sum(x["sourceImageAvailable"] for x in readiness),
              "imageExtractedRecipes": sum(bool(x["ingredientCount"]) for x in readiness), "uniqueIngredients": len(ingredients), "activeImportReadyCount": 0,
              "sourcePoolImages": len(list((folder / "images").glob("*"))), "sourcePoolExtractedRecipes": len(recipes),
              "selectedRecipesWithUnknownGramAmounts": [dish["code"] for dish in dishes if any(item["rawWeightG"] is None for item in dish["recipeItems"])],
              "sourceImagesWithoutIngredientLists": [x["code"] for x in omitted_by_code.values()],
              "qaSeedUnchangedSha256": hashlib.sha256((CATALOG / "food-catalog-vi-qa.json").read_bytes()).hexdigest(),
              "missingSourceFieldDishCounts": dict(Counter(field for row in readiness for field in row["sourceNumericFieldsMissing"])),
              "warning": "Pending data only. Offline audit candidate only; runtime imports nin-source-import.json and never uses legacy QA dishes. No clinical completeness claimed.", "dishes": readiness}
    write(folder / "food-catalog-nin-pending.json", seed)
    write(folder / "nin-catalog-provenance.json", {"sourceRawSha256": hashlib.sha256(raw_path.read_bytes()).hexdigest(), "foodRawSha256": hashlib.sha256(food_path.read_bytes()).hexdigest(), "ingredients": ingredient_evidence, "dishes": provenance})
    write(folder / "nin-catalog-readiness.json", report)
    (folder / "selected-codes.txt").write_text("\n".join(SELECTED) + "\n", encoding="utf-8")
    refresh_manifest(folder)
    print(json.dumps({key: report[key] for key in ["sourceDishCount", "downloadedImages", "imageExtractedRecipes", "uniqueIngredients", "activeImportReadyCount"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", type=Path, default=DATA)
    build(parser.parse_args().folder)
