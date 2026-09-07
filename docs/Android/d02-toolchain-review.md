# D02 工具链兼容矩阵核查

核查日期：2026-09-07。角色：Terra 执行，仅分析与文档记录；未启动子 Agent，未升级工具链或修改 Android 构建/业务代码。本记录为 Astra 复审输入，不是实施授权。2026-09-08 独立复核及表述修正见本文末尾；原 2026-09-07 现场记录保留。

## 范围、现场与来源等级

- 分支：`codex/ios-ui-redesign-demo`；开始 HEAD：`bcfbe28`。
- 当前工程：AGP 8.8.2、Gradle 8.10.2、JDK 17、Kotlin/Compose plugin 2.0.21、Compose BOM 2024.12.01、`compileSdk`/`targetSdk` 35、`minSdk` 26。
- 当前组合已在有效临时 SDK 下构建、JVM 测试和 API 35 ARM64 模拟器安装/两次冷启动；这只证明当前组合，不能证明 D02 的“实施环境可用的最新稳定版本”。
- 本文把 `sdkmanager` 当作**当前实施环境可用包**证据，把 Android Developers 发布说明当作**官方兼容要求**证据；未在官方兼容矩阵中找到的映射明确标为未验证。

## 稳定渠道实测

查询命令（临时 SDK `/tmp/qingke-android-sdk-1788767128`）：

```bash
"$ANDROID_HOME/cmdline-tools/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --list
```

2026-09-07 输出摘要：可用的非 `rc` Build Tools 包为 35.0.0、35.0.1、36.0.0、36.1.0、37.0.0；平台包为 `platforms;android-35`、`android-36`、`android-36.1`、`android-37.0`、`android-37.1`。同一清单还列出 `rc`/`beta` 包，本文不把它们纳入候选。

“非预览包出现在 SDK stable channel”只说明可安装，**不单独证明** Android 版本的公开发布日期、Google Play target 要求或对应 AGP 已支持该小版本。官方 [SDK Platforms release notes](https://developer.android.com/studio/releases/platforms) 明确记录 Android 16/API 36 revision 1 于 2025-03 在 Platform Stability 后进入 stable channel；本轮没有找到 API 36.1、37.0、37.1 各自的官方发布日期条目。因此它们的 SDK 包可用性是已验证事实，发布日期/产品稳定性仍需 Astra 在选定升级日期再次复核。

## 官方兼容矩阵

| 候选 `compileSdk`/`targetSdk` | 已核实的官方 AGP 上限与发布信息 | 官方 Gradle / Build Tools / JDK | 结论 |
| --- | --- | --- | --- |
| 35（当前） | [AGP 8.8](https://developer.android.com/build/releases/agp-8-8-0-release-notes)（2025-01）最大 API 35 | Gradle 8.10.2、Build Tools 35.0.0、JDK 17 | 已实测可构建和启动，但不是最新 SDK。 |
| 36 | [平台说明](https://developer.android.com/studio/releases/platforms)确认 API 36 stable；AGP 8.8 不支持，需采用官方声明最大 API ≥36 的 AGP | 需要随选定 AGP 重新取官方表；未在本轮把 API 36 与某一“最小 AGP”配对为结论 | 可作为过渡候选，不是当前最高可验证候选。 |
| 36.1 | [AGP 9.0.1](https://developer.android.com/build/releases/agp-9-0-0-release-notes)（2026-01）最大 API 36.1 | Gradle 9.1.0、Build Tools 36.0.0、JDK 17 | 官方完整配对已核实；但落后于可见 API 37 包。 |
| 37.0 | [AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes)（2026-09）最大 API 37 | Gradle 9.6.0、Build Tools 36.0.0、JDK 17 | 当前最高有官方 AGP 上限及完整配对的候选。 |
| 37.1 | SDK 清单可安装；AGP 9.4 官方文字仅称“API level 37”，未明确 `37.1` | 无经核实的对应 AGP/Gradle 映射 | 不可作为本轮“满足 D02”的可执行结论。 |

Build Tools 是 AGP 官方默认/兼容版本，平台 `37.0` 不意味着必须使用 Build Tools 37.0.0；对 API 37，官方 AGP 9.4 表中指定 36.0.0。

## Kotlin 与 Compose 迁移影响

已核实事实：工程根构建文件应用 `org.jetbrains.kotlin.android`、`org.jetbrains.kotlin.plugin.compose` 和 `org.jetbrains.kotlin.plugin.serialization`，版本均为 2.0.21。官方 [Compose compiler 设置](https://developer.android.com/develop/ui/compose/compiler)说明 Kotlin 2.0+ 使用 `org.jetbrains.kotlin.plugin.compose`；因此现有 Compose compiler plugin 模式本身不是升级到 Kotlin 2.x 的障碍。

升级到 AGP 9 必须单独评估 Kotlin：AGP 9.0 发布说明的 built-in Kotlin 迁移章节声明 `org.jetbrains.kotlin.android` 与新 DSL 不兼容。该项目不是 KMP，但仍需在实际升级分支确认 AGP 9 是否启用 built-in Kotlin、如何保留 serialization plugin、`kotlin { compilerOptions }` 和 Compose plugin 的等价配置。不能仅更改版本号后假定可用。Compose BOM 不由 AGP 兼容表锁定：2024.12.01 可先保持，之后按 AndroidX 发布说明和编译测试决定是否升级，不能把 BOM 升级列为 D02 的必然改动。

## 建议：供 D02 选型的已核实候选组合

**建议提交 Astra 审查的目标：API 37.0 + AGP 9.4.0 + Gradle 9.6.0 + Build Tools 36.0.0 + JDK 17，保持 minSdk 26。**

这是本次列举资料能够配对的候选，不是满足 API 37.0 的最低 AGP／Gradle 版本，也不代表 D02 已完成；未推断 37.1 已兼容。建议实际升级时将 `compileSdk` 与 `targetSdk` 一并改为 37；包名、minSdk、D01/D03/D04 范围不变。

必要改动与风险：

- 更新 AGP 与 Gradle Wrapper；重新下载 wrapper、AGP、Kotlin/Compose 和 AndroidX 解析缓存，CI 镜像须提供 JDK 17、Build Tools 36.0.0 与 Platform 37。
- 迁移/验证 AGP 9 built-in Kotlin，不能继续假设现有 `org.jetbrains.kotlin.android` 可与新 DSL 并用；同时验证 serialization 与 Compose compiler plugin。
- 执行 clean/非缓存构建、Debug/Release JVM 测试、ARM64 安装启动和 CI；AGP DSL/API 弃用或第三方依赖元数据可能失败。
- `targetSdk` 37 可能引入 Android 平台行为变更，须在获授权的 P1 工具链升级任务中验证，并在后续功能阶段继续验证；当前骨架启动不代表未来完整应用兼容。

## 未决项与限制

- API 36.1、37.0、37.1 的官方发布日期/稳定产品公告未在本轮逐项定位；升级前重新核查。
- `37.1` 虽可安装，但没有获得官方 AGP 上限或 Gradle 映射，不能据此升级。
- 未在本任务实际改版、构建或运行 AGP 9.4 组合；建议不是已测试结果。
- 未知字段 Android 严格拒绝而 Swift 宽容接受的策略仍待产品决定，与本工具链结论无关。


## 2026-09-08 独立复核与结论边界

复核对象：`55eff97^..55eff97`，实际只新增本文 60 行；该提交未修改测试或其他文件。开始 HEAD 为 `398fa43`、工作区干净。后续 `5613f23`／`398fa43` 是既有复审记录，本轮已阅读，但没有把其“通过”字样代替独立核实。回传的 9 项文档测试属于运行结果，不等于该提交新增了 9 项测试。

**结论：所列 AGP 8.8／9.0／9.4 的官方兼容参数核对通过；修正“最小组合”表述后，本文可作为选型输入。升级可执行性仍未验证，未授权或实施升级，不能据此关闭 D02 或 P1。**

- 本轮直接读取 Android Developers 版本专页；AGP 9.4 兼容表确为最大 API 37、Gradle 9.6.0、Build Tools 36.0.0、JDK 17。已把滚动的 `gradle-plugin` 链接替换为固定版本页，减少以后引用漂移。
- 补充较低版本的官方映射：[AGP 9.1.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes) 明确支持 API 37.0 及以下，Gradle 9.3.1、Build Tools 36.0.0、JDK 17。因此 9.4.0 不是 API 37.0 的最低必需版本。这里也没有穷尽所有 AGP 补丁来证明 9.1.1 就是绝对最低；不擅自将原建议改成用户选定的升级组合。
- [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes) 已支持 API 36.1，配套 Gradle 8.13、Build Tools 35.0.0、JDK 17。原表使用 AGP 9.0.1 是一种有效配对，不能被解释为 API 36.1 必须迁移至 AGP 9。
- 本轮执行 `sdkmanager --list --channel=0`，退出 0。复核了回传列出的平台与 Build Tools，并新增观察到 `platforms;android-37.2`（不带 beta 后缀）。相关行见 [SDK 清单证据节选](evidence/d02-sdk-list-20260908.txt)。本轮仍未核实 37.1／37.2 的官方 AGP 映射；不据可安装性宣布它们已满足 D02。新查询不倒推 2026-09-07 清单错误。
- Kotlin／Compose 迁移风险记录方向正确；[Compose compiler 官方设置](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler) 将 Compose compiler plugin 与 Kotlin 版本关联，选定 built-in Kotlin 版本后必须配对验证 compose 与 serialization 插件。AGP 兼容表不能替代本项目插件编译实测。
- 本轮未定位齐所有小版本公开发布日期／产品稳定公告，未解析下载 AGP 9.4／Gradle 9.6.0 构件，未运行其升级构建或 CI；没有将这些未验证项写成通过。平台行为验证应纳入获授权的 P1 升级任务，不能推迟到 P1/P2 之外才检查。

下一步：工具链升级仍需另立任务并取得授权；无须为已修正的文档措辞再派发 Terra 文档任务。当前另有 JSON 数字语法问题须先修，见 [最新应用补充审查](p1-01-review.md)。该修正不依赖未知字段策略或工具链升级决定。
