#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TEST_ROOT="${REPO_ROOT}/.build/ios-install-script-tests"
CALL_LOG="${TEST_ROOT}/calls.log"

IOS_PROJECT_PATH="${TEST_ROOT}/QingKeSchedule.xcodeproj"
IOS_DERIVED_DATA_PATH="${TEST_ROOT}/derived-data"
XCRUN_BIN="fake_xcrun"
XCODEBUILD_BIN="fake_xcodebuild"
PLISTBUDDY_BIN="fake_plistbuddy"

mkdir -p "${IOS_PROJECT_PATH}" "${IOS_DERIVED_DATA_PATH}"
: > "${CALL_LOG}"

# shellcheck source=../ios-install.sh
source "${REPO_ROOT}/scripts/ios-install.sh"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

assert_equals() {
    local expected="$1"
    local actual="$2"
    local description="$3"

    if [[ "${actual}" != "${expected}" ]]; then
        fail "${description}: expected '${expected}', got '${actual}'"
    fi
}

assert_log_contains() {
    local expected="$1"

    if ! grep -F -- "${expected}" "${CALL_LOG}" >/dev/null; then
        fail "call log did not contain '${expected}'"
    fi
}

fake_device_json='{"result":{"devices":[]}}'

fake_xcrun() {
    if [[ "$1" == "devicectl" && "$2" == "list" && "$3" == "devices" ]]; then
        [[ "$*" == *"--json-output /dev/stdout"* ]] \
            || fail "device discovery must consume devicectl JSON output"
        [[ "$*" == *"--filter"* ]] \
            || fail "device discovery must filter for a connected iPhone"
        printf '%s' "${fake_device_json}"
        return 0
    fi

    printf 'xcrun' >> "${CALL_LOG}"
    printf ' <%s>' "$@" >> "${CALL_LOG}"
    printf '\n' >> "${CALL_LOG}"
}

fake_xcodebuild() {
    printf 'xcodebuild' >> "${CALL_LOG}"
    printf ' <%s>' "$@" >> "${CALL_LOG}"
    printf '\n' >> "${CALL_LOG}"

    mkdir -p "${APP_PATH}"
    "${PLUTIL_BIN}" -create xml1 "${APP_PATH}/Info.plist"
}

fake_plistbuddy() {
    printf '%s' 'test.local.QingKeSchedule'
}

fake_device_json='{"result":{"devices":[{"identifier":"fixture-device"}]}}'
assert_equals "fixture-device" "$(discover_connected_iphone)" "single-device discovery"

fake_device_json='{"result":{"devices":[]}}'
if no_device_output="$(discover_connected_iphone 2>&1)"; then
    fail "device discovery should fail when no iPhone is connected"
fi
[[ "${no_device_output}" == *"No paired, booted, connected iPhone"* ]] \
    || fail "no-device failure should explain how to reconnect"

fake_device_json='{"result":{"devices":[{"identifier":"fixture-one"},{"identifier":"fixture-two"}]}}'
if multiple_device_output="$(discover_connected_iphone 2>&1)"; then
    fail "device discovery should fail when multiple iPhones are connected"
fi
[[ "${multiple_device_output}" == *"More than one connected iPhone"* ]] \
    || fail "multiple-device failure should ask the user to disambiguate"

fake_device_json='{"result":{"devices":[{"identifier":"fixture-device"}]}}'
main >/dev/null

assert_log_contains "xcodebuild <build>"
assert_log_contains "<-destination> <platform=iOS,id=fixture-device>"
assert_log_contains "<-allowProvisioningUpdates>"
assert_log_contains "<-quiet>"
assert_log_contains "xcrun <devicectl> <device> <install> <app> <--device> <fixture-device>"
assert_log_contains "xcrun <devicectl> <device> <process> <launch> <--device> <fixture-device>"
assert_log_contains "<--terminate-existing> <--quiet> <test.local.QingKeSchedule>"

echo "ios-install.sh tests passed."
