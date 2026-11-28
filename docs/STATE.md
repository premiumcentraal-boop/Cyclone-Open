# Cyclone Open engineering state

Updated 28 November 2026.

| Milestone | State | Evidence or blocker |
| --- | --- | --- |
| M00 · APK foundation | CI and artifact verified | The debug APK from build #3 matched its archive and APK checksums, source SHA, and debug signature. PR #1 is open. Pixel acceptance awaits an authorized connected device. |
| M01 · Core hardening | CI verified | Android 15 CI verified restart, corruption handling, transaction recovery, activity recreation, and the image-grant contract. Injected `ENOSPC` and `EDQUOT` left committed data unchanged. |
| M02 · Profile interface | In progress | Android 15 CI exercised the glass profile gallery, Aurora's host action, reduced-motion semantics, and image search. Manual TalkBack and font-scale checks remain. |
| M03 · Runtime contract | In progress | The typed switch journal and generation checks pass fixture tests. Both guest adapters report `NOT_IMPLEMENTED`. |
| M05 · Pixel feasibility | Blocked | This workspace has no connection to the owner's Pixel 8. Firmware, AVF access, and guest feasibility need device observations. |
| M06 · Linux reference | Blocked | This workspace is x86_64 without `/dev/kvm`. No compatible authorized Cuttlefish host is available. |
| M14 · Ecosystem | Not run | Applicable Google Play distribution rights, target eligibility, and authorized workflows have no verified evidence. |

The app manages the current host, draft configurations, and imported image references. Both guest runtimes report unavailable. Physical verification, security review, app compatibility, and Google Play licensing each have their own release gates.

Builds #3, #4, and #5 have different debug signers. The repository secret is required to publish APKs under one stable CI signer. [Signing setup](DEBUG_SIGNING.md)

Owner-authorized Pixel installation and both tile paths remain tracked separately from Android emulator tests.

Whole-filesystem exhaustion, sudden power loss, live third-party document-provider behavior, manual TalkBack, and updates signed by the owner's key remain unverified.

## Access needed for first guest boot

- **Pixel 8 track.** The owner's rooted Pixel 8 connected by USB and authorized for ADB on a trusted machine accessible to the operator. Start with read-only Android build, ABI, memory, disk, and AVF checks. Booting a full Android guest also needs a supported Android-capable virtualization interface on that firmware, a matching AOSP image, and authorization to run it with disposable guest data. The supported AVF Linux test is a capability check.
- **Linux reference track.** Shell access on an authorized ARM64 Linux host with usable `/dev/kvm`, sufficient RAM and writable persistent storage for the matching Cuttlefish host packages, AOSP image, and one guest. The operator needs permission to install or use those packages, launch and restart the VM, and reach guest ADB and WebRTC over a private connection. An x86_64 KVM host can qualify an x86_64 reference with its matching images.

[Milestone tracker](TASK_BACKLOG.json) · [Qualification matrix](QUALIFICATION_MATRIX.csv) · [Engineering evidence](EVIDENCE.md)
