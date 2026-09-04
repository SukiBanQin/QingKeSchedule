#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

[[ -d "${REPOSITORY_ROOT}/ios" ]] || fail "ios/ must contain the iOS app"
[[ -d "${REPOSITORY_ROOT}/web" ]] || fail "web/ must contain the iPhone UI prototype"

for required_web_path in \
    web/.openai/hosting.json \
    web/app/globals.css \
    web/app/layout.tsx \
    web/app/page.tsx \
    web/package.json \
    web/public/qingke-logo-lockup.png \
    web/tests/rendered-html.test.mjs; do
    [[ -f "${REPOSITORY_ROOT}/${required_web_path}" ]] \
        || fail "required Web prototype path is missing: ${required_web_path}"
done

[[ ! -e "${REPOSITORY_ROOT}/web/archive/p3r" ]] \
    || fail "the retired P3R concept must not remain in the active Web prototype"

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
