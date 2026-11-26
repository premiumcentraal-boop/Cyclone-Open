# Cyclone Open
Independent Android-first cloud-phone launcher, alpha 0.1.0-alpha.1.
## What works in this foundation
- App-private registry: create, select, rename and remove phone configurations; selection survives process restart.
- Image library: import a document, compare its SHA-256 to an owner-supplied digest, retain read access. This is content verification, not publisher certification or bootability.
- Current physical Android environment, OS/ABI health and a link to Android's native user settings.
- Cyclone-inspired glass/Compose design and Quick Settings tile.
## What is not implemented
Local Android VM, remote backend, guest companion, automatic cross-user installation, virtual hardware, persistent guest storage and actual guest switching. Drafts are never advertised as running. The current-user registry is not yet synchronized across Android users.
## Build
JDK 17, Gradle 8.9, Android SDK 35. Run `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. GitHub Actions uploads the debug APK and SHA-256 on successful checks. No release signing key, production publication or phone installation is attempted.
## Upstream
Newest independently located source candidate: Cyclone 5.0.0-alpha.155.dev1, code 309, commit 1ad783823c79960d9cc09935e8f46253c3823dcc. Published alpha.154 is the fallback, alpha.155 publication is unverified/previously reported failed. See docs/UPSTREAM.md. Physical Pixel acceptance is unverified.
