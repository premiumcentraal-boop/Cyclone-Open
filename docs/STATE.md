# Cyclone Open engineering state

Updated 28 November 2026.

| Milestone | State | Evidence or blocker |
| --- | --- | --- |
| M00 · APK foundation | CI and artifact verified | The debug APK from build #3 matched its archive and APK checksums, source SHA, and debug signature. PR #1 is open. Pixel acceptance awaits an authorized connected device. |
| M01 · Core hardening | In progress | Android 15 CI verified two-process restart and registry rollback after injected `ENOSPC` and `EDQUOT`. Whole-filesystem exhaustion remains untested. |
| M05 · Pixel feasibility | Blocked | This workspace has no connection to the owner's Pixel 8. Firmware, AVF access, and guest feasibility need device observations. |
| M06 · Linux reference | Blocked | This workspace is x86_64 without `/dev/kvm`. No compatible authorized Cuttlefish host is available. |
| M14 · Ecosystem | Not run | Applicable Google Play distribution rights, target eligibility, and authorized workflows have no verified evidence. |

The app manages the current host, draft configurations, and imported image references. Both guest runtimes report unavailable. Physical verification, security review, app compatibility, and Google Play licensing each have their own release gates.

Builds #3, #4, and #5 have different debug signers. The repository secret is required to publish APKs under one stable CI signer. [Signing setup](DEBUG_SIGNING.md)

The next M01 step after B004 is to move registry observation and mutations into lifecycle-safe state (B003). Owner-authorized Pixel installation and both tile paths remain tracked separately from Android emulator tests.

[Milestone tracker](TASK_BACKLOG.json) · [Qualification matrix](QUALIFICATION_MATRIX.csv) · [Engineering evidence](EVIDENCE.md)
