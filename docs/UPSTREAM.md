# Source provenance
- Cyclone source candidate: 5.0.0-alpha.155.dev1, Android versionCode 309, 1ad783823c79960d9cc09935e8f46253c3823dcc.
- [Version metadata](https://github.com/premiumcentraal-boop/Cyclone/blob/1ad783823c79960d9cc09935e8f46253c3823dcc/release/version.toml).
- [GlassOptics](https://github.com/premiumcentraal-boop/Cyclone/blob/1ad783823c79960d9cc09935e8f46253c3823dcc/apps/mobile/app/src/main/java/com/cyclone/mobile/ui/overlay/glass/GlassOptics.kt) copied with namespace/doc-link adjustment.
- ProfileSetupRuntime, ProfileRegistryStore and ProfileSwitch at same source revision were read as architecture references; registry uses independent types and atomic file writes, not copied Android user/root commands.
- Build stack AGP 8.7.3, Kotlin 2.2.21, Compose BOM 2025.05.01 adapted from same revision. SDK 35 selected for initial build rather than alpha155's compileSdk 36. 
- Alpha154 published tag exists; alpha155 source candidate verified via release/version.toml, no alpha155–157 published tag found in queried refs. Not a guarantee no inaccessible newer branch exists.
- No Cloak identity hooks, integrity changes, privileged helper, model keys, signing secrets or original Cyclone package/authorities included.
- Cyclone upstream describes its license as proprietary. Owner authorized reuse; do not assume public source equals open-source license. Keep provenance and review before redistribution.

## Ported files

The owner authorized reuse from `premiumcentraal-boop/cyclone-cloak-forge` at `5113af4a84a6607b067b08566431c304c74f1776`. This revision contains no `LICENSE` file. The port retains source attribution and does not assign an open-source license to Forge or proprietary Cyclone code.

| Cyclone Open file | Source at the pinned revision | License | Changes | Tests |
| --- | --- | --- | --- | --- |
| `core/ProfileStorage.kt` | Forge `app/src/main/java/com/cyclonecloak/app/data/ProfileStorage.kt` | Owner-authorized reuse | Uses private durable and cache roots, canonical UUIDs, no symlink traversal, atomic file publication, and typed disk-full errors. | `ProfileStorageRecoveryTest`, `ProfileRestartTest` |
| `core/ProfileStorageRecoveryTest.kt` | Forge `app/src/androidTest/java/com/cyclonecloak/app/StartupRecoveryTest.kt` and `ProfileStorageTest.kt` in that directory | Owner-authorized reuse | Tests interrupted empty-directory cleanup, independent areas, and registry rollback after simulated Linux storage errors. | Android instrumentation |
| `core/ProfileRestartTest.kt` | Forge `app/src/androidTest/java/com/cyclonecloak/app/ProfileActivationRestartTest.kt`, `ProfileCreationRestartTest.kt`, and `ProfileDeletionRestartTest.kt` in that directory | Owner-authorized reuse | Combines two separate Android instrumentation invocations around `am force-stop`; verifies a different PID and intact registry and storage. | Android instrumentation restart phases |
| `core/PhoneVolumeRepository.kt` | Forge `app/src/main/java/com/cyclonecloak/app/data/ProfileRepository.kt` and `app/src/main/java/com/cyclonecloak/app/CycloneCloakApplication.kt` | Owner-authorized reuse | Adapts reserve, prepare, publish, and startup recovery to private directories and the existing AtomicFile registry. | `PhoneVolumeRepositoryTest`, `ProfileRestartTest` |
| `core/PhoneVolumeRepositoryTest.kt` | Forge `app/src/androidTest/java/com/cyclonecloak/app/ProfileStorageTest.kt` and `StartupRecoveryTest.kt` in that directory | Owner-authorized reuse | Exercises legacy draft provisioning, selection safety on ENOSPC, and recovery at both sides of publication. | Android instrumentation |
| `core/ProfileSwitchCoordinator.kt` | Cyclone `apps/mobile/app/src/main/java/com/cyclone/mobile/runtime/workspaces/ProfileSwitch.kt` and `WorkspaceRuntime.kt` in that directory | Proprietary, owner-authorized reuse | Adapts preflight, prepare, confirm, and generation binding to typed runtime adapters. Stores no privileged commands. | `ProfileSwitchCoordinatorTest`, `AtomicSwitchJournalTest` |
| `core/AtomicSwitchJournal.kt` | Cyclone `apps/mobile/app/src/main/java/com/cyclone/mobile/runtime/workspaces/ProfileStepJournal.kt` | Proprietary, owner-authorized reuse | Uses an independent AtomicFile for the pending phase, target, lease and last failure. | `AtomicSwitchJournalTest` |
| `ui/GlassOptics.kt` | Cyclone `apps/mobile/app/src/main/java/com/cyclone/mobile/ui/overlay/glass/GlassOptics.kt` | Proprietary, owner-authorized reuse | Namespace and documentation link adapted. | `OpenProfileUiTest` exercises the glass surface |
| `ui/OpenGlass.kt` | Cyclone `apps/mobile/app/src/main/java/com/cyclone/mobile/ui/overlay/glass/TiltGlass.kt`, `GlassKit.kt`, and `GlassTools.kt` in that directory. Cyclone-Cloak `android/app/src/main/java/dev/cyclone/cloak/ui/CloakTheme.kt` and `CloakDesign.kt` in that directory. | Cyclone proprietary and owner-authorized, Cyclone-Cloak MIT | Adapts rim optics, contrast, palette, tool labels, and reduced-motion handling. | `OpenProfileUiTest` |
| `ui/PhoneProfileGallery.kt` | Cyclone `apps/mobile/app/src/main/java/com/cyclone/mobile/ui/v32/CycloneProfilesPage.kt` and `ProfileGlassKit.kt` in that directory. Cyclone-Cloak `android/app/src/main/java/dev/cyclone/cloak/ui/CloakProfileGallery.kt`. | Cyclone proprietary and owner-authorized, Cyclone-Cloak MIT | Keeps labelled profile cards and deterministic avatars. Uses real draft state and accessible selection and action labels. | `OpenProfileUiTest` |
| `ui/Aurora.kt` | Cyclone `apps/mobile/app/src/main/java/com/cyclone/mobile/ui/ProfileRescueBar.kt` | Proprietary, owner-authorized reuse | Adds an in-app host-owned sheet for Android Home and draft selection. | `OpenProfileUiTest` |

The MIT components are pinned to `premiumcentraal-boop/Cyclone-Cloak` at `62a05e4a966119908125016211096983cb258529`. The [MIT notice](licenses/Cyclone-Cloak-MIT.txt) applies to those components. The APK packages the notice at `assets/Cyclone-Cloak-MIT.txt`, and CI checks it byte for byte.

## Cloak reuse handoff

On 28 November 2026, Cloak `main` still pointed to the RC3 calm-glass merge `62a05e4a966119908125016211096983cb258529`. The connector returned no associated PR for that commit. Donor file bytes were matched to the pinned Git blob digests. Both donor repositories were used read-only. [Cloak merge](https://github.com/premiumcentraal-boop/Cyclone-Cloak/commit/62a05e4a966119908125016211096983cb258529)

| Cyclone Open file | Source at the pinned revision | License | Changes | Tests |
| --- | --- | --- | --- | --- |
| `core/ReferenceCatalog.kt` | Cloak `docs/CATALOG-IMPORT-BRIDGE-V1.md`, blob `e4dff68d3e3d8916079ac67ec2f8f22e6525bc17`. Display inventory also checked against `catalog/device-factory-v1.json`, blob `310104bf3781d273b74760dcf8483c7755f69831`. | MIT for donor material | Adapts bounded parsing and provenance. Only a closed display and draft sizing schema enters the app. | `ReferenceCatalogReaderTest` |
| `core/DraftReview.kt`, `ui/DraftReviewPanel.kt` | Cloak `android/app/src/main/java/dev/cyclone/cloak/ui/PhoneEditorScreen.kt`, blob `6aebdc86a26797958097588f4f1fd1b690ca3703`, and `ProfileOperations.kt`, blob `6a6de1c4691c56dfe5a3e6e248e54dd1acb9dd17` | MIT | Adapts the preview and revalidation patterns. Approval binds the exact catalog and image reference before the existing volume transaction runs. | `DraftReviewsTest`, `PhoneVolumeRepositoryTest`, `OpenProfileUiTest` |
| `core/RuntimeReadiness.kt` | Cloak `docs/PLAY-SURFACE-CONTRACT-V1.md`, blob `45dd9fcda438054b11889d7934795d4d505eca61`, and `docs/SERVICE-SURFACE-ISOLATION-V2.md`, blob `9d931679c64c7c2f8c584e2da956460e89e7e2c2` | MIT | Adapts evidence binding and freshness. Guest operation, app compatibility, store functionality, licensing, and certification use independent gates. | `RuntimeReadinessTest` |
| `ui/RuntimeDoctorPanel.kt` | Cloak `android/app/src/main/java/dev/cyclone/cloak/ui/ProfileReadinessCard.kt`, blob `543530c3e9cce09ab28d3794cfd55fb0b3d908cd` | MIT | Reuses readiness rows with measured controller facts and explicit guest blockers. | `OpenProfileUiTest` |

The Pixel 7-class portrait dimensions and maximum refresh were independently checked against Google's hardware specifications. Samsung's support specifications supply the base Galaxy S22 dimensions, and its Global Newsroom supplies the refresh rate. The donor catalog contains a Galaxy S23 entry. The S22-class record uses Samsung's S22 sources. Both records are revision 1. The 420 dpi and 3 GiB values are Cyclone Open draft recommendations, distinct from physical panel density and actual runtime allocations. Only the cited factual values are reproduced, with no third-party imagery or firmware. [Google Pixel 7 specifications](https://support.google.com/pixelphone/answer/7158570/pixel-phone-hardware-tech-specs?hl=en-GB) · [Samsung Galaxy S22 dimensions](https://www.samsung.com/sg/support/mobile-devices/enjoy-the-epic-standard-of-smartphone-experiences-with-the-galaxy-s22-series/) · [Samsung Galaxy S22 refresh](https://news.samsung.com/global/new-samsung-galaxy-s22-and-s22-plus-deliver-revolutionary-camera-experiences-day-and-night)

### Draft backup ports

These ports come from Cyclone-Cloak `62a05e4a966119908125016211096983cb258529`. The MIT notice is bundled in the APK.

| Cyclone Open file | Pinned donor path and Git blob | License | Adaptation and tests |
| --- | --- | --- | --- |
| `core/StrictJson.kt` | `android/app/src/main/java/dev/cyclone/cloak/forge/StrictJson.kt` · `7d783b2f319a51d1e88283158d44d73e332031bc` | MIT | Rejects duplicate names, lenient grammar and trailing input. Adds bounded UTF-8 reads for catalog and backups. `ProfileBackupTest` and `ReferenceCatalogReaderTest`. |
| `core/JsonDirectory.kt` | `android/app/src/main/java/dev/cyclone/cloak/data/JsonDirectory.kt` · `9155dd6f3943779787db31afa0165fab5895c458` | MIT | Canonical UUID filenames, synced staged writes, atomic directory swap, restart recovery. `ProfileBackupTest`. |
| `core/ProfileBackup.kt` | `android/app/src/main/java/dev/cyclone/cloak/forge/ProfileBackup.kt` · `8a655c6c351c8d3e5db5fd436b63c5c2ec3b626b` | MIT | Rewrites the backup schema around draft settings and an image digest. Restore creates a new reviewed draft. `ProfileBackupTest`. |
