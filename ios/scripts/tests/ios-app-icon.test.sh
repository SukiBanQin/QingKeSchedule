#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ICON_SET="${IOS_ROOT}/QingKeSchedule/Assets.xcassets/AppIcon.appiconset"
ICON_PATH="${ICON_SET}/AppIcon-1024.png"
PROJECT_PATH="${IOS_ROOT}/QingKeSchedule.xcodeproj/project.pbxproj"
SOURCE_ICON="${IOS_ROOT}/../source/cover.png"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

[[ -f "${ICON_PATH}" ]] || fail "missing AppIcon-1024.png"
[[ -f "${SOURCE_ICON}" ]] || fail "missing source/cover.png"

metadata="$(sips -g pixelWidth -g pixelHeight -g hasAlpha -g space "${ICON_PATH}")"
[[ "${metadata}" == *"pixelWidth: 1024"* ]] || fail "app icon width must be 1024 pixels"
[[ "${metadata}" == *"pixelHeight: 1024"* ]] || fail "app icon height must be 1024 pixels"
[[ "${metadata}" == *"hasAlpha: no"* ]] || fail "app icon must have an opaque background"
[[ "${metadata}" == *"space: RGB"* ]] || fail "app icon must use an RGB color space"

source_metadata="$(sips -g pixelWidth -g pixelHeight -g hasAlpha -g space "${SOURCE_ICON}")"
[[ "${source_metadata}" == *"pixelWidth: 1254"* ]] || fail "cover source width must be 1254 pixels"
[[ "${source_metadata}" == *"pixelHeight: 1254"* ]] || fail "cover source height must be 1254 pixels"
[[ "${source_metadata}" == *"hasAlpha: no"* ]] || fail "cover source must have an opaque background"
[[ "${source_metadata}" == *"space: RGB"* ]] || fail "cover source must use an RGB color space"

comparison_dir="$(mktemp -d)"
trap 'rm -rf "${comparison_dir}"' EXIT
sips -z 1024 1024 "${SOURCE_ICON}" --out "${comparison_dir}/AppIcon-1024.png" >/dev/null
cmp -s "${ICON_PATH}" "${comparison_dir}/AppIcon-1024.png" \
    || fail "AppIcon-1024.png must be generated from source/cover.png"

plutil -convert xml1 -o /dev/null -- \
    "${IOS_ROOT}/QingKeSchedule/Assets.xcassets/Contents.json"
plutil -convert xml1 -o /dev/null -- "${ICON_SET}/Contents.json"

grep -F '"filename" : "AppIcon-1024.png"' "${ICON_SET}/Contents.json" >/dev/null \
    || fail "asset catalog must reference AppIcon-1024.png"
grep -F 'ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;' "${PROJECT_PATH}" >/dev/null \
    || fail "iOS target must select the AppIcon asset"
grep -F 'Assets.xcassets in Resources' "${PROJECT_PATH}" >/dev/null \
    || fail "asset catalog must be included in the app resources"

echo "iOS app icon tests passed."
