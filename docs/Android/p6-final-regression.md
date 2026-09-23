# P6-02 最终回归与证据索引（2026-09-23）

状态：P6-02 全量回归和 A01—A11 证据整理已完成，并通过 [GPT6 SOL 独立复审](p6-final-regression-review.md)；P6 阶段仍待用户确认。D04（个人安装／分发／正式发布范围）继续单独决定。

## 本轮基准与回归结果

- 代码基准：`Android` 分支，起点 `5700d42197e8a53391e4b267371a50ff8a6833bb`，与 `origin/Android` 一致。起始工作区只有用户未跟踪 `.vscode/`，保留且未纳入本任务。
- 本轮只修改 `docs/Android/` 文档，不改 Android、iOS、Web、共享 schema／fixtures 或发布配置。
- 主机：JDK 17.0.20.1；设备：`emulator-5554`，API 37，`arm64-v8a`。
- Debug JVM：283 tests；Release JVM：283 tests；API 37 ARM64 `connectedDebugAndroidTest`：202 tests。三组均为 0 failures、0 errors、0 skipped。
- `assembleDebug`、`assembleRelease`、`assembleDebugAndroidTest` 与 `lintDebug` 均成功。Lint 报告 24 warnings、0 errors；强制重跑 Debug 编译时另输出 `statusBarColor` 和 `navigationBarColor` 两条 Java deprecated 编译警告。
- 仪器测试运行期间控制台出现一次 `[EmulatorConsole]: Emulator console auth failed`；ADB 仍返回设备状态 `device`，设备启动完成，仪器测试全部完成且无失败。
- 首次合并 Gradle 调用把 Debug JVM 标为 `UP-TO-DATE`；随后单独以 `--rerun-tasks` 强制执行 Debug JVM，测试 XML 记录本轮 283 项结果。Release JVM 在首次合并调用中实际执行。

命令与原始测试报告路径见[本轮回归证据记录](evidence/p6-final-regression-20260923.txt)。当前工作区生成的 Gradle XML 和 lint HTML/SARIF 报告位于该记录所列的 `Android/app/build/` 路径；它们是构建产物，不纳入文档提交。

## A01—A11 证据矩阵

| 编号 | 实现与技术复审 | 用户验收 | 设备／基准证据 | 结论与保留限制 |
| --- | --- | --- | --- | --- |
| A01 首次设置与路由 | 首次设置、保存后主壳、重启恢复及导入路由均已实现并通过技术复审 | 2026-09-21，与 A02／A09 一并确认通过 | [P6 预审](p6-pre-audit.md#p6-01-集中用户验收2026-09-21)、[A10 API 37 文件面板证据](evidence/p4-a10-json-transfer/README.md) | 通过；Android 使用系统 `OpenDocument`，作为 iOS 文件导入流程的平台对应方式 |
| A02 今日课表 | 日期／时区、排序、课程状态、教学周与空状态逻辑已实现并复审；同状态 Android／iOS 视觉对照已审 | 2026-09-21 集中确认通过 | [P6 预审](p6-pre-audit.md#a02-核对)、[今日页实施记录](p3-03-today-schedule.md)、[A02 视觉证据](evidence/p3-03-r2-visual-alignment-20260914.txt) | 通过；本轮复用已审证据，没有重新生成或声称新截图 |
| A03 周课表 | 周课表与视觉修正已实现并通过技术复审 | 已确认通过 | [周课表设备证据](evidence/p3-05-visual-r2/README.md) | 通过；不另增实现任务 |
| A04 课程新增／编辑／删除 | 编辑器、校验、草稿保留和修正均已实现并通过技术复审 | 已确认通过 | [课程编辑记录](p3-04-course-editor.md)、[课程编辑设备记录](evidence/p3-04-course-editor-20260914.txt) | 通过 |
| A05 冲突、放弃与删除确认 | 课程冲突处理、确认与返回行为已实现并通过技术复审 | 已确认通过 | [P3-04／R8 错误与确认记录](p3-04-r8-course-save-error-dialog.md)、[对话框设备证据](evidence/p3-04-r8-course-save-error-dialog/README.md) | 通过 |
| A06 学期与节次设置 | 学期编辑、节次及级联保存（含 R7／R1／R2）已实现并通过技术复审 | 已确认通过 | [级联保存修正记录](p3-06-r7-settings-save-cascade.md)、[R7 证据](evidence/p3-06-r7-semester-cascade/README.md) | 通过 |
| A07 教学日历与午休 | 日历例外、午休及冲突处理已实现并通过技术复审 | 已确认通过 | [R1 审查记录](p3-07-r1-review.md)、[日历设备证据](evidence/p3-07-a07-calendar/README.md) | 通过 |
| A08 上课提醒 | 三批提醒实现均通过 Sol 独立技术复审 | 用户于 2026-09-23 确认已观察到的真机体验通过；只代表当前观察范围 | [小米 10 设备证据](evidence/p6-a08-mi10/README.md)、[提醒批次 1／2／3 记录](p3-08-a08-reminders-batch1.md) | 在小米上需要青课“自启动”开启、省电策略“无限制”，通知类别允许悬浮／震动／锁屏完整显示。自然长时待机因手机没电而无结果，用户决定不重测；真实系统 UI 手动拒绝权限后的完整路径、非精确能力长期待机、其他厂商设备未验证。小米默认推荐省电策略曾阻滞后台闹钟。用户称已自行清理测试课程；设备未连接，未独立核对课程、节次或闹钟，不自动改动设备 |
| A09 本地持久化 | Room、联合状态、生产 CRUD 与导入替换事务均通过技术复审 | 2026-09-21，与 A01／A02 一并确认通过 | [P6 预审](p6-pre-audit.md#a09-核对)、[持久化实现](p2-01-persistence-state.md)、[A10 整体替换设备证据](evidence/p4-a10-json-transfer/device-verification-20260920.txt) | 通过；覆盖重启、离线 CRUD、注入写入失败与失败回滚 |
| A10 JSON 导入／导出 | 共享 version 1 双向契约、系统文件面板、整体事务与系统返回修正均通过技术复审 | 已确认通过 | [P4／A10 实施记录](p4-a10-json-transfer.md)、[Android 文件往返证据](evidence/p4-a10-json-transfer/README.md)、[iOS 导出来源完整性](evidence/p4-a10-json-transfer/ios-source-integrity.txt) | 通过；iOS App 的真实文件 App／ShareLink UI 往返未执行，既有记录将其作为已接受限制保留 |
| A11 外观模式 | 三态主题、系统跟随、布局／无障碍和 Logo 修正已通过技术复审 | 2026-09-21 确认通过 | [P3-09／A11 记录](p3-09-a11-appearance.md)、[API 37 设备证据](evidence/p3-09-a11-appearance/README.md) | 通过；以已保存的 iOS 视觉基准对照；本轮未重新执行 iOS App UI |

各项“已实现”“已测试”“已审查”和“用户已验收”依据各自证据分别记录；不得把 P6 本身写成用户已验收。A08 是按用户明确接受的已观察范围记录通过，表中的未验证边界仍有效。

## iOS 基准核对与差异

本轮沿用已独立审阅的[只读预审](p6-pre-audit.md)、P3-03-R2 同状态视觉对照及 A10 未修改 iOS version 1 导出文件的证据，没有重新构建或操作 iOS App。另核对 `git diff --name-only 77da40b..HEAD -- Android ios web ios/Shared` 无输出，说明预审基准后这些代码与共享协议路径没有提交差异。已记录的 Android 对应约定包括：今日页展示规则与 iOS 同基准、version 1 导入导出严格遵守共享 schema、安卓文件选择／返回使用系统对应交互。A10 的真实 iOS 文件 App／ShareLink UI 往返限制继续保留；未发现需要扩大到 iOS、Web 或共享协议的任务。

## P6 当前状态与下一步

P6-02 本轮自动化回归和 A01—A11 证据矩阵已齐备，GPT6 SOL 已独立核对并通过。随后由用户决定是否接受 P6 阶段结果。**本文件不代替用户确认，也不把 A08 的范围验收扩为 P6 验收。** D04 仍待决定；不配置商店签名、不发布、不合并 `main`。
