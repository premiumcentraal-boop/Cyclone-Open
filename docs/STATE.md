# Cyclone Open engineering state

Updated 28 November 2026.

| Milestone | State | Evidence or blocker |
| --- | --- | --- |
| M00 · APK foundation | CI and artifact verified | The debug APK from build #3 matched its archive and APK checksums, source SHA, and debug signature. PR #1 is open. Pixel acceptance awaits an authorized connected device. |
| M01 · Core hardening | In progress | B004 passed seven Android 15 emulator tests for store reopen, corrupt data, interrupted staging, write failure, and concurrent updates. Full process death and disk exhaustion still need testing. |
| M05 · Pixel feasibility | Blocked | This workspace has no connection to the owner's Pixel 8. Firmware, AVF access, and guest feasibility need device observations. |
| M06 · Linux reference | Blocked | This workspace is x86_64 without `/dev/kvm`. No compatible authorized Cuttlefish host is available. |
| M14 · Ecosystem | Not run | Applicable Google Play distribution rights, target eligibility, and authorized workflows have no verified evidence. |

The app manages the current host, draft configurations, and imported image references. Both guest runtimes report unavailable. Physical verification, security review, app compatibility, and Google Play licensing each have their own release gates.

Builds #3, #4, and #5 have different debug signers. Update-compatible signing remains a release gate for installations with existing data.

The next M01 step after B004 is to move registry observation and mutations into lifecycle-safe state (B003). Owner-authorized Pixel installation and both tile paths remain tracked separately from Android emulator tests.

[Milestone tracker](TASK_BACKLOG.json) · [Qualification matrix](QUALIFICATION_MATRIX.csv) · [Engineering evidence](EVIDENCE.md)
