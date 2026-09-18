# P3-07／A07 教学日历证据

本目录只包含 P3-07／A07（教学日历）本轮实现的证据，不覆盖 P3-06 及更早的 R2—R6 截图目录。

- 任务：P3-07／A07 教学日历的 ViewModel 写入入口、共享「03 教学日历」设置区与今日／周表联动。
- 代码基准：分支 `Android`，开始基准 `05248f2`（已推送 `origin/Android`）；本轮只新增本次提交，未改写历史、未强推、未合并 `main`。
- 证据时间：2026-09-19；设备：API 37 ARM64 AVD（`qingke-api37-r3-arm`，emulator-5554），`wm size 1080x2400`、`wm density 420`、浅色／深色、字体比例 1.0／1.3、窄屏 720x1280@320。

## 实现范围（本轮）

- `Android/app/src/main/java/com/qingke/schedule/domain/AcademicCalendarRules.kt`（新增）：`AcademicDayResolution`、`AcademicCalendarResolver`（优先级：指定停课日 > 指定调课日 > 周末停课 > 正常星期）与纯编辑规则（同日互斥、去重、日期排序、同日调课替换来源星期、午休范围合法且开始必须早于结束）。今日、周表和未来 A08 都从这里取规则，不再依赖 `presentation`。
- `Android/app/src/main/java/com/qingke/schedule/preferences/SchedulePreferences.kt`：`LunchBreakSettings.isValidRange` 与 `AcademicCalendarPreferences.isStoredDate` 公开为可复用纯函数，`normalized()` 行为不变（键名、编码、默认值、上限均未改）。
- `Android/app/src/main/java/com/qingke/schedule/presentation/SchedulePresentation.kt`：删除本地副本，改为引用 `domain` 规则，展示行为不变。
- `Android/app/src/main/java/com/qingke/schedule/viewmodel/ScheduleViewModel.kt`：7 个写入入口（周末开关、添加／删除停课日、添加／删除调课日、午休开关、午休时间），全部经 `ScheduleAppState.updatePreferences` 在仓库转换内基于最新存储值原子更新；非法午休范围不写入。
- `Android/app/src/main/java/com/qingke/schedule/ui/QingKeApp.kt`、`ui/AcademicCalendarUiState.kt`（新增）：首次设置与正式设置共享的「03 教学日历」终端面板，复用现有终端时间选择器、内联月历与表单视觉组件；日期、模式、展开状态、午休时间与午餐时间选择器都是独立状态和独立 test tag，不与学期日期控件串联；`InlineMonthCalendar` 与 `CompactPicker` 只增加可选标签／可见标签参数，既有调用行为不变。
- 未改：Room schema、共享 JSON schema／版本 1、iOS、Web、`main`；未实施 A08（通知权限、AlarmManager、提醒 UI、通知重建）、A10（文件迁移）与 A11；D01 维持「版本 1 备份不携带教学日历、导入不清除本机偏好」。

## 截图与目视检查结论

截图由真实 debug 入口（`com.qingke.schedule/.MainActivity`）经 `adb shell input tap` 逐级操作后 `screencap` 取得；每张都在同一状态用 `uiautomator dump` 记录了可见节点与 bounds，明细见 `node-verification-20260919.txt`。由于本窗口没有图形界面，逐张检查方式是：像素分类渲染（浅／深、青、信号黄、危险红、墨迹占比）＋节点文本／内容描述与 bounds 核对，而不是人眼观感确认。

| 截图 | 状态 | 检查结论 |
| --- | --- | --- |
| `a07-calendar-settings-light.png` | 正式设置，滚动到 03 教学日历（浅色，1080x2400@420） | 面板青色竖线存在；`教学日历`／`CALENDAR`／`03` 标题、`周末默认不上课`、`在周课表显示午休`、`开始时间，11:40`、`结束时间，14:00`、`停课日`／`调课上课`、`日期，2026年9月19日，展开日历`、`添加停课日`、footer 文案全部在屏内（y=506…1712，宽 53…1027），信号黄添加按钮与深色选中模式按钮可见 |
| `a07-calendar-onboarding-light.png` | 首次设置同一滚动位置（浅色） | 同一组控件以 `onboarding-*` 前缀渲染，未出现 `settings-*` 节点；`创建课表` 卡片与 01／02 面板同时在屏内，说明 03 区确实插在共享表单里 |
| `a07-calendar-settings-dark.png` | 正式设置同位置（深色） | 与浅色截图节点集合一致，深色下文本、开关、青色竖线、信号黄按钮均可见（墨迹 89.5% 深色 + 5.2% 信号黄） |
| `a07-calendar-exceptions-light.png` | 已添加一个停课日与一个调课日的列表态（浅色） | `停课日 / OFF`、`不显示课程`、`删除 2026年9月19日 周六`、`调课日 / MAKEUP`（`按周三课表`）、`删除 2026年9月15日 周二`、`按课表上课：周三` 与 `添加调课日` 均可见；危险红 ✕ 像素存在 |
| `a07-calendar-lunch-picker-light.png` | 点击午休开始时间后的共享终段时间选择器（浅色） | 全屏遮罩 + 居中对话框：`TIME SELECT`、`午休开始时间`、HH:MM 读数、左右 00—59 数字列（选中行青色）、`取消`／黄色 `确认`；`terminal-time-picker-*` 节点计数为 0，证明午餐选择器用的是独立 tag |
| `a07-today-stop-dark.png` | 把今天设为停课日后的今日页（深色） | 今日空状态卡片（青色 STANDBY 标签与青色竖线）显示 `已设为停课日，今日不显示课程。`，ADD 与底部标签栏可见；说明设置立即驱动今日页 |
| `a07-week-lunch-break-light.png` | 周课表矩阵（浅色，默认午休 11:40–14:00） | 矩阵中红色分隔带位置出现午休横条，节点 `午休，11:40到14:00` bounds=[74,424][1006,503] 横跨全宽；矩阵表头、时间列、行线与其他课程行不受影响 |
| `a07-calendar-narrow-320.png` | 窄屏 720x1280@320（浅色，滚动到 03 区） | 全部控件在屏内（x ≤ 652），开关行 104px、时间单元 96px（= 48dp 触控高度）无横向裁切；面板青色竖线存在 |
| `a07-calendar-font130.png` | 字体比例 1.3（浅色，滚动到 03 区） | 标题、开关、模式按钮、日期行与黄色添加按钮均可见且未截断；文本放大后仍保持在面板内 |

已知取舍：窄屏与 130% 两张截图的黄色添加按钮位于屏幕下缘附近（可继续滚动），不是裁切缺陷。

## 验证与限制

完整命令、结果与设备记录见 `host-and-device-verification-20260919.txt`。摘要：Debug／Release JVM 各 121 tests、0 failures／errors／skipped；`connectedDebugAndroidTest` 104 tests、0 failures／errors／skipped；`lintDebug` 0 errors、20 warnings；`assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 通过；文档验证 71 tests OK、`documentation.test.sh`、`repository-layout.test.sh`、`git diff --check` 通过。

限制：本次只完成实现与自测，**Sol 独立复审与用户视觉验收均未进行**；截图检查为程序化核对，不能替代用户观感验收；A08／A10／A11 与整套 P3 仍未完成。
