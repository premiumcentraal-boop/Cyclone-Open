# CI debug signing

Every published CI APK for `com.cyclone.open` uses the Cyclone Open debug certificate from the repository secret. The workflow restores the keystore into the runner's temporary directory and deletes it before the jobs finish. The downloadable ZIP includes the APK, `SHA256SUMS`, `SOURCE_SHA`, and `APK_SIGNER_SHA256`.

## One-time setup

Back up the keystore in owner-controlled storage. Use the same file for every run and keep it outside Git. To start a new Cyclone Open debug signing identity, generate a keystore once in a private directory.

```sh
umask 077
keytool -genkeypair -keystore cyclone-open-debug.jks -storetype JKS \
  -alias androiddebugkey -storepass android -keypass android \
  -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Cyclone Open CI Debug'
```

While authenticated as a repository administrator, pipe the encoded file straight to the repository Actions secret.

```sh
base64 < cyclone-open-debug.jks | tr -d '\n' | \
  gh secret set CYCLONE_OPEN_DEBUG_KEYSTORE_B64 --repo premiumcentraal-boop/Cyclone-Open
```

The standard debug alias and passwords are part of the build configuration. Protect the keystore bytes and their Base64 encoding as the signing credential. Fork PRs and runs without the secret can still run checks using disposable test APKs inside CI. APK publication is disabled for those runs. An invalid configured secret fails the workflow.

## Verify update compatibility

Check `APK_SIGNER_SHA256` in two successive CI artifacts, then verify both APKs with `apksigner verify --print-certs`. The signer must match across artifacts. To test an update, use an authorized emulator or test handset. Install the first APK, create persistent test data, update with `adb install -r`, and check that the data survives.

Existing artifacts signed before this setup may have different certificates. Android rejects those as in-place updates across signing identities. Keep installed data and use an approved backup or migration procedure if an existing installation needs to cross that boundary. This key is for Cyclone Open debug CI only.
