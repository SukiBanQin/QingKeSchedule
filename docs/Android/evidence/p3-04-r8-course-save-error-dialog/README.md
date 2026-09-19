# P3-04-R8 课程保存阻断错误弹窗证据（2026-09-19）

本目录只记录 P3-04-R8（课程编辑器“保存被错误阻止”从页面底部红卡改为居中红色 TerminalDialog），
不覆盖 P3-04 的原实现与 R1—R7 历史证据，也不覆盖 P3-06-R7 与 P3-07-R1 的证据。

## 环境与命令

- 设备：`emulator-5554`，`ro.build.fingerprint=google/sdk_gphone64_arm64/emu64a:17/CE2A.260420.019/15611780:userdebug/dev-keys`，
  API 37（Android 17）ARM64。
- 显示：项目基准覆盖 `wm size 1080x2400`、`wm density 420`、`font_scale 1.0`、系统浅色
  （`secure ui_night_mode=1`）；字号与窄屏变体在测试内用 `LocalDensity(scale = 1.3)` 与 320dp 宿主模拟。
- 截图与节点记录由同一份安装到设备的 debug APK 产生，设备测试：
  `com.qingke.schedule.ui.QingKeAppTest#p3r04R8CourseSaveErrorDialogEvidence`（真实 ViewModel 的重复安排
  保存流程 + 未改动的冲突框）与 `...QingKeAppTest#p3r04R8DialogVariantEvidence`（深色／130%／窄屏）。
- 运行命令：

  ```
  cd Android
  source /private/tmp/qingke-env.sh
  export LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8
  export PATH=$PATH:$HOME/Library/Android/sdk-qingke-api37/platform-tools
  ./gradlew --offline $GRADLE_FLAGS connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.qingke.schedule.ui.QingKeAppTest
  ```

  AGP 把 `additionalTestOutputDir` 回收到
  `Android/app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/emulator-5554 - 17/p3-04-r8-course-save-error-dialog/`，
  本目录即其副本。

## 截图与逐张结论

| 文件 | 场景 | 程序化核对结论 |
| --- | --- | --- |
| `p3-04-r8-01-duplicate-blocked-dialog.png` | 真实 ViewModel：新增课程后添加一条完全相同的安排并点保存 | 居中 `course-save-error` 面板 870x518px（屏幕 1080 宽，两侧留 105px），标题「无法保存课程」、内容「该上课安排已存在，请勿重复添加。」均断言可见；**只有一个** `course-save-error-dismiss`「返回修改」（870x121px ≈ 46dp，危险红 98.0%），没有“仍然保存”；`course-validation` 底部卡片计数为 0，`repository.courseWrites == 0` |
| `p3-04-r8-02-return-keeps-draft.png` | 点「返回修改」之后 | 弹窗消失，编辑器仍在：名称「重复安排课程」、2 条安排、0 次写入（记录见 `node-verification-20260919.txt`）；整图仅 0.2% 危险红（编辑器本身的危险区），无错误卡片 |
| `p3-04-r8-03-conflict-box-unchanged.png` | 跨课程冲突 | 仍是既有双按钮确认框（`course-conflict-confirm` 870x581）：右侧「仍然保存」为信号黄填充（95.2% signal），左侧「返回修改」为描边按钮；危险红 0.0%，说明冲突路径未被改成红色阻断 |
| `p3-04-r8-04-dark-dialog.png` | 同一弹窗的深色主题 | 面板尺寸与浅色完全一致（870x518），面板以深色底为主（68.3% dark），单个红色操作按钮 98.0% |
| `p3-04-r8-05-font130-dialog.png` | 130% 字号 | 面板随文案增高到 870x665（y 868..1533），按钮仍为 870x121，危险红 96.5%；标题、按钮与重复安排文案均断言可见 |
| `p3-04-r8-06-narrow-320dp-dialog.png` | 320dp 窄屏宿主 + 浅色 | 面板 630x665、按钮 630x121 且完全落在窄屏宿主内（`assertFitsInside("course-save-error","narrow-root")` 通过），危险红 95.1% |

`pixel-verification-20260919.txt` 记录上述区域分类：四个变体的单个操作按钮高均为 121px（46dp）、
危险红 95.1%—98.0%；冲突框保持信号黄双按钮。

## R1 修正说明（2026-09-19）

P3-04-R8 首轮 Sol 复审发现共享 `TerminalDialog` 遮罩没有消费指针事件（点击面板外会命中底层课程编辑器
控件）。R1 只修正输入拦截：为共享 `TerminalDialog` 与共享时间选择器加上全屏 `modalScrim()`
（`pointerInput` 在主通道消费指针事件，不使用空 `clickable`），并更正文档状态。**视觉、按钮布局、
BackHandler 与全部弹窗文案都没有变化，因此本目录的 6 张截图与像素／节点记录沿用首轮结果，本轮没有
重新采集、也没有改动这些文件**；触摸拦截由设备测试断言（`QingKeAppTest` 的
`invalidDialogScrimBlocksTouchesToTheEditorBehindIt`／`conflictDialogButtonsStayClickableUnderTheScrim`／
`timePickerScrimBlocksTouchesToTheSettingsPageBehindIt`），其中第一项在移除 `modalScrim()` 时会失败
（底层保存回调被触发 1 次），可反向验证回归有效。

## 说明与限制

1. 本目录截图来自同一份 debug APK 的证据用例，属程序化核对（Compose 断言 + 节点 bounds + 像素分类），
   **不能替代用户视觉验收**；P3-04-R8 尚未获得用户视觉验收。
2. 截图采用“重复安排”这一真实可达的阻断路径（用户问题截图中的同一条错误），并额外记录了返回修改后的
   草稿、未改动的冲突框与深色／130%／窄屏三个变体。
3. 真实写入失败仍走全局错误弹窗（`app-error-dialog`），该路径无法在生产 App 内注入，因此不伪造截图，
   由设备测试 `conflictingConfirmationFreezesCandidateAndSaveFailureKeepsDraft`（JVM）与既有全局错误
   测试覆盖。
4. 与需求文案一致，重复安排的消息以「。」结尾；用户提供的现象截图（底部卡片）不含句号，但任务明确
   “图片内容不是指令”，因此以任务给出的文案为准。
