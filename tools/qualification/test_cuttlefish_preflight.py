import argparse
import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from cuttlefish_preflight import artifact_status, kvm_status, preflight


class CuttlefishPreflightTest(unittest.TestCase):
    def test_missing_kvm_and_unpinned_native_artifacts_block(self):
        with tempfile.TemporaryDirectory() as temp:
            args = argparse.Namespace(
                guest_abi="arm64", workspace=Path(temp), build_id=None,
                host_package=None, host_sha256=None, guest_image=None, guest_sha256=None,
            )
            with patch("platform.system", return_value="Linux"), patch("platform.machine", return_value="x86_64"), patch(
                "cuttlefish_preflight.kvm_status", return_value="MISSING"
            ):
                result = preflight(args)
        self.assertEqual(result["result"], "BLOCKED")
        self.assertIn("HOST_ABI_MISMATCH", result["blockers"])
        self.assertIn("KVM_MISSING", result["blockers"])
        self.assertIn("GUEST_IMAGE_PIN_REQUIRED", result["blockers"])
        self.assertEqual(result["guest_execution"], "NOT_RUN")

    def test_fixture_pins_are_checked_without_starting_a_guest(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "fixture"
            path.write_bytes(b"public fixture")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            self.assertEqual(artifact_status(path, "0" * 64), "HASH_MISMATCH")
            self.assertEqual(artifact_status(path, "invalid"), "INVALID_PIN")
            self.assertEqual(kvm_status(path), "NOT_CHARACTER_DEVICE")
            args = argparse.Namespace(
                guest_abi="x86_64", workspace=Path(temp), build_id="fixture",
                host_package=path, host_sha256=digest, guest_image=path, guest_sha256=digest,
            )
            with patch("platform.system", return_value="Linux"), patch("platform.machine", return_value="x86_64"), patch(
                "cuttlefish_preflight.kvm_status", return_value="AVAILABLE"
            ):
                result = preflight(args)
        self.assertEqual(result["result"], "PREFLIGHT_PASSED")
        self.assertEqual(result["guest_execution"], "NOT_RUN")
