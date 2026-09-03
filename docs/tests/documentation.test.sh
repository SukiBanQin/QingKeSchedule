#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
IOS_TECHNICAL="${REPOSITORY_ROOT}/docs/ios-technical-solution.md"
HANDOFF="${REPOSITORY_ROOT}/docs/macbook-codex-handoff.md"
WEB_TECHNICAL="${REPOSITORY_ROOT}/docs/technical-solution.md"
WEB_PRODUCT="${REPOSITORY_ROOT}/docs/product-design.md"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

require_marker() {
    local path="$1"
    local marker="$2"

    grep -F -- "${marker}" "${path}" >/dev/null \
        || fail "$(basename "${path}") is missing required content: ${marker}"
}

for path in "${IOS_TECHNICAL}" "${HANDOFF}" "${WEB_TECHNICAL}" "${WEB_PRODUCT}"; do
    [[ -f "${path}" ]] || fail "required documentation was not found: ${path}"
    [[ "$(grep -c '^# ' "${path}")" -eq 1 ]] \
        || fail "$(basename "${path}") must contain exactly one top-level heading"
done

for marker in \
    '# 课表软件 iOS MVP 技术方案' \
    'SwiftUI' \
    'SwiftData' \
    'UserNotifications' \
    'schemaVersion: 1' \
    'ios/Shared/schedule-data.schema.json' \
    'ios/QingKeSchedule/Domain/ScheduleRules.swift' \
    'fileImporter' \
    'Swift Testing' \
    'Personal Team' \
    'xcodebuild' \
    'pre-ios-web-restructure' \
    '## 16. 完成定义'; do
    require_marker "${IOS_TECHNICAL}" "${marker}"
done

for marker in \
    '# MacBook Codex iOS 开发接续说明' \
    'AGENTS.md' \
    'docs/ios-product-design.md' \
    'git status --short' \
    'xcodebuild -version' \
    'xcrun simctl list devices available' \
    '0b97a6d' \
    'pre-ios-web-restructure' \
    '阶段一：Xcode 工程与构建循环' \
    'ios/scripts/ios-install.sh' \
    'Personal Team' \
    'https://github.com/SukiBanQin/School_timetable.git' \
    '## 11. 给 MacBook Codex 的后续提示词'; do
    require_marker "${HANDOFF}" "${marker}"
done

for marker in \
    '# 课表软件 Web MVP 技术方案' \
    '## 2. 方案结论' \
    '## 4. 总体架构' \
    '## 6. 核心数据模型' \
    '## 7. 核心业务规则约定' \
    '## 10. 本地存储方案' \
    '## 11. 测试与质量保障' \
    '## 13. 后续演进路径' \
    'Vue 3' \
    'TypeScript' \
    'Vite' \
    'localStorage' \
    'Vitest' \
    'Playwright' \
    'schemaVersion' \
    'pre-ios-web-restructure' \
    'web/'; do
    require_marker "${WEB_TECHNICAL}" "${marker}"
done

require_marker "${IOS_TECHNICAL}" '[iOS MVP 产品设计文档](./ios-product-design.md)'
require_marker "${WEB_TECHNICAL}" '[产品设计文档](./product-design.md)'
require_marker "${WEB_PRODUCT}" 'pre-ios-web-restructure'

if grep -Eiq '\b(TODO|TBD)\b' "${IOS_TECHNICAL}" "${HANDOFF}"; then
    fail "iOS documentation contains unresolved TODO or TBD markers"
fi

echo "Documentation tests passed."
