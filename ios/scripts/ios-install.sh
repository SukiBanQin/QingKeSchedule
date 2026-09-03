#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_PATH="${IOS_PROJECT_PATH:-${IOS_ROOT}/QingKeSchedule.xcodeproj}"
DERIVED_DATA_PATH="${IOS_DERIVED_DATA_PATH:-${IOS_ROOT}/.build/ios-device}"
SCHEME="${IOS_SCHEME:-QingKeSchedule}"
APP_PATH="${DERIVED_DATA_PATH}/Build/Products/Debug-iphoneos/QingKeSchedule.app"

XCRUN_BIN="${XCRUN_BIN:-xcrun}"
XCODEBUILD_BIN="${XCODEBUILD_BIN:-xcodebuild}"
PLUTIL_BIN="${PLUTIL_BIN:-/usr/bin/plutil}"
PLISTBUDDY_BIN="${PLISTBUDDY_BIN:-/usr/libexec/PlistBuddy}"

discover_connected_iphone() {
    local device_json
    local device_identifier

    if ! device_json="$(
        "${XCRUN_BIN}" devicectl list devices \
            --filter 'hardwareProperties.platform == "iOS" AND hardwareProperties.deviceType == "iPhone" AND deviceProperties.bootState == "booted" AND connectionProperties.pairingState == "paired" AND connectionProperties.tunnelState == "connected"' \
            --json-output /dev/stdout \
            --quiet 2>/dev/null
    )"; then
        echo "Unable to query connected iPhones. Open Xcode once and verify its command-line tools are selected." >&2
        return 1
    fi

    if ! device_identifier="$(
        printf '%s' "${device_json}" \
            | "${PLUTIL_BIN}" -extract result.devices.0.identifier raw -o - - 2>/dev/null
    )"; then
        echo "No paired, booted, connected iPhone was found. Unlock the iPhone and check Xcode's Devices and Simulators window." >&2
        return 1
    fi

    if printf '%s' "${device_json}" \
        | "${PLUTIL_BIN}" -extract result.devices.1.identifier raw -o - - >/dev/null 2>&1; then
        echo "More than one connected iPhone was found. Disconnect the other devices and run the script again." >&2
        return 1
    fi

    printf '%s' "${device_identifier}"
}

main() {
    local device_identifier
    local bundle_identifier

    if [[ ! -d "${PROJECT_PATH}" ]]; then
        echo "Xcode project not found at ${PROJECT_PATH}." >&2
        return 1
    fi

    device_identifier="$(discover_connected_iphone)" || return 1

    echo "Building QingKeSchedule for the connected iPhone with Automatic Signing."
    if ! "${XCODEBUILD_BIN}" build \
        -project "${PROJECT_PATH}" \
        -scheme "${SCHEME}" \
        -configuration Debug \
        -destination "platform=iOS,id=${device_identifier}" \
        -derivedDataPath "${DERIVED_DATA_PATH}" \
        -allowProvisioningUpdates \
        -quiet; then
        echo "The device build or signing step failed. Reopen Signing & Capabilities in Xcode and confirm the same Personal Team and Bundle Identifier still work." >&2
        return 1
    fi

    if [[ ! -d "${APP_PATH}" ]]; then
        echo "The build completed, but the expected app bundle was not found at ${APP_PATH}." >&2
        return 1
    fi

    if ! bundle_identifier="$(
        "${PLISTBUDDY_BIN}" -c 'Print :CFBundleIdentifier' "${APP_PATH}/Info.plist" 2>/dev/null
    )" || [[ -z "${bundle_identifier}" ]]; then
        echo "Unable to read the Bundle Identifier from the built app." >&2
        return 1
    fi

    echo "Installing the signed app without deleting the existing app or its local data."
    if ! "${XCRUN_BIN}" devicectl device install app \
        --device "${device_identifier}" \
        --quiet \
        "${APP_PATH}"; then
        echo "Installation failed. Keep the existing app installed; unlock the iPhone and verify its trust and Developer Mode settings." >&2
        return 1
    fi

    echo "Launching QingKeSchedule on the connected iPhone."
    if ! "${XCRUN_BIN}" devicectl device process launch \
        --device "${device_identifier}" \
        --terminate-existing \
        --quiet \
        "${bundle_identifier}"; then
        echo "The app was installed but could not be launched. Unlock the iPhone and launch it manually once." >&2
        return 1
    fi

    echo "QingKeSchedule was built, installed over the existing app, and launched successfully."
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
