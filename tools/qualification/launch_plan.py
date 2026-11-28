"""Pin explicit local artifact bytes and inspect a launch plan without executing a guest."""

import argparse
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import re

from cuttlefish_preflight import artifact_status, preflight


SUITE = "cyclone-smoke-v1"


def strict_json(path: Path) -> dict:
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("Duplicate JSON field.")
            result[key] = value
        return result

    def invalid_constant(_):
        raise ValueError("Non-JSON number.")

    with path.open("rb") as source:
        data = source.read(65_537)
    if len(data) > 65_536:
        raise ValueError("Qualification document exceeds 64 KiB.")
    value = json.loads(data.decode("utf-8"), object_pairs_hook=pairs, parse_constant=invalid_constant)
    if not isinstance(value, dict):
        raise ValueError("Expected a JSON object.")
    return value


def digest(value: object) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def file_digest(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        raise ValueError("Expected a regular artifact file.")
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


@dataclass(frozen=True)
class LaunchPlan:
    schema: int
    backend: str
    build_id: str
    guest_abi: str
    android_api: int
    host_sha256: str
    image_sha256: str
    suite: str

    def __post_init__(self):
        if type(self.schema) is not int or self.schema != 1 or self.suite != SUITE:
            raise ValueError("Unsupported launch plan schema or suite.")
        if self.backend not in ("cuttlefish", "avf") or self.guest_abi not in ("arm64", "x86_64"):
            raise ValueError("Unsupported backend or ABI.")
        if self.backend == "avf" and self.guest_abi != "arm64":
            raise ValueError("The Pixel AVF reference requires ARM64.")
        if not isinstance(self.build_id, str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,64}", self.build_id):
            raise ValueError("Invalid upstream build ID.")
        if type(self.android_api) is not int or self.android_api not in range(33, 37):
            raise ValueError("Unsupported Android API target.")
        if not digest(self.host_sha256) or not digest(self.image_sha256):
            raise ValueError("Both local artifacts need SHA-256 pins.")


def inspect(plan: LaunchPlan, host: Path, image: Path, workspace: Path) -> dict:
    if plan.backend == "cuttlefish":
        result = preflight(argparse.Namespace(
            guest_abi=plan.guest_abi, workspace=workspace, build_id=plan.build_id,
            host_package=host, host_sha256=plan.host_sha256, guest_image=image, guest_sha256=plan.image_sha256,
        ))
    else:
        result = {
            "result": "BLOCKED",
            "blockers": ["AUTHORIZED_ANDROID_HOST_REQUIRED", "AVF_BACKEND_AND_IMAGE_COMPATIBILITY_NOT_VERIFIED"],
            "host_package": artifact_status(host, plan.host_sha256),
            "guest_image": artifact_status(image, plan.image_sha256),
            "guest_execution": "NOT_RUN",
        }
    return {"plan": asdict(plan), **result}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pin = commands.add_parser("pin")
    pin.add_argument("--backend", choices=("cuttlefish", "avf"), required=True)
    pin.add_argument("--guest-abi", choices=("arm64", "x86_64"), required=True)
    pin.add_argument("--build-id", required=True)
    pin.add_argument("--android-api", type=int, required=True)
    review = commands.add_parser("inspect")
    review.add_argument("--plan", type=Path, required=True)
    review.add_argument("--workspace", type=Path, default=Path.cwd())
    for command in (pin, review):
        command.add_argument("--host-artifact", type=Path, required=True)
        command.add_argument("--guest-image", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == "pin":
            plan = LaunchPlan(1, args.backend, args.build_id, args.guest_abi, args.android_api,
                              file_digest(args.host_artifact), file_digest(args.guest_image), SUITE)
            print(json.dumps(asdict(plan), indent=2))
            return 0
        result = inspect(LaunchPlan(**strict_json(args.plan)), args.host_artifact, args.guest_image, args.workspace)
        print(json.dumps(result, indent=2))
        return 2 if result["result"] == "BLOCKED" else 0
    except (OSError, ValueError, TypeError, RecursionError):
        parser.error("Invalid or unreadable launch plan or artifact.")


if __name__ == "__main__":
    raise SystemExit(main())
