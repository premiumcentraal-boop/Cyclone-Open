# Engineering evidence

## 28 November 2026 — M00 / B001 baseline verification

- `main` at `b17162b91592408b06510d89f5bc92ba5e82ad93`. Foundation head and archived `SOURCE_SHA` both `da8aa05b3388b5c838f38d2a22b3787d4d2a090f`. PR #1 was open, mergeable, and had zero submitted reviews. [PR #1](https://github.com/premiumcentraal-boop/Cyclone-Open/pull/1)
- Build #3 and job `140035355365` completed successfully. The report shows 12 passing unit tests, zero lint errors, and four warnings (`OldTargetApi`, two `GradleDependency`, and `DataExtractionRules`). [Build #3](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/45856271927) · [Report archive](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/45856271927/artifacts/15135339683)
- Both downloaded ZIPs matched the GitHub digests. The APK archive was 8,262,051 bytes and contained the 23,802,175-byte APK, `SHA256SUMS`, and `SOURCE_SHA`. `sha256sum -c SHA256SUMS` and SDK 35.0.0 `apksigner verify --verbose --print-certs` passed. `aapt dump badging` reported `com.cyclone.open`, `0.1.0-alpha.1`, versionCode 1, minSdk 33, and target/compile SDK 35. [APK archive](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/45856271927/artifacts/15135543222)
- Read-only comparison confirmed the pinned Cyclone version `5.0.0-alpha.155.dev1` (versionCode 309) and that the copied `GlassOptics.kt` differs from upstream in its namespace and one documentation link. [Upstream version metadata](https://github.com/premiumcentraal-boop/Cyclone/blob/1ad783823c79960d9cc09935e8f46253c3823dcc/release/version.toml)

| Object | SHA-256 |
| --- | --- |
| APK ZIP | `8abf8ec208705cfb0bc6832bd75bfb4a992d1c7e85f177de80f6584fa9a26a35` |
| Report ZIP | `25f4b489b2786c4dc405e37e5410d6d3be255f0ed8e48e6d3c83098cdd350a25` |
| APK | `522ee752f7010863366323d901b037e91d69fa949bd781154472f942b5f4aa12` |
| Debug signer certificate (APK Signature Scheme v2) | `45854036eb13851edbfb5344b4ec2fd72161fe4474c7ae7d1bc1ede766f8c775` |

Pixel install, coexistence, Quick Settings, process death, and signing continuity are `NOT_RUN`. The foundation APK remains the preserved baseline. A new CI build may use a different debug certificate, so compare signers before attempting an update on a device with saved data.

## 28 November 2026 — M01 / B004 registry persistence

- Starting source `da8aa05b3388b5c838f38d2a22b3787d4d2a090f`, branch `hardening/registry-persistence`, based on PR #1. Upstream Cyclone source remains at the documented provenance pin.
- `RegistryStore` retains the same private path, schema, and Android `AtomicFile` recovery. Mutations validate the current data, sync the staged file, and verify publication by comparing the committed bytes. The app surfaces read failures and unconfirmed writes. An unconfirmed import keeps URI access when a saved reference might still need it.
- Android instrumentation exercises draft create, rename, select and remove across store reopen, malformed and unknown schemas, interrupted first and subsequent writes, legacy backup recovery, failed staging, and 20 concurrent changes. The interrupted-write fixtures stage actual platform `AtomicFile` writes. Full app process death, filesystem exhaustion, and sudden power loss need separate tests.
- Local Gradle 8.9 build passed `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` with 12 unit tests and zero lint errors. `actionlint` passed. GitHub Actions build #4 at `f6fa52c4ea2bb7d29d67989e8bc284ad0bb7fd1c` also passed the APK build and its 12 unit tests, with zero lint errors and six warnings. The additional warnings concern available AndroidX instrumentation dependency updates. [Build #4](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46154724970) · [Lint and unit test reports](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46154724970/artifacts/15267187131)
- Build #4's downloaded APK ZIP SHA-256 was `251952de91df25b74c5a03307c7323b3a6a0e98de890f7a0fdb9b7038bf8b9fc`. Its `SOURCE_SHA` matched the PR commit, its APK SHA-256 was `9b6ccb107b1f2bca198c52798879b8707e586ca13d8ee3efa30b30518c25e4d8`, and `apksigner` verified it. The debug signer SHA-256 was `685425f117e5e29f69576135f487d45decbaaf6534a7206cd29273a0e62bee0b`, which differs from the baseline signer. Android requires signing continuity for an in-place update. [Build #4 APK archive](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46154724970/artifacts/15266662920)
- Build #4 instrumentation stopped before running any tests. The emulator reported `Unknown AVD name [cyclone-registry]`, and the ADB wait timed out with exit 124. The follow-up workflow sets a shared AVD directory, checks the emulator can list the AVD, and prints startup errors if the process exits or misses the boot deadline. [Emulator startup log](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46154724970/artifacts/15267203827)
- Build #5 at `0b1c9e45177718c8d93b7475f067dd05bc51f5d9` passed both jobs. The reports show 12 unit tests passed and zero lint errors with six warnings. Android 15 AOSP x86_64 emulator job `141010690761` ran all seven `RegistryStoreTest` cases with zero failures, errors, or skips. The downloaded instrumentation ZIP matched SHA-256 `e65d5d5fb63f02d6f567c104325c2be02b9aea9e265c598c2ae1ab829d06980e`. [Build #5](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46155353640) · [Instrumentation report](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46155353640/artifacts/15267870806)
- Build #5's downloaded APK ZIP matched SHA-256 `20585d81954b7fa17357ae832f89aa958989a9eb65646dd2c86f2c5a190a8da1`. Its `SOURCE_SHA` matched the PR commit, its APK SHA-256 was `fb593fa9a709dfdd28e40ac56cd07cfba58bb2362aef6d72dda1592267f9b90b`, and `apksigner` verified the debug signer `d5b41438d7ddbcdf35d63bbd09a536a9fa7f567e737099a6afdeaa2c2cfa0556`. The signing certificate differs from both earlier builds. [Build #5 APK archive](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46155353640/artifacts/15267378713)
- Rollback is a code revert. Registry schema, filename, and serialization remain compatible with the foundation. Keep the existing APK signer when updating an installed copy with saved data.
- Pixel hardware and Android guest execution are `NOT_RUN`. No local KVM or authorized Cuttlefish node was available. Google Play rights and application compatibility remain unverified.
- [Draft PR #2](https://github.com/premiumcentraal-boop/Cyclone-Open/pull/2) targets the foundation branch. Both PRs are open for review.

## 28 November 2026 — CI debug signing

Local Gradle verification passed `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` with a disposable JKS injected through the workflow restore script. Both APKs and an app rebuild matched that certificate. The restore script rejected missing and malformed inputs and removed the temporary file after invalid input. The test keystore was disposed of. Owner secret provisioning, stable CI artifact comparison, and installed update data persistence are pending. [Signing setup](DEBUG_SIGNING.md)

Build #7 passed on `bc07d23b50d2c25755c3af3c0b5253a10da75020` and published test and lint reports. The signing secret was unavailable, so the workflow withheld the APK artifact. [Build #7](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46157681556)

## 28 November 2026 — M01 storage and recovery port

The Forge storage adapter adds private files, preferences, and cache areas and maps `ENOSPC` and `EDQUOT` to an actionable error. Android tests inject those errno values after a partial registry write. They compare the committed bytes and retry through the existing `RegistryStore`. A separate CI sequence force-stops the application between two instrumentation invocations and asserts a different process ID before checking state. These faults model a disk-full write. Whole-filesystem exhaustion and sudden power loss are separate cases. [Source provenance](UPSTREAM.md)

Build #8 passed on `4faf65e9b4ea62eb44bde326f43a20e68abd8b6f`. The Android 15 test archive shows ten instrumentation cases and both restart phases passing. I matched its ZIP digest before inspecting the results. [Build #8](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46158138124) · [Instrumentation reports](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46158138124/artifacts/15268856452)

## 28 November 2026 — transactionally provisioned profile volumes

New drafts prepare a private volume before the existing registry publishes them. App startup recovers the interrupted creation markers and provisions older drafts using their existing IDs. Recovery removes an empty unfinished volume, keeps prepared committed data, and defers cleanup if unexpected files are present. Removing a draft retains its profile files. The registry remains schema 1 with an optional volume marker. Profile volumes store controller files and preferences, and cache can be discarded by Android. Guest disks and guest filesystem behavior remain unverified.

Build #9 passed on `f348689dc08e11acd878db1cfa4d50be59961895`. Thirteen Android 15 tests and both restart phases passed. The second process retained data from the committed volume and cleaned an orphan left before publication. [Build #9](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46158646840) · [Instrumentation reports](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46158646840/artifacts/15268792600)

## 28 November 2026 — M03 typed switch journal

The switch coordinator writes preflight, prepare, and confirm to a separate AtomicFile journal before calling the runtime. Recovery clears an interrupted operation and retains the last confirmed lease. A confirmation with a different target or generation is rejected. The app routes its Android Home action through the host adapter, and draft selection still uses the registry. Guest adapters return `NOT_IMPLEMENTED`. Tests use a fake adapter for positive attachment and cover timeout, interruption, stale messages, and a wrong target.

Build #10 passed on `c090890fe496d536686b47f511dbb10169be248b`. The downloaded Android 15 report contains 14 successful tests and two successful restart phases. Lifecycle unit tests used typed fixture responses. [Build #10](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46158968287) · [Instrumentation reports](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46158968287/artifacts/15268932739)

## 28 November 2026 — profile gallery and Aurora

The app uses the adapted glass theme and labelled profile cards. Glass selection motion snaps when Android disables animations. Aurora is an in-app sheet with access to Android Home and the saved draft list. The image chooser searches every imported reference, including results beyond the first eight. Compose tests cover semantic selection and action labels, the Home action in Aurora, and selecting a search result. Manual TalkBack, font-scale and device checks remain separate. [Source provenance](UPSTREAM.md)

Build #11 passed on `ceafbe68199893433593ca5f46f979dd8b3fdd00`. The verified test archive contains 17 successful Android 15 instrumentation cases and both restart phases. [Build #11](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46159591823) · [Instrumentation reports](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46159591823/artifacts/15269158163)

## 28 November 2026 — M01 registry and document grants

An Android ViewModel owns in-flight registry operations across activity recreation. UI instances observe committed records from the same store. Image references release access only when no saved reference needs it. Startup reconciles interrupted-import grants, and a failed import retains its grant if the saved state is uncertain. Recheck verifies the current bytes, and Restore access verifies the original document and digest before renewing a revoked grant. Instrumentation covers duplicate references, failed publication, revoked access, changed input and activity recreation.

Build #12 passed on `3fadc9e70dd76b61f5e6e665411affcf020ce2e2`. The verified archive contains 21 passing Android 15 tests and both force-stop phases. Grant and provider scenarios use controlled fixtures. No downloadable APK was published because the owner signing secret was unavailable. These results close M01 at CI scope. [Build #12](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46160032575) · [Instrumentation reports](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46160032575/artifacts/15268864972)

## 28 November 2026 — Cloak reuse for references and readiness

- Starting source was Cyclone Open PR #2 at `7445be5d91b0bdf792dd8b243b281d6a39b0b875`. Build #13 passed both jobs at that exact head. PR #1 remained open at `da8aa05`. [PR #2](https://github.com/premiumcentraal-boop/Cyclone-Open/pull/2) · [Build #13](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46160486001)
- The app reads a bounded, versioned catalog bundled in the APK. Saved draft records refer to an optional reference ID and revision. A stale or missing reference cannot create a new volume. Existing drafts still load with their original IDs. Validation rejects unknown fields, duplicate entries and malformed data. [Catalog sources](UPSTREAM.md)
- Draft confirmation revalidates the catalog digest, reference revision, image metadata and memory choice under the same registry lock used for volume publication. The review creates no volume. Retrying the same approved instance preserves its first publication, and the existing startup journal reconciles interruptions. Runtime Doctor observes controller facts only. The qualification evaluator keeps application compatibility, isolation, Google Play licensing, store and certification checks independent and invalidates evidence after expiry or scope changes, including a new guest generation.
- `python3 -B -m unittest discover -s tools/qualification -p 'test_*.py' -v` passed two preflight tests. The workspace probe exited `2` with host ABI mismatch, KVM missing, and missing pinned artifacts and build ID. The workspace reported 61,799,059,456 free bytes in the selected directory. No Pixel was attached. Actual guest boot, functional isolation, and application compatibility remain `NOT_RUN`. [Cuttlefish preflight](CUTTLEFISH_REFERENCE.md)

## 28 November 2026 — deferred Cloak ports

- PR #3 at `f9cf1d7f90ad09c06f2a2234958e76076df27aeb` passed build #14 after retrying a Maven Central HTTP 429 on the same commit. PR #3 was marked ready after both jobs passed. PRs #1 through #3 remain unmerged. [Build #14](https://github.com/premiumcentraal-boop/Cyclone-Open/actions/runs/46169697708)
- Draft settings backups store a label, requested backend and memory, reference revision and image digest in a synced private directory. Restore requires a unique matching imported image and a fresh review. The normal creation transaction allocates a new empty profile volume. The snapshots are local to this installation and contain no guest disk data, document URI or original instance ID. [Source provenance](UPSTREAM.md)
