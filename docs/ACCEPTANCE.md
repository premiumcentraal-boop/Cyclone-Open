# Release gates
1. CI Gradle unit tests, lint, debug APK build, SHA-256 and artifact. Match APK to exact commit.
2. On an authorized Pixel 8: install separately from Cyclone, launch, verify system model/version; import known sample with matching digest; wrong digest fails; create two drafts; select and rename; restart/kill and verify selected data; remove with confirmation; image removal while referenced fails; verify tile returns to app. Inspect logcat for crashes.
3. No real local guest claims until an actual full Android 13+ OS boots, has separate data, graphical display, input and tested hardware integration. No trust/certification claims from a virtual guest or rooted host.
4. No production release signing or cross-user modifications until physical acceptance.
