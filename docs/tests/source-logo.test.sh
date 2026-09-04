#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LOGO_PATH="${PROJECT_ROOT}/source/qingke-logo-q-matrix-preview.png"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

[[ -f "${LOGO_PATH}" ]] || fail "missing source logo preview"

metadata="$(sips -g format -g pixelWidth -g pixelHeight -g hasAlpha "${LOGO_PATH}")"
[[ "${metadata}" == *"format: png"* ]] || fail "logo preview must be a PNG"
[[ "${metadata}" == *"pixelWidth: 1672"* ]] || fail "logo preview width must be 1672 pixels"
[[ "${metadata}" == *"pixelHeight: 941"* ]] || fail "logo preview height must be 941 pixels"
[[ "${metadata}" == *"hasAlpha: yes"* ]] || fail "logo preview must preserve its transparent background"

echo "Source logo preview tests passed."
