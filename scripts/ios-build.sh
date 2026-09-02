#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_PATH="${REPOSITORY_ROOT}/ios/QingKeSchedule.xcodeproj"
DERIVED_DATA_PATH="${REPOSITORY_ROOT}/.build/ios"

discover_iphone_simulator() {
    xcrun simctl list devices available | awk '
        /^-- iOS / { in_ios = 1; next }
        /^-- / { in_ios = 0 }
        in_ios && !found && /iPhone/ {
            count = split($0, fields, /[()]/)
            for (field_index = 2; field_index <= count; field_index += 2) {
                if (length(fields[field_index]) == 36 && fields[field_index] ~ /^[0-9A-Fa-f-]+$/) {
                    print fields[field_index]
                    found = 1
                }
            }
        }
    '
}

SIMULATOR_UDID="$(discover_iphone_simulator)"
if [[ -z "${SIMULATOR_UDID}" ]]; then
    echo "No available iPhone Simulator was found. Install an iOS Simulator runtime in Xcode." >&2
    exit 1
fi

echo "Building QingKeSchedule with an automatically discovered iPhone Simulator."
xcodebuild build \
    -project "${PROJECT_PATH}" \
    -scheme QingKeSchedule \
    -destination "platform=iOS Simulator,id=${SIMULATOR_UDID}" \
    -derivedDataPath "${DERIVED_DATA_PATH}"
