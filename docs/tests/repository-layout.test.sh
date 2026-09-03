#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

[[ -d "${REPOSITORY_ROOT}/ios" ]] || fail "ios/ must contain the iOS app"
[[ -d "${REPOSITORY_ROOT}/web" ]] || fail "web/ must be reserved for the future Web app"
[[ -f "${REPOSITORY_ROOT}/web/.gitkeep" ]] || fail "web/.gitkeep must preserve the empty directory"

unexpected_web_file="$(find "${REPOSITORY_ROOT}/web" -mindepth 1 ! -name '.gitkeep' -print -quit)"
[[ -z "${unexpected_web_file}" ]] || fail "web/ must remain empty; found ${unexpected_web_file}"

for legacy_path in \
    .openai/hosting.json \
    package.json \
    package-lock.json \
    public \
    scripts \
    shared \
    src \
    tests \
    worker; do
    [[ ! -e "${REPOSITORY_ROOT}/${legacy_path}" ]] \
        || fail "legacy root path must be removed: ${legacy_path}"
done

for required_path in \
    ios/QingKeSchedule.xcodeproj \
    ios/QingKeSchedule \
    ios/QingKeScheduleTests \
    ios/QingKeScheduleUITests \
    ios/Shared/schedule-data.schema.json \
    ios/Shared/fixtures/manifest.json \
    ios/scripts/ios-build.sh \
    ios/scripts/ios-test.sh \
    ios/scripts/ios-install.sh; do
    [[ -e "${REPOSITORY_ROOT}/${required_path}" ]] \
        || fail "required iOS path is missing: ${required_path}"
done

echo "Repository layout tests passed."
