"""Read-only prerequisites for a single authorized AOSP Cuttlefish reference boot."""

import argparse
import fcntl
import hashlib
import json
import os
import platform
import re
import shutil
import stat
from pathlib import Path


KVM_GET_API_VERSION = 0xAE00
SUPPORTED_KVM_API = 12
HOST_ABIS = {"aarch64": "arm64", "arm64": "arm64", "x86_64": "x86_64"}


def kvm_status(device: Path) -> str:
    try:
        if not stat.S_ISCHR(device.stat().st_mode):
            return "NOT_CHARACTER_DEVICE"
        descriptor = os.open(device, os.O_RDWR | os.O_CLOEXEC)
        try:
            version = fcntl.ioctl(descriptor, KVM_GET_API_VERSION, 0)
        finally:
            os.close(descriptor)
        return "AVAILABLE" if version == SUPPORTED_KVM_API else "UNSUPPORTED_API"
    except FileNotFoundError:
        return "MISSING"
    except PermissionError:
        return "PERMISSION_DENIED"
    except OSError:
        return "UNAVAILABLE"


def artifact_status(artifact: Path | None, expected: str | None) -> str:
    if artifact is None or expected is None:
        return "PIN_REQUIRED"
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        return "INVALID_PIN"
    if artifact.is_symlink():
        return "SYMLINK_REJECTED"
    if not artifact.is_file():
        return "MISSING"
    try:
        with artifact.open("rb") as stream:
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
    except OSError:
        return "UNREADABLE"
    return "HASH_MATCH" if actual == expected else "HASH_MISMATCH"


def workspace_status(workspace: Path) -> dict:
    try:
        if not workspace.is_dir() or not os.access(workspace, os.W_OK | os.X_OK):
            return {"status": "UNAVAILABLE", "free_bytes": None}
        return {"status": "AVAILABLE", "free_bytes": shutil.disk_usage(workspace).free}
    except OSError:
        return {"status": "UNAVAILABLE", "free_bytes": None}


def preflight(args: argparse.Namespace) -> dict:
    host_abi = HOST_ABIS.get(platform.machine()) if platform.system() == "Linux" else None
    workspace = workspace_status(args.workspace)
    kvm = kvm_status(Path("/dev/kvm")) if host_abi else "UNSUPPORTED_HOST"
    host = artifact_status(args.host_package, args.host_sha256)
    image = artifact_status(args.guest_image, args.guest_sha256)
    blockers = [
        reason for passed, reason in (
            (host_abi == args.guest_abi, "HOST_ABI_MISMATCH"),
            (kvm == "AVAILABLE", f"KVM_{kvm}"),
            (workspace["status"] == "AVAILABLE", "WORKSPACE_UNAVAILABLE"),
            (host == "HASH_MATCH", f"HOST_PACKAGE_{host}"),
            (image == "HASH_MATCH", f"GUEST_IMAGE_{image}"),
            (args.build_id is not None, "BUILD_ID_REQUIRED"),
        ) if not passed
    ]
    return {
        "schema": 1,
        "result": "BLOCKED" if blockers else "PREFLIGHT_PASSED",
        "blockers": blockers,
        "host_abi": host_abi,
        "guest_abi_requested": args.guest_abi,
        "kvm": kvm,
        "workspace": workspace,
        "host_package": host,
        "guest_image": image,
        "build_id": args.build_id,
        "guest_execution": "NOT_RUN",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--guest-abi", required=True, choices=("arm64", "x86_64"))
    parser.add_argument("--workspace", type=Path, default=Path.cwd())
    parser.add_argument("--build-id", type=build_id)
    parser.add_argument("--host-package", type=Path)
    parser.add_argument("--host-sha256")
    parser.add_argument("--guest-image", type=Path)
    parser.add_argument("--guest-sha256")
    evidence = preflight(parser.parse_args())
    print(json.dumps(evidence, indent=2))
    return 2 if evidence["result"] == "BLOCKED" else 0


def build_id(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,64}", value):
        raise argparse.ArgumentTypeError("Enter the build ID of the paired AOSP artifacts.")
    return value


if __name__ == "__main__":
    raise SystemExit(main())
