"""Build a new, source-only demonstration catalog from completed OCR evidence.

No QA dish fallback. Observed source numbers win; optional unknowns remain null.
Mechanical OCR checks are disclosed, not independent clinical validation.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import json
from pathlib import Path
import re

from collect_nin_bulk import CORE, FIELDS, image_container, nutrient_audit, normalize, source_url

REPO_ROOT = Path(__file__).resolve().parents[3]
ROOT = REPO_ROOT / "BackendNutriMenu"
STAGING = ROOT / "foodcatalogservice/src/main/resources/catalog/nin-bulk-staging-20261125"
OLD = ROOT / "foodcatalogservice/src/main/resources/catalog/nin-real-20261123"
OUTPUT = ROOT / "foodcatalogservice/src/main/resources/catalog/nin-bulk-20261126"
PAGE = "https://viendinhduong.vn/vi/cong-cu-va-tien-ich/gia-tri-dinh-duong-mon-an"
ALLERGENS = ("ALLERGEN_EGG", "ALLERGEN_SOY", "ALLERGEN_SEAFOOD", "ALLERGEN_PEANUT", "ALLERGEN_DAIRY")
ENERGY_TOLERANCE = Decimal("0.11")
NUTRITION_BASIS = "SOURCE_SERVING_OCR_INFERRED"


def load(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def audit_database_boundaries(fixture):
    """Check real JPA field limits before exporting, including noneligible rows."""
    base = ROOT / "foodcatalogservice/src/main/java/com/example/foodcatalogservice/entity"
    specs, metadata = {}, {}
    for entity in ("Dish", "Ingredient", "DishIngredient"):
        path = base / (entity + ".java")
        text = path.read_text(encoding="utf-8")
        fields = {}
        for annotation, field in re.findall(r"@Column\(([^)]*)\)\s*private\s+\w+\s+(\w+)\s*[;=]", text, flags=re.S):
            length = re.search(r"\blength\s*=\s*(\d+)", annotation)
            precision = re.search(r"\bprecision\s*=\s*(\d+)", annotation)
            scale = re.search(r"\bscale\s*=\s*(\d+)", annotation)
            fields[field] = {"length": int(length[1]) if length else None,
                "precision": int(precision[1]) if precision else None, "scale": int(scale[1]) if scale else None}
        specs[entity] = fields; metadata[entity] = {"path": path.relative_to(ROOT).as_posix(), "sha256": sha(path.read_bytes())}
    maxima, rounded = {}, []

    def check(entity, record, identity):
        for field, value in record.items():
            spec = specs[entity].get(field)
            if value is None or not spec: continue
            key = entity + "." + field
            if isinstance(value, str) and spec["length"]:
                maxima[key] = max(maxima.get(key, 0), len(value))
                if len(value) > spec["length"]:
                    raise ValueError(f"Database string limit exceeded {identity}/{key}: {len(value)} > {spec['length']}")
            if isinstance(value, (float, int)) and not isinstance(value, bool) and spec["precision"]:
                decimal = Decimal(str(value))
                if not decimal.is_finite() or abs(decimal) >= Decimal(10) ** (spec["precision"] - spec["scale"]):
                    raise ValueError(f"Database numeric range exceeded {identity}/{key}")
                if max(0, -decimal.as_tuple().exponent) > spec["scale"]:
                    rounded.append({"code": identity, "field": key, "sourceValue": value, "databaseScale": spec["scale"], "rawSourcePreservedInQualityJson": True})
    for row in fixture["ingredients"]:
        check("Ingredient", {"vfcCode": row["code"], "nameVi": row["nameVi"], "nameNormalized": normalize(row["nameVi"]), "ingredientGroup": row["group"]}, row["code"])
    for row in fixture["dishes"]:
        check("Dish", {**row, **row["nutrition"], "nameNormalized": normalize(row["nameVi"]), "dishRole": row["dishRole"]}, row["code"])
        for item in row["recipeItems"]: check("DishIngredient", item, row["code"])
    return {"entitySchemas": metadata, "maximumExportedStringLengths": maxima, "sourceValuesExceedingDatabaseScale": rounded}


def checked_file(root, relative, expected_hash):
    path = (root / relative).resolve()
    if not path.is_relative_to(root.resolve()) or not path.is_file():
        raise ValueError(f"Missing or unsafe evidence path: {relative}")
    content = path.read_bytes()
    if sha(content) != expected_hash:
        raise ValueError(f"Evidence hash mismatch: {relative}")
    return path, content


def image_energy(text):
    if not isinstance(text, str):
        return None
    found = re.findall(r"(\d+(?:[.,]\d+)?)\s*k?cal", text, flags=re.I)
    return Decimal(found[0].replace(",", ".")) if len(found) == 1 else None


def blocking_uncertainties(notes):
    result = []
    for text in notes:
        n = normalize(text)
        # Do not discard source observations for a disclosed sub-kcal sum/round
        # difference. This never changes the image/API energy match requirement.
        if any(token in n for token in ("clipped", "truncated", "unreadable", "off-image", "cut off", "omitted", "no printed quantity", "blank weight", "no amount", "depicts", "twice", "both rows")):
            result.append(text); continue
        if "two spaces" in n or "double space" in n:
            continue
        numeric_kcal = [Decimal(x.replace(",", ".")) for x in re.findall(r"(\d+(?:[.,]\d+)?)\s*kcal", text, flags=re.I)]
        small = len(numeric_kcal) >= 2 and abs(numeric_kcal[0] - numeric_kcal[1]) <= Decimal("0.5")
        if small and any(word in n for word in ("sum", "rounding", "differs slightly", "table energy")):
            continue
        # A blank per-ingredient kcal cell does not conceal a source quantity;
        # only the image/API dish energy is used, never invented row energies.
        if ("energy" in n or "nang luong" in n) and any(word in n for word in ("blank", "empty", "de trong")) and not any(word in n for word in ("weight", "quantity", "trong luong", "dinh luong")):
            continue
        result.append(text)
    return result


def validate_ocr(value, code, image_hash):
    fields = {"code", "imageSha256", "ingredientListStatus", "servingHeadingText", "imageEnergyText", "ingredients", "uncertainties", "reviewStatus"}
    if not isinstance(value, dict) or set(value) != fields or value["code"] != code or value["imageSha256"] != image_hash:
        raise ValueError("OCR schema or source identity/hash mismatch")
    if value["ingredientListStatus"] not in {"PRESENT", "ABSENT", "ILLEGIBLE"} or not isinstance(value["ingredients"], list):
        raise ValueError("Invalid ingredient-list status")
    if value["reviewStatus"] != "PENDING_INDEPENDENT_REVIEW" or not isinstance(value["uncertainties"], list) or not all(isinstance(x, str) for x in value["uncertainties"]):
        raise ValueError("Invalid OCR review status/uncertainties")
    for field in ("servingHeadingText", "imageEnergyText"):
        if value[field] is not None and not isinstance(value[field], str):
            raise ValueError("Invalid OCR source heading/energy text")
    ingredient_fields = {"nameSourceText", "amountSourceText", "numericQuantity", "unitSourceText", "preparationSourceText", "certainty"}
    for item in value["ingredients"]:
        if not isinstance(item, dict) or set(item) != ingredient_fields or not isinstance(item["nameSourceText"], str) or not item["nameSourceText"].strip():
            raise ValueError("Invalid OCR ingredient schema")
        if item["certainty"] not in {"CLEAR", "UNCERTAIN", "ILLEGIBLE"}:
            raise ValueError("Invalid ingredient certainty")
        if item["numericQuantity"] is not None and (isinstance(item["numericQuantity"], bool) or not isinstance(item["numericQuantity"], (float, int)) or not Decimal(str(item["numericQuantity"])).is_finite()):
            raise ValueError("Invalid ingredient numeric amount")
        for field in ("amountSourceText", "unitSourceText", "preparationSourceText"):
            if item[field] is not None and not isinstance(item[field], str):
                raise ValueError("Invalid ingredient source text")


def old_recipe_to_ocr(recipe, image_hash):
    return {"code": recipe["code"], "imageSha256": image_hash,
        "ingredientListStatus": "PRESENT" if recipe.get("ingredients") else "ABSENT",
        "servingHeadingText": recipe.get("serving_basis", {}).get("source_text"),
        "imageEnergyText": str(recipe.get("source_nutrition", {}).get("energy_kcal_on_image")) + " kcal",
        "ingredients": [{"nameSourceText": r["name_vi"], "amountSourceText": r.get("source_text"),
            "numericQuantity": r.get("quantity_g"), "unitSourceText": "g" if r.get("quantity_g") is not None else None,
            "preparationSourceText": None, "certainty": "CLEAR" if r.get("visual_certainty") == "high" else "UNCERTAIN"}
            for r in recipe.get("ingredients", [])],
        "uncertainties": recipe.get("evidence", {}).get("uncertainties", []), "reviewStatus": "PENDING_INDEPENDENT_REVIEW"}


def ingredient_name(name):
    # English gloss is retained in originalRecipe; the Vietnamese identity alone
    # permits an exact, auditable join. Do not strip cooking words or infer a cut.
    return name.split("/", 1)[0].strip()


def ingredient_group(name):
    n = normalize(ingredient_name(name))
    if re.search(r"\b(trung|egg)\b", n): return "EGG"
    if any(x in n for x in ("dau phu", "dau nanh", "dau tuong", "soy", "tao pho")): return "SOY_TOFU"
    if any(x in n for x in ("sua", "pho mai", "phomai", "phomat", "cheese", "milk")): return "MILK_DAIRY"
    fish = re.search(r"\b(tom|cua|muc|oc|ngao|hen|so|hau|tep|trai|luon)\b", n) or (n.startswith("ca ") and not n.startswith(("ca chua", "ca rot", "ca tim", "ca phao", "ca bat", "ca na")))
    if fish: return "FISH_SEAFOOD"
    if re.search(r"\b(lon|heo|gio lua|lap xuong|xuc xich|pate|nem|cha lon)\b", n): return "PORK"
    if re.search(r"\b(ga|vit|chim|ngan)\b", n): return "POULTRY"
    if re.search(r"\b(bo|be|trau|de)\b", n) and n != "bo": return "BEEF"
    if any(x in n for x in ("gao", "com", "xoi")): return "RICE_GRAIN"
    if any(x in n for x in ("bun", "banh pho", "mien", "my", "mi ", "khoai", "san ", "ngo ", "bot")): return "NOODLE_TUBER"
    if any(x in n for x in ("lac", "vung", "hat dieu", "hat sen")): return "NUT_SEED"
    if n.startswith(("dau an", "dau tv", "dau thuc vat", "mo ", "dau lac", "dau me", "dau vung")): return "OIL_FAT"
    if n.startswith(("rau ", "cai ", "bap cai", "ngon ", "la ")): return "LEAFY_VEG"
    if n.startswith(("ca chua", "ca rot", "su hao", "su su", "bau", "bi ", "gia ", "nam", "dua chuot", "muop", "dau co ve", "cu ", "mang")): return "ROOT_FRUIT_VEG"
    if n.startswith(("muoi", "duong", "nuoc", "mam", "tieu", "hanh", "toi", "ot", "sa", "rieng", "gieng", "mi chinh", "hat nem", "bot nem", "gia vi")): return "CONDIMENT"
    if any(n.startswith(x) for x in ("cam", "tao", "buoi", "chuoi", "du du", "xoai", "oi ", "le ", "dao", "hong", "dau tay", "sau rieng", "dua hau", "dua ta", "dua chuot")): return "FRUIT"
    return "MIXED"


def role_for(source, ingredients):
    n = normalize(source["name_vi"]); category = normalize(source.get("category_name", ""))
    if "trai cay" in category: return "SNACK", "SOURCE_FRUIT_CATEGORY"
    if n.startswith(("canh ", "sup ")): return "SOUP", "DISH_NAME_SOUP"
    if n.startswith(("com ", "xoi ", "chao trang")):
        animal = any(ingredient_group(i["nameSourceText"]) in {"PORK", "BEEF", "POULTRY", "FISH_SEAFOOD", "EGG"} for i in ingredients)
        mixed = re.search(r"\b(suat|rang|chien|pate|thit|gio|cha|ca|tom|suon)\b", n) or "lap xuong" in n
        return ("COMPOSITE_DISH", "RICE_MIXED_MEAL_WITH_PROTEIN") if animal or mixed else ("MAIN_STARCH", "STARCH_DISH_IDENTITY")
    if n.startswith(("khoai lang luoc", "khoai lang mat nuong", "ngo luoc", "san luoc", "bun tuoi")):
        return "MAIN_STARCH", "PLAIN_STARCH_IDENTITY"
    if n.startswith(("bun ", "bún ", "pho ", "chao ", "mien ", "mi ", "my ", "hu tieu", "banh canh", "banh da ", "banh my", "banh mi", "hamburger", "burger", "pizza", "sushi", "sui cao", "hoanh thanh", "banh cuon", "banh bao", "banh gio", "banh chung", "banh tet", "banh xeo", "set ")):
        return "COMPOSITE_DISH", "COMPLETE_STARCH_BASED_DISH_NAME"
    if n.startswith(("rau ", "cai ", "bap cai", "su hao", "su su", "gia do", "dau cove", "dau co ve", "bi do xao", "nam xao", "muop", "ngon ", "ca tim", "ca chua", "ca rot", "dua cai", "mang ", "nom ", "cu su ")):
        return "VEGGIE_DISH", "VEGETABLE_DISH_NAME"
    if n.startswith(("thit ", "ca ", "tom ", "trung ", "dau phu", "ga ", "bo ", "suon ", "vit ", "oc ", "muc ", "nem ", "cha ", "long ", "chan ", "canh ga", "dui ", "gan ", "chim ", "hạt", "hau ", "cua ", "so ", "ngaо ", "ngao ", "pha lau", "doi ", "bi lon", "be ", "luon ", "de ", "tai ", "tim ", "trach ", "nhong ", "ech ", "tiet ", "xuc xich")):
        return "PROTEIN_DISH", "PROTEIN_DISH_NAME"
    if n.startswith(("che ", "kem", "sua", "caramen", "tao pho", "thach", "tra ", "nuoc ", "banh ", "khoai ", "hat ", "lac ", "bong", "quay", "pho mai", "nha dam", "chuoi", "hong", "dao", "le ", "xoai", "sau rieng", "tao ", "cu ", "qua ", "hoa ")):
        return "SNACK", "SNACK_OR_DESSERT_IDENTITY"
    return "SNACK", "UNRESOLVED_ROLE_BROWSING_ONLY"


def cooking(name, role):
    n = normalize(name)
    for token, method in (("luoc", "BOILED"), ("hap", "STEAMED"), ("xao", "STIR_FRIED"),
        ("kho", "BRAISED"), ("rim", "BRAISED"), ("chien", "PAN_FRIED"), ("ran", "PAN_FRIED"), ("nuong", "GRILLED")):
        if token in n.split(): return method
    return "SOUP_SIMMERED" if role == "SOUP" or n.startswith(("pho ", "bun ", "chao ")) else "RAW" if role == "SNACK" and not n.startswith(("banh", "che", "kem")) else "STEAMED"


def allergen_evidence(items, complete, code, image_hash):
    names = [normalize(ingredient_name(i["nameSourceText"])) for i in items]
    present = {
        "ALLERGEN_EGG": lambda n: bool(re.search(r"\b(trung|egg)\b", n)),
        "ALLERGEN_SOY": lambda n: any(x in n for x in ("dau phu", "dau nanh", "dau tuong", "soy", "tao pho", "xi dau")),
        "ALLERGEN_SEAFOOD": lambda n: ingredient_group(n) == "FISH_SEAFOOD" or bool(re.search(r"\b(mam)\b", n)),
        "ALLERGEN_PEANUT": lambda n: bool(re.search(r"\b(lac|dau phong|peanut)\b", n)),
        "ALLERGEN_DAIRY": lambda n: (any(x in n for x in ("sua", "pho mai", "phomai", "phomat", "cheese", "milk")) and not any(x in n for x in ("sua dau nanh", "sua dau tuong", "sua lac", "sua dua", "nuoc cot dua"))) or n == "bo",
    }
    # These names do not enumerate their own ingredients. No negative allergen
    # claim can be made from a dish/condiment label alone.
    compounds = ("sot", "nuoc cham", "nuoc dung", "nuoc tron", "nuoc thit", "nuoc canh", "canh ", "nuoc mam pha", "hat nem", "bot nem", "bot canh", "gia vi", "huong lieu", "mayonnaise", "pate", "xuc xich", "lap xuong", "gio lua", "cha ", "nem ", "banh mi", "banh my", "caramen", "kem ", "mien tron", "com rang", "bot chien", "chao ", "xoi khuc", "banh bao", "ruoc ")
    ambiguous = [items[index]["nameSourceText"] for index, n in enumerate(names) if any(x in n for x in compounds)
        or (ingredient_group(n) == "MIXED") or n in {"bot", "dau", "bo thuc vat", "sua bot"} or any(x in n for x in ("thap cam", "tam bot", "nuoc uop"))
        or re.search(r"\b(xào|kho|hầm|chiên|rán|rang|phi)\b", ingredient_name(items[index]["nameSourceText"]).lower())]
    result = {}
    for tag in ALLERGENS:
        matches = [items[index]["nameSourceText"] for index, n in enumerate(names) if present[tag](n)]
        extra = []
        if tag in {"ALLERGEN_SOY", "ALLERGEN_PEANUT"}:
            extra = [items[index]["nameSourceText"] for index, n in enumerate(names)
                if n in {"dau tv", "dau thuc vat", "dau an", "vegetable oil"}]
        if tag == "ALLERGEN_PEANUT":
            extra += [items[index]["nameSourceText"] for index, n in enumerate(names) if n == "muoi vung"]
        if tag in {"ALLERGEN_SOY", "ALLERGEN_EGG", "ALLERGEN_DAIRY"}:
            extra += [items[index]["nameSourceText"] for index, n in enumerate(names) if re.search(r"\b(mi|my)\b", n) and not n.startswith("mi chinh")]
        status = "PRESENT" if matches else "UNKNOWN" if not complete or ambiguous or extra else "ABSENT_CONFIRMED"
        result[tag] = {"status": status, "matchedIngredients": matches,
            "unresolvedIngredients": sorted(set(ambiguous + extra)),
            "reason": "EXPLICIT_NAMED_SOURCE_INGREDIENT" if matches else "COMPLETE_READABLE_SOURCE_LIST_NAMED_IDENTITIES; NOT_A_CROSS_CONTACT_OR_MANUFACTURER_CERTIFICATE" if status == "ABSENT_CONFIRMED" else "INCOMPLETE_LIST_OR_UNDECLARED_COMPOUND_COMPOSITION",
            "sourceRecordId": code, "sourceImageSha256": image_hash}
    return result


def build(staging=STAGING, output=OUTPUT, check=False):
    pointer = load(staging / "latest-complete-source.json")
    source_path, source_bytes = checked_file(staging, pointer["path"], pointer["sha256"])
    sources = json.loads(source_bytes)
    images = load(staging / "image-collection.json")["records"]
    photos = load(staging / "dish-photos/manifest.json")["records"]
    old_recipes = {r["code"]: r for r in load(OLD / "extracted-recipes.json")["recipes"]}
    approved_old = {d["sourceRecordId"]: d for d in load(OLD / "nin-source-import.json")["dishes"]}
    ingredient_refs = {normalize(r["sourceName"]): r for r in load(OLD / "ingredient-model-links.json")["ingredients"]}
    identities, dishes, audit_rows, input_files = {}, [], [], [source_path, staging / "latest-complete-source.json",
        staging / "image-collection.json", staging / "dish-photos/manifest.json",
        OLD / "extracted-recipes.json", OLD / "nin-source-import.json", OLD / "ingredient-model-links.json"]
    for source in sorted(sources, key=lambda s: s["code"]):
        code, identity = source["code"], source["_id"]
        record = images[code]; official_url = source_url(source["image"])
        if record["imageUrl"] != official_url:
            raise ValueError(f"Image URL identity mismatch {code}")
        original_path, original_bytes = checked_file(staging, record["localPath"], record["sha256"])
        image_container(original_bytes)
        photo = photos[code]
        if photo["sourceRecordId"] != identity or photo["originalImageSha256"] != record["sha256"]:
            raise ValueError(f"Crop source identity mismatch {code}")
        crop_path, crop_bytes = checked_file(staging, photo["croppedImagePath"], photo["croppedImageSha256"])
        image_container(crop_bytes)
        bounds = photo["cropBoxPixels"]
        bounds_ok = len(bounds) == 4 and 0 <= bounds[0] < bounds[2] <= photo["originalWidth"] and 0 <= bounds[1] < bounds[3] <= photo["originalHeight"]
        input_files.extend([original_path, crop_path])
        response_path = staging / "ocr-responses" / (code + ".json")
        old = old_recipes.get(code)
        if response_path.is_file():
            response = load(response_path); validate_ocr(response, code, record["sha256"])
            recipe_evidence = "GEMINI_OCR_HASH_SCHEMA_VALIDATED_PENDING_INDEPENDENT_REVIEW"
            input_files.append(response_path)
        elif old and old["source_id"] == identity and old["image"]["sha256"] == record["sha256"]:
            response = old_recipe_to_ocr(old, record["sha256"])
            recipe_evidence = "PRIOR_VISUALLY_REVIEWED_SOURCE_RECIPE_EXACT_IMAGE_HASH"
        else:
            response = {"ingredients": [], "ingredientListStatus": "ABSENT", "servingHeadingText": None, "imageEnergyText": None, "uncertainties": []}
            recipe_evidence = "NO_SOURCE_INGREDIENT_LIST"
        items = response["ingredients"]
        nutrition, observations, issues = nutrient_audit(source)
        nutrition.update({"netCarbG": nutrition["totalCarbG"], "glycemicLoad": None})
        field_evidence = {k: {"method": "NIN_SOURCE_UNMODIFIED", **v} for k, v in observations.items() if nutrition.get(k) is not None}
        if nutrition["netCarbG"] is not None:
            field_evidence["netCarbG"] = {"method": "FULL_SOURCE_GLUCID_CONSERVATIVE_NO_FIBRE_SUBTRACTION"}
        if nutrition["estimatedGi"] is not None and nutrition["netCarbG"] is not None:
            nutrition["glycemicLoad"] = float((Decimal(str(nutrition["estimatedGi"])) * Decimal(str(nutrition["netCarbG"])) / 100).quantize(Decimal(".01"), rounding=ROUND_HALF_UP))
            field_evidence["glycemicLoad"] = {"method": "SOURCE_GI_X_SOURCE_CARB_DIV100"}
        recipe_items, unresolved_items, aggregated = [], [], {}
        for index, item in enumerate(items):
            name = ingredient_name(item["nameSourceText"])
            key = normalize(item["nameSourceText"]) + "|prep=" + normalize(item.get("preparationSourceText") or "")
            ingredient_code = "NIN_BULK_I_" + sha(key.encode())[:16].upper()
            identities.setdefault(ingredient_code, {"code": ingredient_code, "nameVi": name, "group": ingredient_group(name)})
            valid = item.get("certainty") == "CLEAR" and item.get("unitSourceText") == "g" and item.get("numericQuantity") is not None and item["numericQuantity"] > 0
            if not valid:
                unresolved_items.append(item)
                continue
            source_text = " | ".join(str(item.get(x) or "") for x in ("nameSourceText", "amountSourceText", "unitSourceText", "preparationSourceText"))
            if ingredient_code in aggregated:
                aggregate = aggregated[ingredient_code]
                aggregate["rawWeightG"] = float(Decimal(str(aggregate["rawWeightG"])) + Decimal(str(item["numericQuantity"])))
                aggregate["sourceAmountText"] += " ; " + source_text
            else:
                aggregate = {"ingredientCode": ingredient_code, "rawWeightG": item["numericQuantity"],
                    "mainIngredient": False, "sourceAmountText": source_text}
                aggregated[ingredient_code] = aggregate
                recipe_items.append(aggregate)
        role, role_method = role_for(source, items)
        if recipe_items:
            target_group = {"MAIN_STARCH": {"RICE_GRAIN", "NOODLE_TUBER"}, "PROTEIN_DISH": {"PORK", "POULTRY", "BEEF", "FISH_SEAFOOD", "SOY_TOFU", "EGG"},
                "VEGGIE_DISH": {"LEAFY_VEG", "ROOT_FRUIT_VEG"}, "SNACK": {"FRUIT"}}.get(role, set())
            candidates = [r for r in recipe_items if identities[r["ingredientCode"]]["group"] in target_group] or recipe_items
            max(candidates, key=lambda r: r["rawWeightG"])["mainIngredient"] = True
        recipe_complete = bool(items) and response["ingredientListStatus"] == "PRESENT" and not unresolved_items
        prior_approved = identity in approved_old and approved_old[identity]["imageUrl"] == official_url
        # Known source annotation exceptions stay visible; no fictitious grams.
        if prior_approved and unresolved_items and all(ingredient_name(x["nameSourceText"]) in {"Nước canh", "Gia vị*", "Muối trong dưa cải bẹ*"} for x in unresolved_items):
            recipe_complete = True
        energy = image_energy(response.get("imageEnergyText"))
        energy_match = energy is not None and nutrition["caloriesKcal"] is not None and abs(energy - Decimal(str(nutrition["caloriesKcal"]))) <= ENERGY_TOLERANCE
        heading = response.get("servingHeadingText")
        portion_complete = bool(recipe_complete and heading and energy_match)
        blockers = []
        if not recipe_complete: blockers.append("INCOMPLETE_OR_NON_GRAM_RECIPE_ITEMS")
        if not portion_complete: blockers.append("SOURCE_PORTION_HEADING_OR_ENERGY_MATCH_UNVERIFIED")
        if role_method == "UNRESOLVED_ROLE_BROWSING_ONLY": blockers.append("ROLE_UNRESOLVED")
        for field in CORE:
            if nutrition[field] is None: blockers.append("MISSING_SOURCE_" + field)
        if nutrition["caloriesKcal"] == 0: blockers.append("ZERO_SOURCE_ENERGY")
        if issues: blockers.append("SOURCE_NUTRIENT_QUALITY_FLAGS")
        unresolved_notes = blocking_uncertainties(response.get("uncertainties", []))
        if unresolved_notes and not prior_approved: blockers.append("OCR_UNCERTAINTY_PENDING_REVIEW")
        # Only exact named identities and relevant unprepared forms may fill a
        # missing field from the approved ingredient snapshot. Never copy a dish.
        if recipe_complete:
            for field in ("fiberG", "cholesterolMg", "fatG", "totalCarbG"):
                if nutrition[field] is not None: continue
                total = Decimal(0); components = []; complete = True
                for item in items:
                    ref = ingredient_refs.get(normalize(ingredient_name(item["nameSourceText"])))
                    if item.get("preparationSourceText") and normalize(item["preparationSourceText"]) not in {"raw", "tuoi", "song"}:
                        ref = None  # Do not apply a raw-form reference to a cooked/processed item.
                    value = ref.get("nutrients", {}).get(field) if ref else None
                    fraction = ref.get("edibleFraction") if ref else None
                    if not ref or value is None or (value != 0 and fraction is None) or item.get("unitSourceText") != "g" or item.get("numericQuantity") is None:
                        complete = False; break
                    total += Decimal(str(value)) * Decimal(str(item["numericQuantity"])) * Decimal(str(fraction if fraction is not None else 1)) / 100
                    components.append({"sourceName": item["nameSourceText"], "referenceCode": ref["code"], "per100g": value, "grams": item["numericQuantity"], "edibleFraction": fraction, "evidence": ref["perFieldEvidence"][field]})
                if complete and components:
                    nutrition[field] = float(total.quantize(Decimal(".01"), rounding=ROUND_HALF_UP))
                    field_evidence[field] = {"method": "EXACT_INGREDIENT_IDENTITY_RECIPE_ESTIMATE", "reference": "nin-real-20261123/ingredient-model-links.json", "retention": "assumed1_not_measured", "components": components}
            nutrition["netCarbG"] = nutrition["totalCarbG"]
            if nutrition["netCarbG"] is not None and "netCarbG" not in field_evidence:
                field_evidence["netCarbG"] = {"method": "FULL_ESTIMATED_CARBOHYDRATE_NO_FIBRE_SUBTRACTION", "carbohydrateEvidenceField": "totalCarbG"}
        general_eligible = not blockers
        allergens = allergen_evidence(items, recipe_complete, identity, record["sha256"])
        unit_status = "SOURCE_IMAGE_GRAMS_RAW_OR_COOKED_AS_NAMED_NOT_UNIFORMLY_RAW"
        quality = {"datasetId": "nin-bulk-20261126", "sourceCode": code, "generalEligible": general_eligible,
            "clinicalCompletenessClaimed": False, "medicalEligible": False,
            "imageSha256": record["sha256"], "originalImageLocalPath": record["localPath"],
            "dishPhoto": {**photo, "boundsValidated": bounds_ok, "selectedVariant": "READY_CROP" if photo["status"] == "READY" and bounds_ok else "ORIGINAL_SOURCE_FALLBACK", "allOriginalSourceBytesRetained": True},
            "ocrEvidence": recipe_evidence, "ocrResponseSha256": sha(response_path.read_bytes()) if response_path.is_file() else None,
            "ocrUncertainties": response.get("uncertainties", []), "blockingOcrUncertainties": unresolved_notes,
            "ocrReviewStatus": "PENDING_INDEPENDENT_REVIEW" if not prior_approved else "PRIOR_SOURCE_VISUAL_REVIEW",
            "originalRecipe": items, "nonQuantifiedSourceAnnotations": [{**i, "name_vi": i["nameSourceText"], "source_text": " | ".join(str(i.get(k) or "") for k in ("nameSourceText", "amountSourceText", "unitSourceText"))} for i in unresolved_items],
            "ingredientQuantityBasis": unit_status, "cookedWeightStatus": "UNKNOWN_NOT_SUM_OF_RECIPE_GRAMS",
            "cookingMethodStatus": "INFERRED_FROM_DISH_NAME_NOT_SOURCE_COOKING_INSTRUCTIONS",
            "mealSlotEvidence": "GENERAL_REFERENCE_ROLE_COMPATIBILITY_NOT_SOURCE_MEAL_TIME",
            "recipeAggregationMethod": "ADD_ONLY_GRAM_ROWS_WITH_IDENTICAL_FULL_SOURCE_NAME_AND_PREPARATION; ORIGINAL_ROWS_RETAINED_VERBATIM",
            "servingBasis": {"sourceHeading": heading, "sourceImageEnergyText": response.get("imageEnergyText"),
                "apiEnergyKcal": nutrition["caloriesKcal"], "energyMatchToleranceKcal": float(ENERGY_TOLERANCE), "energyMatches": energy_match,
                "method": "SOURCE_SERVING_INFERRED_FROM_IMAGE_RECIPE_HEADING_AND_MATCHING_ENERGY", "quantityG": None,
                "limitation": "Generic ingredients heading does not independently prove every nutrient serving basis. Demonstration inference only; no 100g assumption or cooked mass inferred."},
            "sourceNutritionUnmodified": {"total_energy": source.get("total_energy"), "nutritional_components": source["nutritional_components"]},
            "carbohydrateDefinition": "UNSPECIFIED_SOURCE_GLUCID_FULL_AMOUNT_NO_FIBRE_SUBTRACTION",
            "perFieldEvidence": field_evidence, "unknownFields": [k for k, v in nutrition.items() if v is None],
            "sourceNumericIssues": issues, "generalEligibilityBlockers": blockers,
            "perAllergenEvidence": allergens, "allergenEvidenceScope": "NAMED_SOURCE_RECIPE_INGREDIENTS_ONLY_NOT_CROSS_CONTACT_CERTIFICATION",
            "roleEvidence": role_method, "sourceEnumerationComplete": pointer.get("sourceEnumerationComplete", False),
            "sourceCompletenessWarning": pointer.get("sourceCompletenessWarning")}
        sushi = normalize(source["name_vi"]).startswith("sushi")
        slots = ["MORNING_SNACK", "AFTERNOON_SNACK"] if role == "SNACK" else ["BREAKFAST", "LUNCH", "DINNER"]
        row = {"code": "NIN_BULK_" + code.replace("-", "_"), "nameVi": source["name_vi"], "imageUrl": official_url,
            "sourceRecordId": identity, "sourceUrl": PAGE, "nutritionBasis": NUTRITION_BASIS,
            "qualityJson": json.dumps(quality, ensure_ascii=False), "cookingMethod": cooking(source["name_vi"], role),
            "dishRole": role, "mealSlots": slots, "householdUnitLabel": "1 miếng theo ảnh nguồn" if sushi else "1 khẩu phần theo ảnh nguồn (cơ sở có suy luận)",
            "solverEligible": general_eligible, "allergenEvidenceComplete": all(v["status"] != "UNKNOWN" for v in allergens.values()),
            "portionEvidenceComplete": portion_complete, "minServingScaledX2": 2 if sushi else 1,
            "maxServingScaledX2": 24 if sushi else 8 if role == "SNACK" else 6, "stepServingScaledX2": 2 if sushi else 1,
            "sourceRawPortionWeightG": None, "nutrition": nutrition, "recipeItems": recipe_items}
        dishes.append(row)
        audit_rows.append({"code": row["code"], "sourceCode": code, "nameVi": row["nameVi"], "role": role,
            "generalEligible": general_eligible, "blockers": blockers, "unknownFields": quality["unknownFields"],
            "perAllergenStatus": {k: v["status"] for k, v in allergens.items()}, "ocrEvidence": recipe_evidence,
            "cropStatus": photo["status"], "cropBoundsValid": bounds_ok, "sourceImageSha256": record["sha256"]})
    general = [d for d in audit_rows if d["generalEligible"]]
    egg_soy = [d for d in general if all(d["perAllergenStatus"][k] == "ABSENT_CONFIRMED" for k in ("ALLERGEN_EGG", "ALLERGEN_SOY"))]
    report = {"schemaVersion": 1, "datasetId": "nin-bulk-20261126", "sourceCount": len(sources), "exportedBrowsableCount": len(dishes),
        "generalEligibleCount": len(general), "generalPendingCount": len(dishes) - len(general), "clinicalCompletenessClaimed": False,
        "medicalEligibleCount": 0, "generalRoleCounts": dict(Counter(d["role"] for d in general)),
        "eggSoyAbsenceConfirmedGeneralCount": len(egg_soy), "eggSoyAbsenceConfirmedRoleCounts": dict(Counter(d["role"] for d in egg_soy)),
        "blockerCounts": dict(Counter(b for d in audit_rows for b in d["blockers"])),
        "unknownFieldCounts": dict(Counter(f for d in audit_rows for f in d["unknownFields"])),
        "imageHashValidatedCount": len(dishes), "cropHashValidatedCount": len(dishes),
        "cropBoundsValidatedCount": sum(d["cropBoundsValid"] for d in audit_rows),
        "sourceEnumerationComplete": pointer.get("sourceEnumerationComplete", False), "apiReportedTotal": pointer.get("apiReportedTotal"),
        "rawSourceRows": pointer.get("rawRowCount"), "qaFallbackUsed": False, "runtimeDatabaseApplied": False,
        "original40Modified": False, "dishes": audit_rows}
    fixture = {"schemaVersion": 1, "datasetId": "nin-bulk-20261126", "ingredients": sorted(identities.values(), key=lambda i: i["code"]), "dishes": dishes}
    database_audit = audit_database_boundaries(fixture)
    artifacts = {"nin-source-import.json": fixture, "integration-readiness.json": report,
        "database-boundary-audit.json": database_audit,
        "input-provenance.json": {"sourceSnapshotSha256": sha(source_bytes), "sourcePointer": pointer,
            "inputFiles": [{"path": p.relative_to(ROOT).as_posix(), "sha256": sha(p.read_bytes())} for p in sorted(set(input_files))],
            "builderSha256": sha(Path(__file__).read_bytes()), "approvedIngredientReferenceSha256": sha((OLD / "ingredient-model-links.json").read_bytes())}}
    for name, value in artifacts.items():
        if check:
            if load(output / name) != value: raise ValueError(f"Stale generated output {name}")
        else: write(output / name, value)
    print(json.dumps({k: v for k, v in report.items() if k not in {"dishes", "blockerCounts", "unknownFieldCounts"}}, ensure_ascii=True))
    return artifacts


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staging", type=Path, default=STAGING)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    build(args.staging, args.output, args.check)
