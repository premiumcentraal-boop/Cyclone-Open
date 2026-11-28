# Cuttlefish reference preflight

Use the read-only preflight on the authorized Linux host selected for the first AOSP guest. It checks the host ABI, opens `/dev/kvm` and queries its API version, measures free space in an existing workspace, and compares the local host package and guest image to explicit SHA-256 pins. It downloads no artifacts and starts no processes inside a guest.

Use matching artifacts from the same AOSP build and target. Obtain the build ID and digests from the approved artifact source before running the preflight. Python 3.11 or newer is required. For an ARM64 reference, run it on an ARM64 Linux host. An x86_64 host can qualify a separate x86_64 reference. [AOSP Cuttlefish setup](https://source.android.com/docs/devices/cuttlefish/get-started)

```sh
python3 -B tools/qualification/cuttlefish_preflight.py \
  --guest-abi arm64 --workspace /path/to/cuttlefish-work \
  --build-id APPROVED_BUILD_ID \
  --host-package /path/to/cvd-host_package.tar.gz --host-sha256 HOST_SHA256 \
  --guest-image /path/to/guest-image.zip --guest-sha256 IMAGE_SHA256
```

`PREFLIGHT_PASSED` means the local checks passed. Before launching, confirm the image and host package came from the same build and target, the workspace meets their storage needs, and the operator can use the Cuttlefish networking and rendering setup. Use private access for guest ADB and WebRTC. Keep Android boot, first frame, touch, app installation, and persistence after restart as separate observations tied to the exact image and backend digests.

The 28 November 2026 workspace probe used `--guest-abi arm64`. It reported `HOST_ABI_MISMATCH`, `KVM_MISSING`, `HOST_PACKAGE_PIN_REQUIRED`, `GUEST_IMAGE_PIN_REQUIRED`, and `BUILD_ID_REQUIRED`. The workspace is x86_64. Guest execution was `NOT_RUN`. The first Pixel AVF checks still require the owner's authorized Pixel over ADB. [Engineering evidence](EVIDENCE.md)
