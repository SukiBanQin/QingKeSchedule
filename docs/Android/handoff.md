# 安卓项目当前交接状态

## 最近更新与阅读入口

更新时间：2026-09-07。本轮职责：维护模型档位与人工审查流程文档。用户已确认文档；P1-01 已有实施提交，本轮未审查应用代码，未授权本窗口修改应用代码。

先读 [根目录规则](../../AGENTS.md)，再按任务阅读 [功能对照](product-baseline.md)、[技术方案](technical-design.md)、[实施计划](implementation-plan.md)。接手时检查 `git status --short`、`git branch --show-current` 和 `git log -5 --oneline`，不能只信文档中的状态。

## 已确认决定

- Android 首版复现当前 iOS 已有功能、业务行为和视觉风格，系统交互按安卓方式实现。
- `Android/` 存放安卓代码，`docs/Android/` 存放本文档，保持大小写一致。
- 官方／中转站各一个 Astra 分析窗口和 Terra 执行窗口，采用人工提示词交接；Luna 按需增加，暂不启用自动委派。
- 分析窗口不修改应用代码；本轮用户明确要求写文档，因此允许文档和相关验证改动。
- 延续每次改动更新测试、验证并创建 Git commit 的仓库要求。

## 建议与待决定事项

- Kotlin + Compose、Room、DataStore 和 Mac 主力开发是已提出的建议；本轮确认 P1 采用 Kotlin + Compose 单 app 工程，Room/DataStore 仍按阶段计划在 P2 接入。
- D01：是否让备份携带教学日历设置并同步扩展 iOS 协议；现有版本 1 不携带这些设置。
- D02：已确认包名 `com.qingke.schedule`、最低 API 26；目标 API 使用实施环境可用的最新稳定版本。目标机型尚未扩大为固定清单。
- D03：精确提醒不可用时的降级方式与文案尚未确定。
- D04：已确认 P1 仅做个人安装验证和可复现 debug 构建，不配置商店签名；正式发布范围仍未确定。
- 用户已确认采用实施计划中的模型档位建议。当前下一步建议 Astra／中审查 P1-01；实际窗口模型和档位未核实。不要替用户变更模型、档位或服务商配置。

## 当前代码与既有改动

初始 iOS 参考提交：`dabdc2eae41143b1288ae5f9ba5eabcebce6de5a`。本次文档提交另从交付消息或 Git 历史查询，不使用自引用提交编号。

开始建档时已有以下未提交文件，本轮保留且不纳入提交：

- `ios/QingKeSchedule.xcodeproj/project.pbxproj`
- `ios/QingKeSchedule.xcodeproj/xcshareddata/xcschemes/QingKeSchedule.xcscheme`

首次实施前重新核对分支及这些改动的最新状态。源码阅读不代表已执行 iOS 构建、模拟器、UI 或真机验收。

## 已完成与未完成

- 已完成：项目可行性分析、工作流程讨论、初始功能对照和技术／实施／交接草案。
- 本轮更新：模型档位建议、任务回传模板、审查后推进规则及对应文档测试。
- 文档已由用户确认，D02 工程参数及 D04 首轮 debug 范围已记录；D01、D03、正式发行和目标机型范围仍待后续决定。A01—A11 尚未完整验收。
- 已发现实施提交 `9b521db`（`feat(android): bootstrap compose domain and schedule contract`），包含工程、领域／解码代码和测试。本轮仅核对提交及文件存在，未完成实现审查或核验执行者测试证据，不认定 P1 已通过。
- 旧 iOS 文档部分内容落后于代码；本轮在新文档标明差异，没有重写历史文档。

## 验证记录

本轮于 2026-09-07 执行以下检查；结果只证明文档与仓库结构验证，不证明应用运行正确：

| 检查 | 状态 | 范围 |
| --- | --- | --- |
| `python3 docs/tests/android-documentation.test.py` | 通过，6 项测试 | 新文档结构、链接、交叉编号及代码基准 |
| `bash docs/tests/documentation.test.sh` | 通过 | 既有文档约束回归 |
| `bash docs/tests/repository-layout.test.sh` | 通过 | 既有仓库布局回归 |
| `git diff --check`、`git diff --cached --check` | 通过 | 原有工作区与本次暂存补丁空白检查 |

iOS／Web 应用构建和安卓应用测试：本轮未运行。本轮只修改文档及其验证；安卓已有工程但执行结果待单独核验，不能据此宣称任一平台的完整应用测试通过。

## P1 实施准备记录（历史）

以下保留进入 P1 前的讨论依据；D02 和首轮 D04 的后续确认见本节末尾，旧建议不是需要重复询问的事项：

- P1 工程建议先采用 Kotlin + Compose + 单 `app` 模块，领域与 JSON 契约先不依赖 Android UI；Room、DataStore 在 P2 再接入。这样首个提交可独立验证构建、日期规则和共享协议。
- D02 建议首轮以当前可获得的 Android Studio/SDK 稳定组合为准，最低版本优先选择 API 26（Android 8.0），目标版本使用安装环境可用的最新稳定 API；包名建议暂用 `com.qingke.schedule`。这些参数影响 Gradle 配置和真机覆盖，需用户确认后锁定。
- D03 不阻塞 P1 领域与协议测试；提醒实现前建议采用“降级为系统允许的非精确提醒，并在设置页明确提示可能延迟”的方案，具体文案留待提醒阶段确认。
- D04 不阻塞 P1；P1 只产出可复现的 debug 构建和测试，不配置商店签名或发布流水线。
- D01 不阻塞 P1；P1 仅兼容现有版本 1 JSON，不修改 iOS、共享 schema 或备份协议。

已收到的 P1 参数确认：

用户已确认：包名 `com.qingke.schedule`、最低 API 26、目标 API 采用实施环境可用的最新稳定版本；P1 仅做个人安装验证（debug/reproducible build），不做商店发布签名。上述决定不等同于 D01、D03 或 D04 的完整产品决定。

## 给 Terra 的第一项实施提示词（历史，不要重复执行）

```text
任务编号与阶段：P1-01，工程与规则基础
角色：执行；仅实施本次范围，不完成整个 App。
建议模型／思考档位：Terra／中（本轮补充建议，不代表此前执行实际设置）。
选择理由：涉及工程、稳定依赖和基础规则测试，需要中档起步。
目标：在 Android/ 建立可构建的 Kotlin Android 工程（Compose、单 app 模块），固定经过验证的稳定依赖；实现与 Android UI 无关的最小领域/JSON 契约骨架，覆盖教学周计算、单双周判断、版本 1 数据解码与业务校验，并接入共享 fixtures。
代码基准／当前分支：基于提交 78e362e；当前分支 codex/ios-ui-redesign-demo。开始前运行 git status --short --branch、git log -5 --oneline，保留两处既有 iOS 工程配置改动，不覆盖、不暂存、不提交它们。
必读文档与参考源码：AGENTS.md；docs/Android/handoff.md；docs/Android/product-baseline.md；docs/Android/technical-design.md；docs/Android/implementation-plan.md；ios/Shared/schedule-data.schema.json；ios/Shared/fixtures/manifest.json 及其 fixtures。
允许修改的路径：Android/**；必要的 Android 测试与构建配置仅限 Android/**。不得修改 ios/**、web/**、共享 schema/fixtures 或 docs/Android/**（交接记录由分析窗口维护）。
已确认决定及不能自行决定的差异：目标是复现 iOS；P1 不扩展 D01 协议。D02 已确认：包名 `com.qingke.schedule`、最低 API 26、目标 API 采用实施环境可用的最新稳定版本；D04 当前仅限个人安装 debug 验证。不得自行扩展 D01、D03 或 D04 的范围。
验收条件：./gradlew assembleDebug 可复现通过；./gradlew test 通过；JVM 测试覆盖教学周边界、单双周、版本/必填字段/颜色/日期时间/课程安排校验、semester:null，以及有效和无效共享 fixtures；未知版本、非法业务数据和超出输入上限按文档拒绝。提供实际命令与输出摘要。
交付：修改摘要、测试文件和结果、构建命令和结果、提交编号、未完成项/限制、下一步建议。只暂存 Android/** 并创建一个独立 Git commit；完成后把提交号和证据回传，并说明交接记录待分析窗口更新。
```

## 下一步

用户回传 Terra 完成情况后，以 `9b521db` 为候选提交核对 P1-01 的完整范围、实际 diff 和构建／测试证据。推荐 Astra／中进行审查；有问题给修正任务，证据不足列待验证项，通过且无阻塞时给下一项提示词，由用户交给 Terra 执行。不要重做历史 P1-01 提示词，不自动跳到 P2，不修改应用代码。

## 后续更新约定

每次阶段结束更新本文件的当前状态，保留决定理由及相关提交；较旧细节可从 Git 查阅，不无限追加聊天。执行者给出提交编号、测试证据、未完成项；审查者记录检查范围和发现。重要决定或切换服务前及时保存，即使任务尚未完成，也如实记录最后操作和未提交内容。
