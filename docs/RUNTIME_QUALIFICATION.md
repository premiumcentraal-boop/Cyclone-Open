# Runtime launch planning and compatibility evidence

## Pin the artifact pair

Obtain the approved AOSP image and the matching Cuttlefish host package for the same build and target. For AVF, use an authorized host backend artifact and an image that the firmware's documented AVF interface supports. Record their upstream build ID. The pin command records the bytes you supply. Verify their source independently before considering a launch. No approved artifact pair or Android host is available in the current workspace.

```sh
python3 -B tools/qualification/launch_plan.py pin \
  --backend cuttlefish --guest-abi arm64 --android-api 35 \
  --build-id APPROVED_BUILD_ID --host-artifact /path/to/host-package \
  --guest-image /path/to/guest-image > launch-plan.json

python3 -B tools/qualification/launch_plan.py inspect \
  --plan launch-plan.json --host-artifact /path/to/host-package \
  --guest-image /path/to/guest-image --workspace /path/to/lab
```

On Cuttlefish, inspect checks the actual architecture, KVM access, workspace, and artifact digests through the [host preflight](CUTTLEFISH_REFERENCE.md). On the Pixel track, inspect checks the supplied pins and reports blocked until authorized hardware and interface compatibility are verified. Android's AVF feature advertisement, Linux Terminal and Microdroid are separate capability observations. A full Android guest requires a suitable Android image, a working display and input path, and observed framework boot. [Official Cuttlefish setup](https://source.android.com/docs/devices/cuttlefish/get-started) · [Android Virtualization Framework](https://source.android.com/docs/core/virtualization) · [VirtualizationService](https://source.android.com/docs/core/virtualization/virtualization-service)

## First boot on an authorized lab host

- **Cuttlefish.** After preflight passes, unpack both pinned archives in a fresh private workspace. Follow the pinned build's `cvd` or `launch_cvd` instructions. Start one instance first, with a persistent data directory and ADB and WebRTC restricted to the operator. Use `adb devices -l` to record the assigned endpoint and target subsequent commands with `adb -s SERIAL`. Check `getprop sys.boot_completed`, Android API and ABI inside that target. Record the launcher first frame and deliberately routed touch input. Stop and restart with the same data directory and verify a harmless guest marker survives. Provision guest B with a different volume before the isolation suite.
- **Pixel AVF.** On the owner's authorized Pixel 8, record firmware API, ABI, available resources, and Android virtualization feature advertisement with the device's documented tools. Confirm the firmware exposes a supported full-Android guest launch interface and a usable display and input path. Gather a compatible ARM64 Android image and pin it with the host backend artifact. Select the launch procedure from the confirmed interface once those gates are met. Keep the Pixel track blocked if the only available guest interfaces support Linux or Microdroid.

## Capture observations independently

Use two disposable guests with separate storage and a control. Record actual framework boot, first frame and routed input. Use harmless distinct markers in each guest. Check both expected presence and expected absence, then cleanly restart to check persistence. Use a test APK to record installation, launch, update and retained app data. Record test notifications, graphics and media observations and recovery in separate captures. Keep ADB and viewer transport private, and retain no account data in the captures.

The observation document has `schema` set to `1`, `source` set to `fixture` or `lab`, an exact `plan` copy, two distinct canonical UUIDs in `instances`, a nonnegative `generation`, and `cases`. Each case has a typed `observed` object and its own `artifact_sha256`. The required case IDs and fields are in `expected_cases` in [the harness](../tools/qualification/compatibility_harness.py). Leave an unobserved case absent. Store each capture as `<artifact_sha256>.blob` in the chosen capture directory. The capture digest verifies file integrity. A reviewer still needs to confirm the capture's measurements and provenance.

```sh
python3 -B tools/qualification/compatibility_harness.py \
  --plan launch-plan.json --observations observations.json --artifacts /path/to/captures
```

The harness distinguishes missing, mismatched and unverifiable cases. Complete fixtures return `FIXTURE_MATCHED`. Complete lab submissions return `LAB_REVIEW_REQUIRED`. Guest execution and certification stay `NOT_RUN` in this offline tool. Licensing, Google Play and app policy remain separate gates.
