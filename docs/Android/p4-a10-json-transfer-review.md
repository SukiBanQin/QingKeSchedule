# P4／A10 JSON 导入导出独立技术复审

## R1 再复审结论（2026-09-20）

R1 提交 `156b004c09b8503ed48f74f63828fbc8ae5eae0c` 已修复 R0 的唯一阻断，**通过 Sol 独立技术再复审**。
复审范围为 `5ab64f974e356122fca6ecdeb3ab80ed5f1419b8..156b004`；未发现越界修改共享 JSON version 1、
5 MiB 边界、严格解码／校验、Room 整体事务、`DATA_SAVED` 提醒协调、D01 本地偏好边界、iOS、Web、共享
schema／fixtures、A11、发布流程或 `main`。

- `TransferUiState.showsPrompt` 与四种实际弹窗状态保持一致；`TransferDialogHost` 仅在提示可见时启用
  `BackHandler`。可取消态系统返回只调用一次 `dismissTransferPrompt()`；`isWriting` 时空处理器消费返回，
  不确认、不取消事务、不关闭弹窗或 Activity。
- 新增的真实 `Espresso.pressBack()` 设备测试覆盖预览、解析失败、可重试写入失败、写入中以及无提示但课程
  编辑器接管返回；同时保留按钮禁用和指针遮罩回归。没有提示时禁用的传输处理器不抢占其他返回处理。
- API 37 真实 `OpenDocument` 流程证明预览弹窗按系统返回后只收起弹窗，应用和首次设置页仍在且没有执行导入；
  无弹窗时再次返回会正常离开应用。写入态因真实事务持续时间极短，使用确定性的设备自动化覆盖，证据边界准确。

Sol 独立复跑 Debug／Release JVM 各 **273 tests**，以及 API 37 ARM64 `DataTransferSectionTest`
**15 tests**，均为 0 failures／errors／skipped。执行者报告的完整设备 189、构建、lint、文档验证和 R1 设备证据
已核对，本次未重复完整 189 项设备集合或构建／lint。首次设备复跑因临时 Android home 同时改变 Gradle 缓存
位置而在插件解析前失败；恢复既有隔离 Gradle 缓存后 15 项设备测试通过，该次属于复审环境准备失败，不是应用
测试失败。

至此 A10 应用实现的技术门槛关闭，但**不等于用户验收、整个 P4 或完整 App 完成**。iOS App 真实 UI 往返、
A08 真实权限弹窗／通知观感、重启／Doze 与厂商真机后台投递限制保持不变；A11、发布和 `main` 合并仍未授权。

## R0 复审结论（历史，2026-09-20）

> 后续状态：R1 `156b004` 已关闭下述系统返回阻断并通过 Sol 独立技术再复审；本节保留 R0 历史。

提交 `b56eda8f79221837dbe06f89ee44f43f152bd5d5` 已完成 A10 的主要实现、测试与设备证据，但本次 Sol
独立技术复审**未通过**。复审范围为 `c730c62f5f655057df661124dccf8cd814a1d160..b56eda8`；未发现越界修改
iOS、Web、共享 JSON schema／fixtures、Room schema、DataStore 偏好键、A11、发布流程或 `main`。

有界读取、严格 UTF-8／版本 1 解码与业务校验、预览确认后整体事务替换、取消／解析／写入失败保留原数据、
导入成功后学期草稿重建与单次 `DATA_SAVED` 提醒协调、系统文件导出和 D01 本地偏好不变均已落到代码并有对应
测试。当前剩余一项 Android 系统返回行为缺口，必须完成 R1 后再复审。

## 必须修正的问题

### 导入弹窗没有接管系统返回，写入中仍可退出 Activity

`TransferDialogHost` 显示导入预览、写入失败重试、解析失败或导出失败弹窗时，没有注册 `BackHandler`；共用的
`TerminalDialog`／`ModalScrimHost` 只拦截指针事件，也不处理系统返回。因此按 Android 系统返回键不会调用
`dismissTransferPrompt()`，而会落到 Activity 默认返回行为。普通预览／错误态会把页面或应用一起退出，而不是
像弹窗「取消／好」按钮一样收起当前提示；更关键的是，`isWriting == true` 时界面按钮虽然全部禁用，系统返回仍
未被消费，用户可以在整体事务替换尚未完成时退出并使 Activity／ViewModel 进入销毁流程。

这不符合本项目“返回操作采用安卓对应方式”的基线，也没有满足 A10 对取消边界和破坏性写入期间交互封闭的
要求。现有 `DataTransferSectionTest` 只覆盖按钮和遮罩，设备证据中的系统返回取消只发生在 SAF 文件选择器，
均未覆盖传输弹窗本身的系统返回。

R1 应保持现有业务和协议不变，并至少完成：

1. 传输预览、解析失败、导出失败和可重试写入失败弹窗可见时，系统返回与对应取消／确认收口一致，仅关闭弹窗，
   不退出当前页面或 Activity；
2. 导入写入进行中消费系统返回且不调用取消，直至事务完成；不得借机改变仓库事务、提醒协调或 D01 偏好边界；
3. 增加 Compose 设备测试，真实发送系统返回并断言上述可取消态和写入态行为；保留现有指针遮罩、按钮禁用和
   ViewModel 失败保留原数据回归；
4. 在 API 37 设备上至少验证一次预览弹窗系统返回只收起弹窗且 App 仍停留原页面，并保存可核对的运行记录；
   如自动化可直接证明，可用测试记录作为证据，不要求新增截图。

## 已确认正确的部分

- 输入流按实际读取字节执行 5 MiB 上限，恰好 5 MiB 可进入解码，第 5 MiB+1 字节拒绝，不依赖 provider 元数据。
- 解码拒绝非法 UTF-8、未知字段、未知版本、非整数数值及业务无效数据；`semester: null`、重复 ID、顺序与
  `updatedAt` 按版本 1 既有语义处理。
- 预览不写库；只有明确确认后调用既有 Room 事务整体替换。写入成功后发布新快照、重建学期草稿并以
  `DATA_SAVED` 协调一次提醒；失败不先报成功且原数据保留。
- 导入导出不读取、写入、清除或覆盖教学日历、午休、提醒、外观和通知授权等 DataStore／系统本地状态；没有
  把 Room 与 DataStore 宣称为跨存储事务。
- Android 使用 `OpenDocument`／`CreateDocument("application/json")` 和 content URI，不申请存储权限；导出
  生成严格 UTF-8 version 1 JSON。标准 CreateDocument provider 的真实导出路径已验证。
- 未修改的 `origin/IOS` version 1 实现与 Android 之间完成程序化双向文件往返；两向 JSON 树与共享 fixture
  一致。iOS App 真实文件 App／ShareLink UI 因 Swift 宏工具链故障未运行，执行记录已明确作为限制而非通过项。
- 非数字 `schemaVersion` 的两端错误分类差异不影响两端均拒绝；MediaStore 非标准 provider 的 `rwt` 截断限制、
  A08 的真实重启／Doze／厂商真机投递和通知观感限制均已准确记录，没有被误报为 A10 已验证。

## 独立验证

- 复审开始时工作区干净；本地 `Android`、`origin/Android` 跟踪引用与远端
  `refs/heads/Android` 均为 `b56eda8f79221837dbe06f89ee44f43f152bd5d5`。
- 独立复跑 A10 JVM 关键测试 `ScheduleDataReaderTest`、`ScheduleTransferContractTest`、
  `ScheduleViewModelTransferTest`：Debug／Release 各 **32 tests**，均为 0 failures／errors／skipped。
- API 37 ARM64 `emulator-5554` 独立复跑 `ScheduleFileAccessDeviceTest` **4 tests**、
  `ScheduleImportDeviceTest` **5 tests**、`DataTransferSectionTest` **10 tests**，合计 **19 tests**，均为
  0 failures／errors／skipped。
- 已核对执行者报告的完整 Debug／Release JVM 各 273、完整设备 184、构建、lint、文档验证、12 张截图、
  命令记录、设备属性、双向文件与 iOS 源码完整性证据；本次未重复全量 273／184 或完整构建。
- 首次独立 JVM 命令使用全新离线 Gradle home，因该目录没有 AGP 缓存而在编译前失败；改用项目既有隔离缓存后
  测试通过。首次设备命令因默认 Android home 不可写而在安装前失败；指定可写临时 home 后上述 19 项通过。
  两次均属于复审环境准备失败，不是应用测试失败。

## 状态与边界

A10 已实现并完成执行者自测，但因系统返回阻断，**技术门槛未关闭，待 R1 和 Sol 再复审；用户验收未进行**。
A11、发布和 `main` 合并仍未授权。本次 Sol 只维护审查与交接文档，不修改应用代码，不扩大 D01。
## R1 执行窗口记录（2026-09-20，非复审结论）

> 后续状态：R1 `156b004` 已通过本文顶部记录的 Sol 独立技术再复审；本节保留执行窗口当时的交付状态。

R1 只修正 R0 指出的「导入弹窗没有接管系统返回」一项，未改共享版本 1、5 MiB 边界、严格解码／校验、Room 整体
事务替换、`DATA_SAVED` 提醒协调或 D01 本地偏好边界。

- `TransferUiState.showsPrompt`：传输提示（预览／解析失败／导出失败／可重试写入失败）是否可见的唯一判定。
- `TransferDialogHost`：`BackHandler(enabled = transfer.showsPrompt)` 与弹窗渲染共用同一判定；非写入态的系统
  返回只调用一次 `dismissTransferPrompt()` 并停留原页面；`isWriting` 时只消费返回，不调用 confirm／dismiss、
  不取消事务、不退出 Activity；事务结束后沿用既有成功／失败状态。没有传输提示时不注册，SAF 文件面板、课程
  编辑器、学期保存弹窗与普通页面返回行为不变。
- 新增 `DataTransferSectionTest` 5 个用例（真实 `Espresso.pressBack()`）：可取消预览态只 dismiss 一次且页面／
  Activity 仍在、解析失败与可重试写入失败态各 dismiss 一次且 confirm 0 次、写入态 confirm／dismiss 均 0 次且
  按钮仍禁用、无提示时课程编辑器仍收到返回、遮罩仍吞掉落在弹窗后导入行的真实触摸。
- 回归（实际运行）：Debug／Release JVM 各 **273 tests、0 failures／0 errors／0 skipped**；API 37 ARM64
  `connectedDebugAndroidTest` **189 tests、0 failures／0 errors／0 skipped**（R0 184 + R1 5）；
  `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` **0 errors／24 warnings**
  （全部既有类别，R1 无新增）；`python3 docs/tests/android-documentation.test.py` 71 tests OK；
  `documentation.test.sh`、`repository-layout.test.sh`、`git diff --check` 通过。
- 真实设备（`emulator-5554`，API 37 ARM64）：真实 `ACTION_OPEN_DOCUMENT` 选入 version 1 文件得到预览后按系统
  返回，弹窗消失、`topResumedActivity` 仍是本应用、页面仍是原首次设置页、无「已导入 …」；无弹窗时再按返回则
  离开应用（前台变为 Launcher）。记录与截图：
  `evidence/p4-a10-json-transfer/r1-system-back-20260920.txt`、
  `evidence/p4-a10-json-transfer/13-r1-preview-back-dismissed-api37.png`。
- R1 限制：写入中的返回无法用 adb 手工捕捉（事务毫秒级完成），由自动化设备测试证明。

**本记录由执行窗口填写，不是复审结论。R1 已实施并自测，待 Sol 再复审；不得据此宣布复审或用户验收通过。**
