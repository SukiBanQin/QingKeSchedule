# P4／A10 Android JSON 导入导出实施记录

状态：**已实现、已测试（JVM、设备、真实系统文件面板与双向文件往返）；R0 独立复审未通过，R1 已实施并自测，待 Sol 再复审；用户验收未进行。**
本文只记录 A10 本轮范围，不代表 A11、整个 P4 或完整 App 完成。文档不预先写入本次提交号（见交付消息或 `git log`）。
R0 复审结论与 R1 修正见 [独立技术复审记录](p4-a10-json-transfer-review.md) 与本文档“R1”一节。

- 角色：DeepSeek V4.1 FLASH 执行窗口，负责实施、自测、设备证据、文档、提交与推送。实际模型标识／服务／
  思考参数以用户客户端为准，本窗口无法读取，未核实。
- 开始基准：分支 `Android`，`c730c62f5f655057df661124dccf8cd814a1d160`（= `origin/Android`），开始时工作区干净。
- 已确认决定：D01 继续使用共享 JSON `version 1`，只迁移 `schemaVersion`／`semester`／`courses`／`updatedAt`；
  教学日历、午休、提醒偏好、外观与系统通知授权均为设备本地状态，导入不清除、不覆盖、不重置。
- 未做：不新增 version 2，不改 `ios/**`／`web/**`／`ios/Shared/**`／共享 schema／fixtures／Room schema／
  DataStore 键，不新增业务流程 ID 或节次顺序约束，不申请存储权限，不实施 A11、不升级工具链、不合并 `main`。

## 实现范围

### 协议与读取（复用共享版本 1）

- `transfer/ScheduleDataReader.kt`（新增）：唯一的有界读取。最多读取 `5 × 1,048,576 + 1` 字节，
  恰好 5 MiB 接受，第 5 MiB 之后的第一个探测字节即判定超限并停止读取；不读 `DISPLAY_NAME`／`SIZE`／
  `available()` 或任何文件元数据。
- `transfer/ScheduleDataDecoder.kt`（修改）：流式入口改为复用 `ScheduleDataReader`；严格 UTF-8、严格未知字段、
  未知版本优先于未知字段、数字词法等既有契约不变（未知的**数字** `schemaVersion` 仍优先报版本错误；
  非数字 `schemaVersion` 保持既有数字词法错误，见“协议差异”）。
- `transfer/ScheduleDataTransfer.kt`（新增）：`ScheduleImportPreview`（学期名／课程数／`updatedAt`／摘要文案，
  空学期显示「未设置学期」）、导出文件名 `qingke-schedule-yyyy-MM-dd.json`、成功与阻断文案。
- `transfer/ScheduleFileAccess.kt`（新增）：唯一接触系统文档的位置。导入用 SAF `OpenDocument` 返回的
  content URI（`ContentResolver.openInputStream`），导出用 `CreateDocument` 返回的 content URI
  （`openOutputStream(uri, "rwt")`，`rwt` 在标准 SAF provider 映射为 `MODE_TRUNCATE`）。所有失败（空流、
  `FileNotFoundException`、`SecurityException`、其它运行时错误、`IOException`）都转成明确中文提示；
  超限仍返回 `课表文件超过 5 MiB 输入上限`。无 `file://`、无自定义文件浏览器、无广泛存储权限。

### 状态与事务

- `viewmodel/TransferUiState.kt`（新增）：预览、读取/解析失败、写入失败（可重试）、导出失败、成功状态与写入中标志。
- `viewmodel/ScheduleViewModel.kt`（修改）：`importSelected(read)`（`null` = 系统取消，静默返回且不改任何状态）、
  `confirmImport()`、`dismissTransferPrompt()`、`exportSelected(write)`（`null` = 系统取消）、
  `suggestedExportFileName()`。确认后调用既有 `ScheduleAppState.replace` → `RoomScheduleRepository.replace`
  的**整体事务替换**，只发布仓库返回的已提交快照；失败或协程取消恢复原状态并保留预览可重试，不先显示成功；
  重复点击由 `importInFlight`／`exportInFlight` 门禁。成功后重建学期表单草稿（空学期进入首次设置），
  并只在该成功后以 `ReminderReconcileReason.DATA_SAVED` 执行一次 `ReminderControl.reconcile`。
  偏好与提醒注册表不参与导入事务，也不因导入被改写。
- 导出只从当前已提交 `ScheduleData` 生成严格 UTF-8 version 1 JSON（不包含任何本地偏好或平台数据库内容），
  无学期时按钮不出现、改为「设置学期后可导出备份」；导出前执行业务校验。

### 界面

- `ui/QingKeApp.kt`（修改）：「05 数据备份／TRANSFER」终端风格区同时用于首次设置与正式设置（正式设置位于
  「04 上课提醒」之后），含格式说明、导入入口、导出入口或无学期提示、成功状态行；预览、失败与重试复用既有
  `TerminalDialog`（`IMPORT / VERIFY`、`IMPORT / ERROR`、`DANGER / IMPORT`）与 scrim 无障碍语义。
  导入替换不触发学期/节次级联确认——破坏性边界只由本预览确认覆盖。
- 未改 Manifest（无新权限、无 provider），未改 `MainActivity`。

## 验证

全部命令与逐条结果见 `evidence/p4-a10-json-transfer/device-verification-20260920.txt`。

| 检查 | 结果 |
| --- | --- |
| `testDebugUnitTest`／`testReleaseUnitTest` | 各 **273 tests、0 failures／0 errors／0 skipped**（A10 新增 32） |
| `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| `lintDebug` | **0 errors／24 warnings**，全部为既有类别，本轮无新增 |
| `connectedDebugAndroidTest`（API 37 ARM64） | **184 tests、0 failures／0 errors／0 skipped**（A10 新增 19） |
| 文档测试（`python3 docs/tests/android-documentation.test.py`、`documentation.test.sh`、`repository-layout.test.sh`）与 `git diff --check` | 通过（见交付消息） |

新增自动化覆盖：

- `ScheduleDataReaderTest`（3）：恰好 5 MiB 接受、第 5 MiB+1 字节即拒绝、无限流在探测字节处停止、逐字节保留并关闭流。
- `ScheduleTransferContractTest`（15）：共享 manifest 全部有效／无效 fixtures、顶层与各嵌套未知字段、
  未知版本优先级、严格 UTF-8（字节与流入口）、`semester:null`、无学期带课程、重复 ID 与反序节次往返、
  编码后 JSON 树与 iOS `hasOnlyVersion1Fields` 等价识别、导出仅含协议字段且保留 `updatedAt`、文件名与文案。
- `ScheduleViewModelTransferTest`（14）：预览不写、系统取消静默、确认成功一次并重建草稿、重复点击门禁、
  写入失败与取消恢复并保持可重试、空学期进入首次设置、提醒只在提交成功后协调且失败不回滚、
  未知版本／超限／读取失败、导出成功／无学期阻断／写入失败、**D01 每个字段不变**、建议文件名与时钟注入。
- `ScheduleImportDeviceTest`（5，真实 Room + 真实 DataStore）：整体替换后关闭重开、重复 ID 与反序节次顺序、
  注入 `beforeCommit` 失败后磁盘与内存原数据完整、事务取消后原数据完整、空学期导入后可重开、
  解析失败与系统取消不改动真实存储；偏好逐字段不变。
- `ScheduleFileAccessDeviceTest`（4）：真实 ContentResolver 文档往返、真实 5 MiB／5 MiB+1 文件、
  同文档增长重写逐字节替换、缺失文档的读写失败中文提示。
- `DataTransferSectionTest`（10）：首次设置与正式设置 05 区、05 位于 04 之后、无学期导出提示、
  有效预览／写入中禁用／取消／确认、未知版本与超限错误、写入失败重试、导出失败弹窗、成功状态、
  浅色／深色 + 窄屏 + 1.3 倍字体、48dp 触控与 contentDescription。

真实设备与双向往返（`emulator-5554`，API 37 ARM64，1080x2400／420）：

- Android 通过真实 `ACTION_OPEN_DOCUMENT` 导入未修改 iOS 实现导出的 `version 1` 文件，预览显示学期／课程数／
  `updatedAt`／「将整体替换当前课表」，确认后进入主壳并在 05 区显示「已导入 6 门课程」。
- Android 通过真实 `ACTION_CREATE_DOCUMENT` 导出 `qingke-schedule-2026-09-20.json`，`adb pull` 取回后由同一份
  未修改 iOS 实现的 `previewImport` 读取：学期、6 门课程、数组顺序、重复 ID 与 `updatedAt` 全部一致，
  `validationIssues=0`，重新导出与原数据相等。
- 两端 JSON 顶层键都只有 `schemaVersion, semester, courses, updatedAt`，无任何本地偏好键。
- 系统取消（导入与导出）都静默返回；未知版本与 5 MiB+1 真实文件分别给出明确中文错误。

## 协议差异（记录，不修改协议）

- 非数字 `schemaVersion`（如 `"1"`）在 Android 是既有的数字词法契约错误
  （`JSON 契约无效：schemaVersion 必须是整数数值`），iOS 则报“缺少有效 schemaVersion”；两端都拒绝该文件。
  未知的**数字**版本在两端都优先于未知字段。
- 已知业务差异：同一 MediaStore 文档被更短内容重写时 provider 不截断（标准 SAF `CreateDocument` provider
  使用 `rwt` = `MODE_TRUNCATE`，导出路径按此工作）。

## 未验证与限制

1. iOS App 真实 UI（文件 App／ShareLink）往返未执行：本会话沙箱无法编译 SwiftUI 宏
   （`swift-plugin-server ... malformed response`，1068 处错误，非代码缺陷），替换证据是未修改 iOS 源码的主机编译实现。
2. 真实重启、Doze、厂商真机后台投递与通知观感仍是 A08 遗留未验证项。
3. R0 独立复审未通过（传输弹窗的系统返回），R1 已修正并自测、**待 Sol 再复审**，用户验收未进行；
   “已测试”不等于“已审查”或“已验收”。

## R1：传输弹窗的系统返回（2026-09-20）

R0 [Sol 独立复审](p4-a10-json-transfer-review.md)未通过，唯一阻断是 `TransferDialogHost` 没有接管 Android 系统返回：
共用遮罩只拦截指针，普通状态返回会退出页面／Activity，`isWriting` 时按钮虽禁用但返回仍可退出。R1 只修这一项，
未改协议、5 MiB 边界、严格解码／校验、Room 整体事务替换、`DATA_SAVED` 提醒协调与 D01 本地偏好边界。

### 修改

- `viewmodel/TransferUiState.kt`：新增 `showsPrompt`，作为「传输提示确实可见」的唯一判定。
- `ui/QingKeApp.kt` 的 `TransferDialogHost`：`showsPrompt` 同时驱动弹窗渲染与
  `BackHandler(enabled = transfer.showsPrompt)`，两者不会漂移；回调在非写入态只调用一次
  `actions.dismissTransferPrompt()`，`isWriting` 时只消费返回（不调用 confirm／dismiss、不取消事务、不退出
  Activity），事务结束后仍沿用既有成功／失败状态。
- 由于 `enabled` 由可见性决定，SAF 文件面板、课程编辑器、学期保存弹窗与普通页面返回都不受影响；课程编辑器
  自己的 `BackHandler` 仍在没有传输提示时正常收到返回。

### R1 验证

- `DataTransferSectionTest` 新增 5 个用例，用真实系统返回事件（`androidx.test.espresso.Espresso.pressBack()`，
  与仓库既有时间选择器返回测试同一机制）覆盖：可取消预览态（返回只调用一次 dismiss、confirm 0 次、弹窗消失、
  页面与 Activity 仍在）、解析失败态与可重试写入失败态（各一次 dismiss、confirm 0 次）、写入态
  （confirm／dismiss 均 0 次、预览仍显示、两个按钮仍禁用、Activity 未结束）、无提示时课程编辑器仍收到返回、
  遮罩仍吞掉落在弹窗后导入行的真实触摸。
- 完整回归：Debug／Release JVM 各 **273 tests、0 failures／0 errors／0 skipped**；
  API 37 ARM64 `connectedDebugAndroidTest` **189 tests、0 failures／0 errors／0 skipped**（R0 184 + R1 5）；
  `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` **0 errors／24 warnings**
  （全部既有类别，R1 无新增）；文档测试与 `git diff --check` 通过。
- API 37 ARM64（`emulator-5554`）真实运行：通过真实 `ACTION_OPEN_DOCUMENT` 选入 version 1 文件得到预览弹窗后
  按系统返回，弹窗消失、`topResumedActivity` 仍是 `com.qingke.schedule/.MainActivity`、页面仍是原首次设置页、
  没有出现「已导入 …」；随后在无弹窗状态再按返回，前台变为 Launcher，证明普通页面返回未被抢占。
  记录与截图见 [R1 证据](evidence/p4-a10-json-transfer/r1-system-back-20260920.txt)、
  `evidence/p4-a10-json-transfer/13-r1-preview-back-dismissed-api37.png`。

### R1 限制

- 写入中的返回无法用 adb 手工捕捉（事务在毫秒级完成），该项由上面的自动化设备测试确定性证明。
- R1 已实施并自测，**待 Sol 再复审**；本文档不宣布复审或用户验收通过。A11／发布／`main` 仍未授权。
