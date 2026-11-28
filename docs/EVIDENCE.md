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
- Rollback is a code revert. Registry schema, filename, and serialization remain compatible with the foundation. Keep the existing APK signer when updating an installed copy with saved data.
- Pixel hardware and Android guest execution are `NOT_RUN`. No local KVM or authorized Cuttlefish node was available. Google Play rights and application compatibility remain unverified.
