import base64
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import collect_nin_bulk as bulk

PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jv1sAAAAASUVORK5CYII=")


def row(code, identity=None):
    return {"code": code, "_id": identity or "id-" + code, "name_vi": "Món kiểm thử",
            "image": "/storage/" + code + ".png", "nutritional_components": [], "total_energy": None}


class FakeClient:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def get(self, url, accept, maximum):
        self.calls.append(url)
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response


def page(number, rows, total=2):
    return json.dumps({"current_page": number, "total": total, "per_page": 1, "data": rows}).encode(), "application/json"


class NinBulkCollectionTest(unittest.TestCase):
    def test_alias_units_duplicates_unknowns_and_explicit_zero(self):
        source = row("VPF-1")
        source["nutritional_components"] = [
            {"key": "lipid", "unit_name": "g", "amount": 0},
            {"key": "carbglucid", "unit_name": "g", "amount": "2.3"},
            {"key": "xo", "unit_name": "mg", "amount": 3},
            {"key": "natri", "unit_name": "mg", "amount": ""},
        ]
        values, observations, issues = bulk.nutrient_audit(source)
        self.assertEqual(0, values["fatG"])
        self.assertEqual(2.3, values["totalCarbG"])
        self.assertIsNone(values["fiberG"])
        self.assertIsNone(values["sodiumMg"])
        self.assertEqual("mg", observations["fiberG"]["rawUnit"])
        self.assertTrue(any(x["reason"] == "UNEXPECTED_UNIT" for x in issues))
        source["nutritional_components"].append({"key": "chat-beo", "unit_name": "g", "amount": 2})
        self.assertIsNone(bulk.nutrient_audit(source)[0]["fatG"])
        for value in (None, "", True, -1, "NaN", "Infinity", "not numeric"):
            self.assertIsNone(bulk.numeric(value))

    def test_identity_and_origin_validation(self):
        with self.assertRaises(ValueError): bulk.validate_rows([row("../unsafe")])
        with self.assertRaises(ValueError): bulk.validate_rows([row("A"), row("A")])
        with self.assertRaises(ValueError): bulk.validate_rows([row("A", "same"), row("B", "same")])
        for url in ("", "http://viendinhduong.vn/image.png", "https://example.org/x", "https://user:secret@viendinhduong.vn/x", "https://viendinhduong.vn:8443/x"):
            with self.assertRaises(ValueError): bulk.source_url(url)

    def test_images_resume_skip_verified_bytes_and_keep_prior_records(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(bulk, "previous_evidence", return_value={}):
            output = Path(temporary)
            client = FakeClient([(PNG, "image/png"), (PNG, "image/png")])
            bulk.collect_images([row("A"), row("B")], output, client, limit=1)
            first = (output / "images/A.png").read_bytes()
            summary = bulk.collect_images([row("A"), row("B")], output, client)
            self.assertEqual(2, len(client.calls))
            self.assertEqual(1, summary["networkRequests"])
            self.assertEqual(first, (output / "images/A.png").read_bytes())
            self.assertEqual({"A", "B"}, set(bulk.image_records(output)))
            bulk.collect_images([row("A")], output, client, offline=True)
            self.assertEqual({"A", "B"}, set(bulk.image_records(output)))

    def test_rate_limit_stops_whole_image_batch_without_retry(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(bulk, "previous_evidence", return_value={}):
            client = FakeClient([HTTPError(bulk.ORIGIN, 429, "Too many requests", None, None)])
            result = bulk.collect_images([row("A"), row("B")], Path(temporary), client)
            self.assertEqual("HTTP_429_STOP_NO_BYPASS", result["stopReason"])
            self.assertEqual(1, len(client.calls))
            self.assertEqual({"A"}, set(bulk.image_records(Path(temporary))))

    def test_bad_response_and_tampered_checkpoint_do_not_become_images(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(bulk, "previous_evidence", return_value={}):
            output = Path(temporary)
            client = FakeClient([(b"<html>Error</html>", "image/png")])
            bulk.collect_images([row("A")], output, client)
            self.assertFalse((output / "images/A.png").exists())
            self.assertEqual("FAILED", bulk.image_records(output)["A"]["status"])
            for value in (b"<html>not image</html>", PNG[:25], b"RIFF" + (24).to_bytes(4, "little") + b"WEBP" + b"bad data" * 3):
                with self.assertRaises(ValueError): bulk.image_container(value)
            bulk.collect_images([row("A")], output, FakeClient([(PNG, "image/png")]))
            (output / "images/A.png").write_bytes(b"tampered")
            retry = FakeClient([])
            bulk.collect_images([row("A")], output, retry)
            self.assertEqual(0, len(retry.calls))
            self.assertEqual("FAILED", bulk.image_records(output)["A"]["status"])

    def test_api_resume_keeps_raw_pages_and_publishes_only_complete_snapshot(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            first = FakeClient([page(1, [row("A")]), HTTPError(bulk.API, 429, "limited", None, None)])
            state = bulk.refresh(output, first, page_size=1, run_id="resume-test")
            self.assertFalse(state["complete"])
            self.assertEqual(1, len(state["pages"]))
            self.assertFalse((output / "latest-complete-source.json").exists())
            raw_page = (output / "api-runs/resume-test/page_0001.json").read_bytes()
            resumed = FakeClient([page(2, [row("B")])])
            state = bulk.refresh(output, resumed, page_size=1, run_id="resume-test")
            self.assertTrue(state["complete"])
            self.assertEqual(1, len(resumed.calls))
            self.assertIn("page=2", resumed.calls[0])
            self.assertEqual(raw_page, (output / "api-runs/resume-test/page_0001.json").read_bytes())
            pointer = bulk.load(output / "latest-complete-source.json")
            self.assertEqual(2, pointer["records"])
            self.assertEqual(["A", "B"], [r["code"] for r in bulk.load(output / pointer["path"])])

    def test_api_exact_duplicate_keeps_raw_occurrences_and_unique_count_caveat(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            state = bulk.refresh(output, FakeClient([page(1, [row("A")]), page(2, [row("A")])]), page_size=1, run_id="duplicate-test")
            self.assertTrue(state["complete"])
            self.assertTrue(state["paginationComplete"])
            self.assertFalse(state["sourceEnumerationComplete"])
            self.assertEqual(2, state["apiReportedTotal"])
            self.assertEqual(2, state["rawRowCount"])
            self.assertEqual(1, state["acceptedUniqueRowCount"])
            self.assertEqual(1, state["extraIdenticalRowCount"])
            self.assertIn("Pagination drift", state["sourceCompletenessWarning"])
            folder = output / "api-runs/duplicate-test"
            self.assertEqual(2, len(bulk.load(folder / "dishes-source-raw.json")))
            self.assertEqual(1, len(bulk.load(folder / "dishes-source.json")))
            reconciliation = bulk.load(folder / "source-reconciliation.json")
            self.assertEqual([1, 2], [p["apiPage"] for p in reconciliation["exactDuplicates"][0]["sourceOccurrences"]])
            no_network = FakeClient([])
            self.assertTrue(bulk.refresh(output, no_network, page_size=1, run_id="duplicate-test")["complete"])
            self.assertEqual([], no_network.calls)

    def test_api_inconsistent_pagination_never_publishes(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            state = bulk.refresh(output, FakeClient([page(1, [row("A")]), page(2, [row("B")], total=3)]), page_size=1, run_id="changed-test")
            self.assertFalse(state["complete"])
            self.assertIn("Pagination changed", state["error"])
            self.assertFalse((output / "latest-complete-source.json").exists())

    def test_identity_code_conflicts_and_connected_variants_are_all_quarantined(self):
        variants = [row("A", "same-id"), row("B", "same-id"), row("B", "different-id"), row("C")]
        accepted, report = bulk.reconcile_source_rows(variants)
        self.assertEqual(["C"], [r["code"] for r in accepted])
        self.assertEqual(3, report["quarantinedRawRowCount"])
        self.assertEqual(1, report["conflictGroupCount"])
        self.assertEqual(0, report["extraIdenticalRowCount"])
        self.assertEqual(variants[:3], [r["rawSourceRow"] for r in report["quarantinedGroups"][0]["records"]])
        changed = row("D"); changed["name_vi"] = "Changed observation"
        accepted, report = bulk.reconcile_source_rows([row("D"), changed, row("E")])
        self.assertEqual(["E"], [r["code"] for r in accepted])
        self.assertEqual(2, report["quarantinedRawRowCount"])

    def test_invalid_source_rows_are_preserved_in_quarantine_not_promoted(self):
        invalid = row("bad"); invalid["nutritional_components"] = None
        accepted, report = bulk.reconcile_source_rows([invalid, row("valid"), "not a row"])
        self.assertEqual(["valid"], [r["code"] for r in accepted])
        self.assertEqual(2, report["quarantinedRawRowCount"])
        self.assertEqual(2, report["conflictGroupCount"])
        self.assertEqual(3, report["rawRowCount"])

    def test_temp_audit_and_ocr_queue_do_not_claim_eligibility_or_execution(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(bulk, "previous_evidence", return_value={}):
            output = Path(temporary)
            source = output / "test-source.json"
            bulk.write(source, [row("A"), row("B")])
            bulk.collect_images([row("A"), row("B")], output, FakeClient([(PNG, "image/png"), (PNG, "image/png")]))
            report = bulk.audit(source, output)
            self.assertEqual(0, report["newBulkSolverEligibleCount"])
            self.assertFalse(report["runtimeCatalogModified"])
            self.assertEqual(2, report["verifiedLocalImageCount"])
            result = bulk.review(output, batch_size=1)
            self.assertEqual(2, result["readyOcrTasks"])
            index = bulk.load(output / "ocr-tasks/queue-index.json")
            self.assertEqual(0, index["apiCallsMade"])
            self.assertEqual(0, index["completedNewOcrCount"])
            task = bulk.load(output / "ocr-tasks/batch-0001.json")["tasks"][0]
            self.assertIsNone(task["apiModelIdentifier"])
            self.assertEqual("NOT_EXECUTED", task["executionStatus"])
            schema = bulk.load(output / "ocr-tasks/response-schema.json")
            self.assertFalse(schema["additionalProperties"])
            self.assertEqual("array", schema["properties"]["ingredients"]["type"])

    def test_prior_absent_ingredient_list_same_image_hash_skips_repeat_ocr(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            source = output / "source.json"
            bulk.write(source, [row("A")])
            with patch.object(bulk, "previous_evidence", return_value={}):
                bulk.collect_images([row("A")], output, FakeClient([(PNG, "image/png")]))
            previous = {"A": {"source_id": "id-A", "image": {"source_url": bulk.ORIGIN + "/storage/A.png", "sha256": bulk.sha(PNG)},
                "extraction_status": "no_image_ingredient_list", "reason": "Photo without ingredient text"}}
            with patch.object(bulk, "previous_evidence", return_value=previous):
                bulk.audit(source, output)
                result = bulk.review(output)
            self.assertEqual(0, result["readyOcrTasks"])
            self.assertEqual(1, result["knownUnchangedPhotoOnlySkipped"])
            photo = bulk.load(output / "ocr-tasks/known-photo-only-no-ingredient-list.json")[0]
            self.assertEqual("ABSENT", photo["ingredientListStatus"])
            self.assertFalse(photo["newOcrExecutionRequired"])


if __name__ == "__main__":
    unittest.main()
