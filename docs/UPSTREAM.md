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
