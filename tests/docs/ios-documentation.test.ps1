$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$technicalPath = Join-Path $repositoryRoot 'docs\ios-technical-solution.md'
$handoffPath = Join-Path $repositoryRoot 'docs\macbook-codex-handoff.md'

foreach ($path in @($technicalPath, $handoffPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required iOS documentation was not found: $path"
    }
}

$technical = Get-Content -Raw -Encoding UTF8 -LiteralPath $technicalPath
$handoff = Get-Content -Raw -Encoding UTF8 -LiteralPath $handoffPath

$technicalMarkers = @(
    '# 课表软件 iOS MVP 技术方案',
    'SwiftUI',
    'SwiftData',
    'UserNotifications',
    'schemaVersion: 1',
    'src/domain/types.ts',
    'src/domain/rules.ts',
    'schedule-data.schema.json',
    'fileImporter',
    'Swift Testing',
    'Personal Team',
    'xcodebuild',
    '## 16. 完成定义'
)

$handoffMarkers = @(
    '# MacBook Codex iOS 开发接续说明',
    'AGENTS.md',
    'docs/ios-product-design.md',
    'docs/ios-technical-solution.md',
    'git status --short',
    'git remote -v',
    'xcodebuild -version',
    'xcrun simctl list devices available',
    '0b97a6d',
    '阶段一：Xcode 工程与构建循环',
    'scripts/ios-install.sh',
    'Personal Team',
    'GitHub 仓库',
    'Public',
    'https://github.com/SukiBanQin/School_timetable.git',
    '## 11. 给 MacBook Codex 的首次提示词'
)

foreach ($marker in $technicalMarkers) {
    if (-not $technical.Contains($marker)) {
        throw "iOS technical solution is missing required content: $marker"
    }
}

foreach ($marker in $handoffMarkers) {
    if (-not $handoff.Contains($marker)) {
        throw "MacBook handoff is missing required content: $marker"
    }
}

if ([regex]::Matches($technical, '(?m)^# ').Count -ne 1) {
    throw 'iOS technical solution must contain exactly one top-level heading.'
}

if ([regex]::Matches($handoff, '(?m)^# ').Count -ne 1) {
    throw 'MacBook handoff must contain exactly one top-level heading.'
}

if ($technical -notmatch '\[iOS MVP 产品设计文档\]\(\./ios-product-design\.md\)') {
    throw 'iOS technical solution must link to the iOS product design document.'
}

if ($technical -match '(?im)\b(TODO|TBD)\b' -or $handoff -match '(?im)\b(TODO|TBD)\b') {
    throw 'iOS documentation contains unresolved TODO or TBD markers.'
}

Write-Output 'iOS technical solution and MacBook handoff checks passed.'
