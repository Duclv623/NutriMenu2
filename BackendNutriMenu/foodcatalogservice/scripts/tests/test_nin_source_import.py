import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_nin_source_import as catalog


class NinSourceImportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.outputs = catalog.build_outputs()
        cls.dishes = cls.outputs["nin-source-import.json"]["dishes"]

    def test_all_forty_have_required_evidence_without_source_replacement(self):
        self.assertEqual(40, len(self.dishes))
        source = {r["code"]: r["sourceNumericFieldsUnscaled"] for r in catalog.load(catalog.FOLDER / "nin-catalog-readiness.json")["dishes"]}
        for dish in self.dishes:
            with self.subTest(dish=dish["code"]):
                self.assertTrue(dish["solverEligible"], json.loads(dish["qualityJson"])["solverMissingRequiredFields"])
                quality = json.loads(dish["qualityJson"])
                for key, value in source[dish["code"]].items():
                    if value is not None:
                        self.assertEqual(value, dish["nutrition"][key], f"source {key} overwritten")
                for field in catalog.REQUIRED:
                    self.assertIsNotNone(dish["nutrition"][field])
                    self.assertGreaterEqual(dish["nutrition"][field], 0)
                    self.assertIn(field, quality["perFieldEvidence"])
                self.assertIsNone(dish["nutrition"]["purineMg"])
                self.assertIsNone(dish["nutrition"]["saturatedFatG"])
                self.assertFalse(quality["clinicalCompletenessClaimed"])
                self.assertEqual("UNKNOWN_NOT_RAW_SUM", quality["cookedWeightStatus"])

    def test_unknown_is_not_zero_and_edible_weight_is_required_for_nonzero(self):
        items = [{"ingredientCode": "x", "rawWeightG": 100}]
        model = {"code": "x", "sourceName": "unknown", "edibleFraction": None,
                 "nutrients": {}, "perFieldEvidence": {}}
        value, evidence = catalog.recipe_sum(items, {"x": model}, "fatG")
        self.assertIsNone(value)
        self.assertEqual(["unknown"], evidence["missingIngredients"])
        model["nutrients"]["fatG"] = 0
        model["perFieldEvidence"]["fatG"] = {"method": "OBSERVED_EXPLICIT_ZERO"}
        self.assertEqual(0, catalog.recipe_sum(items, {"x": model}, "fatG")[0])
        model["nutrients"]["fatG"] = 2
        self.assertIsNone(catalog.recipe_sum(items, {"x": model}, "fatG")[0])

    def test_na_gi_is_not_zero_gl_for_trace_animal_carbohydrate(self):
        items = [{"ingredientCode": "shrimp", "rawWeightG": 230}]
        model = {"shrimp": {"giModelKey": "trace_animal"}}
        gi, gl, evidence = catalog.estimate_gi({"netCarbG": 2.1}, items, model)
        self.assertEqual(0, gi)
        self.assertEqual(2.1, gl)
        self.assertEqual("NOT_APPLICABLE", evidence["giStatus"])
        self.assertEqual("GL_UPPER_BOUND_TRACE_CARBOHYDRATE", evidence["glMethod"])
        gi, gl, evidence = catalog.estimate_gi({"netCarbG": 0}, items, model)
        self.assertEqual((0, 0), (gi, gl))
        self.assertIsNone(catalog.estimate_gi({"netCarbG": None}, items, model)[0])

    def test_roles_and_annotations_preserve_source_without_fake_grams(self):
        dishes = {d["code"]: d for d in self.dishes}
        for suffix in ("000071", "000077", "000089", "000155"):
            self.assertEqual("VEGGIE_DISH", dishes["NIN_REAL_VPF_" + suffix]["dishRole"])
        for suffix in ("000008", "000010", "000011", "000012", "000013", "000014", "000015", "000016"):
            self.assertEqual("COMPOSITE_DISH", dishes["NIN_REAL_VPF_" + suffix]["dishRole"])
        annotated = 0
        for dish in self.dishes:
            quality = json.loads(dish["qualityJson"])
            self.assertTrue(all(i["rawWeightG"] > 0 for i in dish["recipeItems"]))
            self.assertEqual(len(quality["originalRecipe"]), len(dish["recipeItems"]) + len(quality["nonQuantifiedSourceAnnotations"]))
            annotated += len(quality["nonQuantifiedSourceAnnotations"])
        self.assertEqual(5, annotated)

    def test_gi_filter_retains_medical_limit_and_all_source_is_not_full_menu_claim(self):
        report = self.outputs["integration-readiness.json"]
        self.assertEqual(1, report["allSourceRoleCounts"]["MAIN_STARCH"])
        self.assertEqual(0, report["gi50RoleCounts"].get("MAIN_STARCH", 0))
        rice = next(d for d in self.dishes if d["code"] == "NIN_REAL_VPF_000007")
        self.assertGreater(rice["nutrition"]["estimatedGi"], 50)

    def test_generated_outputs_are_reproducible(self):
        self.assertEqual(self.outputs, catalog.build_outputs())
        for name, expected in self.outputs.items():
            self.assertEqual(expected, catalog.load(catalog.FOLDER / name), name)


if __name__ == "__main__":
    unittest.main()
