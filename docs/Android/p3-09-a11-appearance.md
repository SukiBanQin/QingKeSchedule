# P3-09／A11 Android 外观模式实施记录

状态：**A11 已实施并自测，待 Sol 独立复审和用户验收。** 本文只记录 A11 本轮范围，不代表整个 P3、A08 真机
遗留或完整 App 完成；文档不预先写入本次提交号（见交付消息或 `git log`）。

- 角色：原 DeepSeek V4.1 FLASH 执行窗口，复用当前窗口完成实施、自测、API 37 设备证据、文档、提交与推送，
  未创建新窗口或子 Agent。实际模型标识／服务／思考参数以用户客户端为准，本窗口无法读取，未核实。
- 开始基准：分支 `Android`，`a3ecf997535f12328ccc925fc04c26273cfb99dc`（= `origin/Android`），开始时工作区干净。
- 范围与边界：只实现 [实施分析](p3-09-a11-appearance-analysis.md) 定义的 A11；未修改 iOS、Web、共享 schema／
  fixtures、version 1、Room schema、DataStore 键名或存储值、A08 提醒语义、A10 导入导出、其他业务规则、
  工具链、发布配置或 `main`。

## 实现

### 单一路径的状态写入

- `ScheduleViewModel.setAppearanceMode(mode)`（新增）：复用 `ScheduleAppState.updatePreferences` 与既有
  `appearance_mode` 键，成功后才发布新的完整偏好快照（因此主题立即重组）；当前值相同直接返回、不写入；
  `appearanceInFlight` 保证写入中的第二次选择被忽略而不排队；失败由既有写入边界恢复旧偏好并保留既有中文
  全局错误；取消不发布普通错误。外观写入不调用 `reconcile`，不触碰课表、教学日历、提醒偏好或传输状态。
- `QingKeAppActions.setAppearanceMode` 是界面到该路径的唯一入口，生产侧由 `QingKeApp` 绑定 ViewModel。

### 06 外观 / DISPLAY 分区

- `SemesterSettingsScreen` → `TerminalSemesterForm` 新增可选 `appearanceSection`，位于 `transferSection` 之后、
  保存卡片之前，只由正式设置页传入，因此首次设置页没有该入口；首次设置仍然消费已保存或系统主题。
- `AppearanceSettingsSection`（新增）渲染 `06 外观／DISPLAY`：三态选项 `AUTO／跟随系统`、`LIGHT／浅色`、
  `DARK／深色`，选中项使用既有反相背景 + 3dp 黄色底线，`selectable(role = RadioButton)` 加
  `stateDescription = 已选择／未选择` 与 `contentDescription = "CODE 标签"`；下方分隔线与
  `当前显示：浅色／深色`，文字直接取渲染本子树所用的同一个已解析 `dark` 值，不维护第二份可能漂移的状态。
- 响应式：`shouldStackAppearanceOptions(availableWidthDp, fontScale)`（纯函数）以 300dp 与 1.3 倍为阈值；
  普通宽度横排三项，320dp 窄屏、130% 字号与无障碍 200% 字号改为竖排，不裁切、不重叠，每项至少 64dp 高
  （≥48dp 触控要求），设置页本身可继续滚动。

### A11 验证暴露的主题问题（唯一必要的页面修正）

强制浅色 + 系统深色时品牌 Logo 曾显示为浅色夜版图形叠在浅色页面上。原因与修正、断言级前后证据见
[品牌 Logo 主题修正证据](evidence/p3-09-a11-appearance/brand-logo-theme-fix-20260920.txt)：`BrandHeader` 原先用
`painterResource(if (dark) qingke_logo_dark else qingke_logo)`，而 `qingke_logo` 同时有 `drawable-nodpi` 与
`drawable-night-nodpi` 两份，夜版按**系统**夜间模式解析；现在改为按应用外观解析（浅色用 `UI_MODE_NIGHT_NO`
配置上下文取深色墨水版），不改任何资源文件、不重做已验收页面。

## 验证

全部命令、数量与限制见 [设备与自动化证据](evidence/p3-09-a11-appearance/device-verification-20260920.txt) 与
[证据索引](evidence/p3-09-a11-appearance/README.md)。

| 检查 | 结果 |
| --- | --- |
| `testDebugUnitTest`／`testReleaseUnitTest` | 各 **283 tests、0 failures／0 errors／0 skipped**（A11 新增 10：ViewModel 6、布局 4） |
| `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| `lintDebug` | **0 errors／24 warnings**，全部既有类别，A11 无新增 |
| `connectedDebugAndroidTest`（API 37 ARM64） | **202 tests、0 failures／0 errors／0 skipped**（A10 189 + A11 新增 13，`AppearanceSettingsTest` 13） |
| 文档测试与 `git diff --check` | 通过（见交付消息） |

设备（`emulator-5554`，API 37 ARM64，1080x2400／420）：系统浅色与深色下分别验证 SYSTEM 即时跟随；
分别强制 LIGHT／DARK 后交替切换系统明暗证明不再跟随；`force-stop` 重开后仍保持选择，DataStore 文件中
`appearance_mode` 为 `dark` 且无其它外观值；固定同数据核对今日、周课表、设置、课程编辑器与弹窗在强制
浅色（系统深色）与强制深色（系统浅色）下均一致可读，浅色页面全屏均值 200–227、深色 21–36，无夹在中间的
页面；状态栏在浅色态为浅色表面配深色图标、深色态为深色表面配浅色图标；`logcat` 检索 FATAL／ANR 0 命中。

## 未验证与限制

1. 写入中的禁用和失败保留由自动化证明（真实 DataStore 写入毫秒级，adb 无法稳定捕捉中间态）。
2. 设备运行使用本次构建的 debug APK：仓库没有发布签名配置（D04 只约定个人 debug 安装），
   `app-release-unsigned.apk` 未签名，因此不是商店签名产物，也未改发布配置。
3. iOS App 真实 UI 往返、A08 真实权限弹窗／通知观感／重启／Doze／厂商真机后台投递保持原有未验证状态，
   不因 A11 变成已验证。
4. **A11 已实施并自测，待 Sol 独立复审和用户验收；“已测试”不等于“已审查”或“已验收”。**
