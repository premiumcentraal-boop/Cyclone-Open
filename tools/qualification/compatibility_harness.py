"""Compare independently captured observations to a pinned plan offline.

Test design adapted from premiumcentraal-boop/Cyclone-Cloak at
62a05e4a966119908125016211096983cb258529, docs/SERVICE-SURFACE-ISOLATION-V2.md
and docs/PLAY-SURFACE-CONTRACT-V1.md (MIT, Copyright 2026 Cyclone Cloak contributors).
See docs/licenses/Cyclone-Cloak-MIT.txt. Cyclone Open checks cross-guest observations,
runtime scope and capture integrity without importing donor runtime or test code.
"""

import argparse
from dataclasses import asdict
import json
from pathlib import Path
from uuid import UUID

from launch_plan import LaunchPlan, digest, file_digest, strict_json


def expected_cases(plan: LaunchPlan) -> dict:
    return {
        "boot": {"android_api": plan.android_api, "abi": plan.guest_abi, "boot_completed": True},
        "display_input": {"first_frame": True, "input_reached_target": True, "other_target_unchanged": True},
        "persistence": {"guest_restarted": True, "marker_before": True, "marker_after": True},
        "isolation": {"a_in_a": True, "a_in_b": False, "b_in_b": True, "b_in_a": False, "control_has_marker": False},
        "install_update": {"installed": True, "launched": True, "updated": True, "marker_after_update": True},
        "notifications": {"posted": True, "received_in_target": True, "other_target_received": False},
        "graphics_media": {"frames_verified": True, "media_playback_verified": True, "input_latency_measured": True},
        "recovery": {"interruption_observed": True, "reopened": True, "marker_preserved": True},
    }


def same_fields(expected: dict, actual: object) -> bool:
    return isinstance(actual, dict) and expected.keys() == actual.keys() and all(
        type(actual[key]) is type(value) and actual[key] == value for key, value in expected.items()
    )


def assess(plan: LaunchPlan, record: dict, artifacts: Path) -> dict:
    required_fields = {"schema", "source", "plan", "instances", "generation", "cases"}
    if set(record) != required_fields or type(record["schema"]) is not int or record["schema"] != 1:
        raise ValueError("Unsupported observation schema.")
    if record["source"] not in ("fixture", "lab") or not same_fields(asdict(plan), record["plan"]):
        raise ValueError("Observation source or runtime scope mismatch.")
    instances = record["instances"]
    if not isinstance(instances, list) or len(instances) != 2 or any(
        not isinstance(value, str) or str(UUID(value)) != value for value in instances
    ) or instances[0] == instances[1]:
        raise ValueError("Record two distinct, canonical lab instance IDs.")
    if type(record["generation"]) is not int or record["generation"] < 0:
        raise ValueError("Invalid generation.")
    expected = expected_cases(plan)
    cases = record["cases"]
    if not isinstance(cases, dict) or not cases.keys() <= expected.keys():
        raise ValueError("Unknown compatibility case.")
    results = {}
    used_pins = set()
    for name, values in expected.items():
        if name not in cases:
            results[name] = "NOT_RUN"
            continue
        actual = cases[name]
        if not isinstance(actual, dict) or set(actual) != {"artifact_sha256", "observed"}:
            raise ValueError("Invalid observation evidence pin.")
        pin = actual["artifact_sha256"]
        if not digest(pin) or pin in used_pins:
            raise ValueError("Each case needs its own pinned capture.")
        used_pins.add(pin)
        observed = actual["observed"]
        if not isinstance(observed, dict) or observed.keys() != values.keys() or any(
            type(observed[key]) is not type(value) for key, value in values.items()
        ):
            raise ValueError("Invalid observation fields or types.")
        try:
            verified = file_digest(artifacts / f"{pin}.blob") == pin
        except (OSError, ValueError):
            verified = False
        if verified:
            results[name] = "MATCHED" if observed == values else "MISMATCH"
        else:
            results[name] = "UNVERIFIED"
    matched = all(status == "MATCHED" for status in results.values())
    if record["source"] == "fixture":
        result = "FIXTURE_MATCHED" if matched else "FIXTURE_INCOMPLETE"
    else:
        result = "LAB_REVIEW_REQUIRED" if matched else "LAB_INCOMPLETE"
    return {
        "result": result, "plan": asdict(plan), "generation": record["generation"], "cases": results,
        "guest_execution": "NOT_RUN", "certification": "NOT_RUN",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--observations", type=Path, required=True)
    parser.add_argument("--artifacts", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = assess(LaunchPlan(**strict_json(args.plan)), strict_json(args.observations), args.artifacts)
        print(json.dumps(result, indent=2))
        return 0 if result["result"] in ("FIXTURE_MATCHED", "LAB_REVIEW_REQUIRED") else 2
    except (OSError, ValueError, TypeError, RecursionError):
        parser.error("Invalid launch plan, observation scope, or evidence.")


if __name__ == "__main__":
    raise SystemExit(main())
