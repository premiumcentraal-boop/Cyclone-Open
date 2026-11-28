#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${CYCLONE_OPEN_DEBUG_KEYSTORE_B64:-}" ]]; then
  echo '::error::Set the repository Actions secret CYCLONE_OPEN_DEBUG_KEYSTORE_B64 before publishing a debug APK.' >&2
  exit 1
fi

umask 077
keystore=$(mktemp "${RUNNER_TEMP:?}/cyclone-open-debug.XXXXXX.jks")
trap 'rm -f -- "$keystore"' EXIT
printf '%s' "$CYCLONE_OPEN_DEBUG_KEYSTORE_B64" | base64 --decode > "$keystore"
keytool -list -storetype JKS -keystore "$keystore" -storepass android -alias androiddebugkey >/dev/null
printf 'CYCLONE_OPEN_CI_KEYSTORE=%s\n' "$keystore" >> "${GITHUB_ENV:?}"
trap - EXIT
