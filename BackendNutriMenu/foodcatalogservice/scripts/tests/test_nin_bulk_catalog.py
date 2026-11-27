import copy
from decimal import Decimal
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_nin_bulk_catalog as builder
from collect_nin_bulk import nutrient_audit


def ingredient(name, grams=10, unit="g", certainty="CLEAR"):
    return {"nameSourceText": name, "amountSourceText": str(grams), "numericQuantity": grams,
            "unitSourceText": unit, "preparationSourceText": None, "certainty": certainty}


class NinBulkCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = builder.load(builder.OUTPUT / "nin-source-import.json")
        cls.report = builder.load(builder.OUTPUT / "integration-readiness.json")
        pointer = builder.load(builder.STAGING / "latest-complete-source.json")
        cls.sources = {r["_id"]: r for r in builder.load(builder.STAGING / pointer["path"])}
        cls.identities = {r["code"]: r for r in cls.fixture["ingredients"]}

    def test_every_export_is_source_only_and_keeps_observed_numeric_values(self):
        self.assertEqual(1023, len(self.fixture["dishes"]))
        self.assertEqual("nin-bulk-20261126", self.fixture["datasetId"])
        for row in self.fixture["dishes"]:
            with self.subTest(row=row["code"]):
                self.assertTrue(row["code"].startswith("NIN_BULK_"))
                source = self.sources[row["sourceRecordId"]]
                self.assertEqual(source["name_vi"], row["nameVi"])
                observed = nutrient_audit(source)[0]
                for field, number in observed.items():
                    if number is not None:
                        self.assertEqual(number, row["nutrition"][field], field)
                quality = json.loads(row["qualityJson"])
                self.assertEqual(source["nutritional_components"], quality["sourceNutritionUnmodified"]["nutritional_components"])
                self.assertFalse(quality["clinicalCompletenessClaimed"])
                self.assertEqual("UNKNOWN_NOT_SUM_OF_RECIPE_GRAMS", quality["cookedWeightStatus"])
                self.assertIsNone(row["sourceRawPortionWeightG"])
                self.assertFalse(quality["sourceEnumerationComplete"])

    def test_general_readiness_has_real_recipe_portion_and_core_evidence(self):
        ready = [r for r in self.fixture["dishes"] if r["solverEligible"]]
        self.assertGreater(len(ready), 500)
        self.assertEqual(len(ready), self.report["generalEligibleCount"])
        for row in ready:
            quality = json.loads(row["qualityJson"])
            self.assertTrue(quality["generalEligible"])
            self.assertTrue(row["portionEvidenceComplete"])
            self.assertTrue(quality["servingBasis"]["energyMatches"])
            self.assertEqual([], quality["generalEligibilityBlockers"])
            self.assertTrue(row["recipeItems"])
            for field in ("caloriesKcal", "proteinG", "fatG", "totalCarbG", "netCarbG", "sodiumMg"):
                self.assertIsNotNone(row["nutrition"][field])
            self.assertTrue(all(i["rawWeightG"] > 0 for i in row["recipeItems"]))
        self.assertTrue(any(r["nutrition"]["fiberG"] is None for r in ready))
        self.assertTrue(any(r["nutrition"]["cholesterolMg"] is None for r in ready))
        self.assertTrue(all(r["nutrition"]["estimatedGi"] is None for r in self.fixture["dishes"]))
        self.assertTrue(all(r["nutrition"]["glycemicLoad"] is None for r in self.fixture["dishes"]))

    def test_positive_allergens_and_unknown_compounds_do_not_become_absence(self):
        evidence = builder.allergen_evidence([ingredient("Trứng gà"), ingredient("Đậu phụ"), ingredient("Tôm biển")], True, "source", "hash")
        for tag in ("ALLERGEN_EGG", "ALLERGEN_SOY", "ALLERGEN_SEAFOOD"):
            self.assertEqual("PRESENT", evidence[tag]["status"])
        for name in ("Nước dùng", "Nước sốt", "Thịt bò xào", "Nước canh rau", "Gia vị", "Bột", "Bơ thực vật"):
            evidence = builder.allergen_evidence([ingredient(name)], True, "source", "hash")
            for tag in ("ALLERGEN_EGG", "ALLERGEN_SOY"):
                self.assertEqual("UNKNOWN", evidence[tag]["status"], (name, tag))
        evidence = builder.allergen_evidence([ingredient("Dầu TV")], True, "source", "hash")
        self.assertEqual("UNKNOWN", evidence["ALLERGEN_SOY"]["status"])
        self.assertEqual("ABSENT_CONFIRMED", evidence["ALLERGEN_EGG"]["status"])
        evidence = builder.allergen_evidence([ingredient("Muối vừng")], True, "source", "hash")
        self.assertEqual("UNKNOWN", evidence["ALLERGEN_PEANUT"]["status"])

    def test_named_pure_ingredients_and_accent_boundaries_are_not_misclassified(self):
        evidence = builder.allergen_evidence([ingredient("Gạo nếp"), ingredient("Thịt lợn mông sấn"), ingredient("Hành khô"), ingredient("Muối")], True, "source", "hash")
        self.assertEqual("ABSENT_CONFIRMED", evidence["ALLERGEN_EGG"]["status"])
        self.assertEqual("ABSENT_CONFIRMED", evidence["ALLERGEN_SOY"]["status"])
        self.assertEqual("ROOT_FRUIT_VEG", builder.ingredient_group("Cà chua"))
        self.assertEqual("FISH_SEAFOOD", builder.ingredient_group("Cá hồi"))
        role, _ = builder.role_for({"name_vi": "Xôi trắng muối vừng"}, [ingredient("Gạo nếp"), ingredient("Muối vừng")])
        self.assertEqual("MAIN_STARCH", role)

    def test_global_allergen_flag_cannot_override_individual_unknowns(self):
        for row in self.fixture["dishes"]:
            evidence = json.loads(row["qualityJson"])["perAllergenEvidence"]
            self.assertEqual(set(builder.ALLERGENS), set(evidence))
            self.assertEqual(all(x["status"] != "UNKNOWN" for x in evidence.values()), row["allergenEvidenceComplete"])
            self.assertTrue(all(x["sourceImageSha256"] for x in evidence.values()))
            self.assertTrue(all(x["status"] in {"PRESENT", "ABSENT_CONFIRMED", "UNKNOWN"} for x in evidence.values()))

    def test_ingredient_rows_are_db_unique_and_preserve_total_source_grams(self):
        for row in self.fixture["dishes"]:
            ledger = row["recipeItems"]
            self.assertEqual(len(ledger), len({i["ingredientCode"] for i in ledger}), row["code"])
            original = json.loads(row["qualityJson"])["originalRecipe"]
            valid = [i for i in original if i.get("certainty") == "CLEAR" and i.get("unitSourceText") == "g" and i.get("numericQuantity") is not None and i["numericQuantity"] > 0]
            self.assertEqual(sum(Decimal(str(i["numericQuantity"])) for i in valid), sum(Decimal(str(i["rawWeightG"])) for i in ledger), row["code"])
            for item in ledger:
                self.assertIn(item["ingredientCode"], self.identities)
                self.assertLessEqual(len(item["sourceAmountText"]), 500)

    def test_real_unknown_units_are_retained_and_never_converted_to_grams(self):
        found = False
        for row in self.fixture["dishes"]:
            quality = json.loads(row["qualityJson"])
            unknown = quality["nonQuantifiedSourceAnnotations"]
            if any(i.get("unitSourceText") == "ml" for i in unknown):
                found = True
                self.assertFalse(row["solverEligible"])
                self.assertIn("INCOMPLETE_OR_NON_GRAM_RECIPE_ITEMS", quality["generalEligibilityBlockers"])
        self.assertTrue(found)

    def test_harmless_rounding_does_not_hide_real_truncation_or_mismatched_basis(self):
        benign = "Sum of ingredient Kcal values in table is 997.1 Kcal vs badge 997.0 Kcal (0.1 Kcal rounding difference)."
        self.assertEqual([], builder.blocking_uncertainties([benign]))
        clipped = "Bottom row is clipped and quantity unreadable; sum 99.0 Kcal vs badge 99.1 Kcal due to rounding."
        self.assertEqual([clipped], builder.blocking_uncertainties([clipped]))
        duplicate = "Table lists ingredient twice with equal amounts; both rows retained."
        self.assertEqual([duplicate], builder.blocking_uncertainties([duplicate]))
        self.assertEqual(Decimal("47"), builder.image_energy("47kcal/cái"))
        self.assertIsNone(builder.image_energy(None))

    def test_ocr_source_schema_and_hash_are_required(self):
        value = {"code": "test", "imageSha256": "hash", "ingredientListStatus": "PRESENT", "servingHeadingText": "Thành phần", "imageEnergyText": "10 kcal", "ingredients": [ingredient("Cà rốt")], "uncertainties": [], "reviewStatus": "PENDING_INDEPENDENT_REVIEW"}
        builder.validate_ocr(value, "test", "hash")
        changed = copy.deepcopy(value); changed["imageSha256"] = "different"
        with self.assertRaises(ValueError): builder.validate_ocr(changed, "test", "hash")
        changed = copy.deepcopy(value); changed["ingredients"][0]["numericQuantity"] = True
        with self.assertRaises(ValueError): builder.validate_ocr(changed, "test", "hash")
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(ValueError): builder.checked_file(Path(temporary), "../outside", "hash")

    def test_original_and_crop_hashes_and_fallbacks_are_accounted_for(self):
        self.assertEqual(1023, self.report["imageHashValidatedCount"])
        self.assertEqual(1023, self.report["cropHashValidatedCount"])
        self.assertEqual(1017, self.report["cropBoundsValidatedCount"])
        for row in self.fixture["dishes"]:
            photo = json.loads(row["qualityJson"])["dishPhoto"]
            if photo["status"] != "READY" or not photo["boundsValidated"]:
                self.assertEqual("ORIGINAL_SOURCE_FALLBACK", photo["selectedVariant"])

    def test_fixture_matches_actual_database_string_and_numeric_boundaries(self):
        audit = builder.audit_database_boundaries(self.fixture)
        self.assertLessEqual(audit["maximumExportedStringLengths"]["Dish.nutritionBasis"], 50)
        self.assertLessEqual(audit["maximumExportedStringLengths"]["Ingredient.vfcCode"], 30)
        invalid = {"ingredients": [], "dishes": [copy.deepcopy(self.fixture["dishes"][0])]}
        invalid["dishes"][0]["nutritionBasis"] = "x" * 51
        with self.assertRaisesRegex(ValueError, "Database string limit exceeded"):
            builder.audit_database_boundaries(invalid)
        invalid["dishes"][0]["nutritionBasis"] = builder.NUTRITION_BASIS
        invalid["dishes"][0]["nutrition"]["sodiumMg"] = 100000
        with self.assertRaisesRegex(ValueError, "Database numeric range exceeded"):
            builder.audit_database_boundaries(invalid)


if __name__ == "__main__":
    unittest.main()
