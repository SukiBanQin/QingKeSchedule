# P3-04-R8-R2 共享模态遮罩内部滚动回归证据（2026-09-19）

本目录只记录 P3-04-R8-R2（修复共享模态遮罩导致弹窗内部无法真实滑动），不覆盖
`docs/Android/evidence/p3-04-r8-course-save-error-dialog/` 的首轮 R8 截图。

## 问题与结论

用户实测：首次设置与正式设置的时间选择器无法上下拖动小时与分钟，只能点当前可见的几个数值（现象截图见
任务记录）。根因：R1 提交 `6aa6693` 把 `modalScrim()` 装在同时包含弹窗内容的父 Box 上并在 Main 通道消费
指针事件；面板外穿透被挡住了，但子级 `verticalScroll` 的真实拖动也被中断。R2 把全屏拦截层改为**面板
之后的独立 sibling 底层**，面板与其内部滚动内容位于其上方，既保留面板外拦截，又让面板内点击与拖动由
真实子控件接收。

## 环境与命令

- 设备：`emulator-5554`，API 37 ARM64；`wm size 1080x2400`、`density 420`、`font_scale 1.0`、系统浅色。
- 证据用例（真实 ViewModel + 真实 Android 输入注入）：

  ```
  cd Android
  source /private/tmp/qingke-env.sh
  export LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8
  export PATH=$PATH:$HOME/Library/Android/sdk-qingke-api37/platform-tools
  ./gradlew --offline $GRADLE_FLAGS connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class='com.qingke.schedule.ui.QingKeAppTest#p3r04R8R2ModalScrollEvidence'
  ```

## 截图（只能证明滑动后的结果）

| 文件 | 场景 | 记录结果 |
| --- | --- | --- |
| `p3-04-r8-r2-01-first-boot-picker-after-real-swipes.png` | 首次设置：第 1 节开始时间 08:00 → 07:20 | 小时先滑动 1 次（向下）到达 07，分钟滑动 5 次（向上）到达 20，读数 07:20 |
| `p3-04-r8-r2-02-first-boot-draft-after-confirm.png` | 首次设置确认后的草稿 | 草稿第 1 节开始时间 = 07:20，此时尚未写库（`writes=0`） |
| `p3-04-r8-r2-03-settings-picker-after-real-swipes.png` | 正式设置：同一节次 07:20 → 06:35 | 小时滑动 1 次到达 06，分钟滑动 3 次到达 35；确认后保存，`writes=2`，已存学期第 1 节开始时间 = 06:35 |
| `p3-04-r8-r2-04-lunch-picker-after-real-swipes.png` | 午休结束时间 14:00 → 13:30（与节次不重叠） | 小时滑动 1 次到达 13，分钟滑动 7 次到达 30；确认后偏好写入 1 次，`lunchBreak.endTime=13:30`，无冲突确认框 |
| `p3-04-r8-r2-05-color-dialog-measured.png` | 自定义颜色弹窗内部滚动测量 | 面板高 1488px < 窗口 2400px，`clipped=false`；真实滑动后模式标签位置 901 → 901（未移动）→ 该尺寸下内容不溢出，不需要内部滚动，因此不制造假场景 |

**截图只能证明滑动后的结果；真实手势本身由设备测试与节点／状态记录证明**（见下）。

## 手势与验证方式

- 设备测试用 `InstrumentationRegistry.getInstrumentation().sendPointerSync(MotionEvent ...)` 注入**真实
  Android 输入事件**（down + 80 个 move + up，约 5ms 间隔、400px 位移），不是 Compose 的
  `performScrollTo()` 语义动作。
- `node-and-state-verification-20260919.txt` 记录每次滑动的次数、选择器节点 bounds、确认后的草稿值、
  已存学期值、写入计数与颜色弹窗测量。
- **重要发现（如实记录）**：Compose 测试注入（`performTouchInput` 的 swipe/drag）**无法复现**该缺陷——
  在 R1 与 R2 两种结构下小时列都会滚动。只有真实输入管线能区分：
  - `real-input-adb-verification-20260919.txt`：用 `adb shell input swipe` 在 R1 结构下小时列 bounds
    完全不变（11/12/13/14），在 R2 结构下滚动到 07/08/09/10/11。
  - 设备测试 `QingKeAppTest#timePickerHourColumnScrollsWithRealSystemInput`（同样用 sendPointerSync，
    细粒度 move profile）在临时恢复 R1 结构时**失败**（`hour 07 must appear after real system swipes,
    swipes=6`），恢复 R2 后通过；临时改动未提交。
- 因此本任务的回归门是「真实系统输入」测试与 adb 记录；Compose 注入的 swipe 用例保留为行为回归
  （它们能捕捉更硬的破坏，例如 Initial 通道消费会让子级完全收不到事件）。

## 限制

1. 本轮未改动时间选择器的视觉、按钮布局、`BackHandler`、弹窗文案与任何领域／保存规则。
2. 截图与记录来自同一份安装到设备的 debug APK，属程序化核对，**不能替代用户交互／视觉验收**。
3. 时间列的可达范围由真实滑动证明（0—23／0—59 均可到达，例如 06、07、13、35、20、30 等目标）；未穷举
   全部 24×60 组合，用户仍应自行确认体验。
4. 午休证据选用 13:30 以保持与节次不重叠（避免弹冲突确认框）；冲突确认框本身由既有 A07／R1 测试与
   本轮 R1 遮罩回归覆盖。
