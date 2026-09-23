#!/usr/bin/env bash

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
KEYSTORE="${QINGKE_RELEASE_KEYSTORE:-${HOME}/Library/Application Support/QingKeSchedule/AndroidRelease/release-key.p12}"
KEY_ALIAS="qingke-release"
KEYCHAIN_SERVICE="QingKeSchedule-Android-Release-v1"
KEYCHAIN_ACCOUNT="qingke-release"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/Library/Android/sdk-qingke-api37}}"
APKSIGNER="${SDK_ROOT}/build-tools/36.0.0/apksigner"
AAPT="${SDK_ROOT}/build-tools/36.0.0/aapt"
KEYCHAIN_READER="${SCRIPT_DIR}/read-release-password.swift"

if [[ ! -f "${KEYSTORE}" ]]; then
    echo "Release keystore is missing from the configured local key path." >&2
    exit 1
fi
if [[ ! -x "${APKSIGNER}" ]]; then
    echo "Android SDK Build Tools 36.0.0 apksigner was not found." >&2
    exit 1
fi
if [[ ! -x "${AAPT}" ]]; then
    echo "Android SDK Build Tools 36.0.0 aapt was not found." >&2
    exit 1
fi
if [[ ! -f "${KEYCHAIN_READER}" ]]; then
    echo "The local Keychain reader was not found." >&2
    exit 1
fi

release_password="$(/usr/bin/swift "${KEYCHAIN_READER}" \
    "${KEYCHAIN_SERVICE}" "${KEYCHAIN_ACCOUNT}")"
if [[ -z "${release_password}" ]]; then
    echo "The release password was not found in the macOS login Keychain." >&2
    exit 1
fi

export QINGKE_RELEASE_KEYSTORE="${KEYSTORE}"
export QINGKE_RELEASE_STORE_PASSWORD="${release_password}"
export QINGKE_RELEASE_KEY_ALIAS="${KEY_ALIAS}"
export QINGKE_RELEASE_KEY_PASSWORD="${release_password}"
unset release_password

cd "${ANDROID_ROOT}"
./gradlew :app:assembleRelease --no-daemon --console=plain

apk_path="${ANDROID_ROOT}/app/build/outputs/apk/release/app-release.apk"
release_asset_dir="${ANDROID_ROOT}/release-assets"
if [[ ! -f "${apk_path}" ]]; then
    echo "The release APK was not produced." >&2
    exit 1
fi
version_name="$("${AAPT}" dump badging "${apk_path}" \
    | sed -n "s/^package:.*versionName='\\([^']*\\)'.*/\\1/p")"
if [[ ! "${version_name}" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "The release APK has a missing or unsafe versionName." >&2
    exit 1
fi
release_apk="${release_asset_dir}/QingKeSchedule-${version_name}.apk"
mkdir -p "${release_asset_dir}"
cp "${apk_path}" "${release_apk}"
chmod 644 "${release_apk}"
"${APKSIGNER}" verify --verbose "${release_apk}"
shasum -a 256 "${release_apk}"
