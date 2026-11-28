from dataclasses import asdict
import hashlib
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4

from compatibility_harness import assess, expected_cases
from launch_plan import LaunchPlan, SUITE, file_digest, inspect, strict_json


class RuntimeQualificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.host = self.root / "host"
        self.image = self.root / "image"
        self.host.write_bytes(b"host fixture only")
        self.image.write_bytes(b"image fixture only")
        self.plan = LaunchPlan(1, "avf", "fixture", "arm64", 35, file_digest(self.host), file_digest(self.image), SUITE)
        self.observations = {
            "schema": 1, "source": "fixture", "plan": asdict(self.plan),
            "instances": [str(uuid4()), str(uuid4())], "generation": 1, "cases": {},
        }

    def test_avf_pins_are_read_only_and_never_report_guest_boot(self):
        report = inspect(self.plan, self.host, self.image, self.root)
        self.assertEqual(report["guest_image"], "HASH_MATCH")
        self.assertEqual(report["result"], "BLOCKED")
        self.assertEqual(report["guest_execution"], "NOT_RUN")
        self.image.write_bytes(b"changed")
        self.assertEqual(inspect(self.plan, self.host, self.image, self.root)["guest_image"], "HASH_MISMATCH")
        link = self.root / "host-link"
        link.symlink_to(self.host)
        self.assertEqual(inspect(self.plan, link, self.image, self.root)["host_package"], "SYMLINK_REJECTED")

    def test_duplicate_json_field_and_invalid_target_fail(self):
        path = self.root / "plan.json"
        path.write_text('{"schema":1,"schema":2}')
        with self.assertRaises(ValueError):
            strict_json(path)
        fields = asdict(self.plan)
        fields["guest_abi"] = "x86_64"
        with self.assertRaises(ValueError):
            LaunchPlan(**fields)

    def test_isolation_requires_independent_absence_and_presence(self):
        pin = self.capture(b"synthetic marker observation")
        matching = {"a_in_a": True, "a_in_b": False, "b_in_b": True, "b_in_a": False, "control_has_marker": False}
        self.observations["cases"]["isolation"] = {"artifact_sha256": pin, "observed": matching}
        result = assess(self.plan, self.observations, self.root)
        self.assertEqual(result["cases"]["isolation"], "MATCHED")
        self.assertEqual(result["cases"]["boot"], "NOT_RUN")
        matching["a_in_b"] = True
        self.assertEqual(assess(self.plan, self.observations, self.root)["cases"]["isolation"], "MISMATCH")

    def test_scope_changes_and_missing_captures_do_not_promote_results(self):
        self.observations["cases"]["boot"] = {
            "artifact_sha256": "f" * 64,
            "observed": {"android_api": 35, "abi": "arm64", "boot_completed": True},
        }
        self.assertEqual(assess(self.plan, self.observations, self.root)["cases"]["boot"], "UNVERIFIED")
        self.observations["plan"]["schema"] = True
        with self.assertRaises(ValueError):
            assess(self.plan, self.observations, self.root)

    def test_complete_fixture_and_complete_lab_input_never_claim_execution(self):
        for name, values in expected_cases(self.plan).items():
            self.observations["cases"][name] = {"artifact_sha256": self.capture(name.encode()), "observed": values}
        self.assertEqual(assess(self.plan, self.observations, self.root)["result"], "FIXTURE_MATCHED")
        self.observations["source"] = "lab"
        result = assess(self.plan, self.observations, self.root)
        self.assertEqual(result["result"], "LAB_REVIEW_REQUIRED")
        self.assertEqual(result["plan"], asdict(self.plan))
        self.assertEqual(result["generation"], self.observations["generation"])
        self.assertEqual(result["guest_execution"], "NOT_RUN")
        self.assertEqual(result["certification"], "NOT_RUN")

    def test_report_requires_distinct_instances_and_captures(self):
        pin = self.capture(b"one synthetic capture")
        for name, values in list(expected_cases(self.plan).items())[:2]:
            self.observations["cases"][name] = {"artifact_sha256": pin, "observed": values}
        with self.assertRaisesRegex(ValueError, "own pinned capture"):
            assess(self.plan, self.observations, self.root)
        self.observations["instances"][1] = self.observations["instances"][0]
        with self.assertRaisesRegex(ValueError, "distinct"):
            assess(self.plan, self.observations, self.root)

    def capture(self, content: bytes) -> str:
        sha = hashlib.sha256(content).hexdigest()
        (self.root / f"{sha}.blob").write_bytes(content)
        return sha
