#!/usr/bin/env bash
set -euo pipefail

android_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk_path="${android_dir}/app/build/outputs/apk/debug/app-debug.apk"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

if [[ ! -f "${apk_path}" ]]; then
  echo "debug APK not found: ${apk_path}" >&2
  exit 1
fi
if [[ -z "${sdk_root}" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT is required" >&2
  exit 1
fi

aapt2="${sdk_root}/build-tools/35.0.0/aapt2"
if [[ ! -x "${aapt2}" ]]; then
  echo "aapt2 not found: ${aapt2}" >&2
  exit 1
fi

badging="$(${aapt2} dump badging "${apk_path}")"
grep -F "package: name='com.qingke.schedule'" <<<"${badging}" >/dev/null
grep -F "minSdkVersion:'26'" <<<"${badging}" >/dev/null
grep -F "targetSdkVersion:'35'" <<<"${badging}" >/dev/null
echo "APK metadata check passed: com.qingke.schedule, minSdk 26, targetSdk 35"
