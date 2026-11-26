# Source provenance
- Cyclone source candidate: 5.0.0-alpha.155.dev1, Android versionCode 309, 1ad783823c79960d9cc09935e8f46253c3823dcc.
- [Version metadata](https://github.com/premiumcentraal-boop/Cyclone/blob/1ad783823c79960d9cc09935e8f46253c3823dcc/release/version.toml).
- [GlassOptics](https://github.com/premiumcentraal-boop/Cyclone/blob/1ad783823c79960d9cc09935e8f46253c3823dcc/apps/mobile/app/src/main/java/com/cyclone/mobile/ui/overlay/glass/GlassOptics.kt) copied with namespace/doc-link adjustment.
- ProfileSetupRuntime, ProfileRegistryStore and ProfileSwitch at same source revision were read as architecture references; registry uses independent types and atomic file writes, not copied Android user/root commands.
- Build stack AGP 8.7.3, Kotlin 2.2.21, Compose BOM 2025.05.01 adapted from same revision. SDK 35 selected for initial build rather than alpha155's compileSdk 36. 
- Alpha154 published tag exists; alpha155 source candidate verified via release/version.toml, no alpha155–157 published tag found in queried refs. Not a guarantee no inaccessible newer branch exists.
- No Cloak identity hooks, integrity changes, privileged helper, model keys, signing secrets or original Cyclone package/authorities included.
- Cyclone upstream describes its license as proprietary. Owner authorized reuse; do not assume public source equals open-source license. Keep provenance and review before redistribution.
