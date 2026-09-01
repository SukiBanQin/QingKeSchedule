$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$documentPath = Join-Path $repositoryRoot 'docs\technical-solution.md'

if (-not (Test-Path -LiteralPath $documentPath -PathType Leaf)) {
    throw "Technical solution document was not found: $documentPath"
}

$content = Get-Content -Raw -Encoding UTF8 -LiteralPath $documentPath

$requiredHeadings = @(
    '# 课表软件 Web MVP 技术方案',
    '## 2. 方案结论',
    '## 4. 总体架构',
    '## 6. 核心数据模型',
    '## 7. 核心业务规则约定',
    '## 10. 本地存储方案',
    '## 11. 测试与质量保障',
    '## 13. 后续演进路径'
)

$requiredDecisions = @(
    'Vue 3',
    'TypeScript',
    'Vite',
    'CSS Grid',
    'localStorage',
    'Vitest',
    'Playwright',
    'schemaVersion'
)

foreach ($heading in $requiredHeadings) {
    if (-not $content.Contains($heading)) {
        throw "Required section is missing: $heading"
    }
}

foreach ($decision in $requiredDecisions) {
    if (-not $content.Contains($decision)) {
        throw "Required technical decision is missing: $decision"
    }
}

$topLevelHeadingCount = [regex]::Matches($content, '(?m)^# ').Count
if ($topLevelHeadingCount -ne 1) {
    throw "Expected exactly one top-level heading, found $topLevelHeadingCount"
}

if ($content -notmatch '\[产品设计文档\]\(\./product-design\.md\)') {
    throw 'Technical solution must link to the product design document.'
}

Write-Output 'Technical solution document checks passed.'
