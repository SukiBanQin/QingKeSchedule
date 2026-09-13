# P3-03 今日课表页面与实时刷新分析

## 状态、基准与任务定位

分析日期：2026-09-13。用户已确认 P3-02 的实现、测试和最终独立复审结果，并授权继续准备
P3-03。分析基准为 Android 分支 `db1077f4c7103b548644b484346b34588469bd4e`；分析开始时
本地 HEAD、`refs/heads/Android`、`origin/Android` 与远端同名分支一致，工作区干净。重新获取
远端后，`origin/IOS` 为 `fc3ddfb8ffa14b205a591ffdbed5632d5f975001`。

P3-03 把 P3-01 的纯 Kotlin 今日展示模型接入 P3-02 主壳的“今日”标签，交付真实今日课程、
空状态、状态强调、进行中进度和时间刷新。它只覆盖 A02 的今日展示以及 A07 对今日展示的既有
影响，不实现课程新增／编辑、周课表、设置或通知，也不表示 A02、A07、P3 或完整 App 已验收。

建议执行窗口使用 **Terra／高**：页面结构和状态边界已经明确，但实施同时涉及最新 iOS 基准
差异、秒级时间语义、生命周期、Compose 下拉刷新、重复业务 ID 的稳定身份和设备视觉验证。
完成后必须由分析审查窗口独立复审。

## 最新 iOS 基准差异必须先纳入

Android 分支中的 iOS 文件是分支建立时的旧副本，不能代表当前 iOS 基准。分析时对照
`origin/IOS` `fc3ddfb8ffa14b205a591ffdbed5632d5f975001` 的实际文件，确认其中已包含
`c3191ae`“刷新课表时间和倒计时”修正；它早于本次分析，但尚未同步到 Android 的 P3-01 模型：

- iOS `ScheduleAppState` 持有可观察 `currentTime`，应用处于 active 时每秒更新，回到前台立即刷新；
- 今日、周和设置滚动区域支持下拉刷新时间，并短暂显示 `SYNC / LOCAL` 反馈；刷新不重新读取仓库；
- `CourseTimingProgress` 已从整分钟改成 `elapsedSeconds`、`remainingSeconds` 和 `fraction`，同时提供
  整分钟派生值与 `m:ss` 剩余倒计时；
- 课程状态以秒为边界：开始时刻进入 `ONGOING`，结束时刻立即进入 `FINISHED`，结束时刻不再属于
  进行中。

P3-01 已确认的排序、下一门、教学日历、重复 ID 和空状态不变。P3-03 只补齐今日页实际需要的
上述时间差异并更新对应 P3-01 回归；`be6a056`、`fc3ddfb` 的周表跟随当前周修正留给后续周课表
任务，不在本轮提前实施。

## 一、展示模型的秒级对齐

1. 将 Android `CourseTimingProgress` 对齐为 `elapsedSeconds`、`remainingSeconds`、`fraction`，提供
   `elapsedMinutes = elapsedSeconds / 60` 和稳定 `remainingClockText`（例如 `63:08`）。不要在 UI
   重新计算倒计时。
2. 进行中进度以实际秒数计算；`2026-08-31T09:41:52` 对共享 fixture 中 08:55–10:45 的课程应为
   `elapsedSeconds=2812`、`remainingSeconds=3788`、`remainingClockText="63:08"`，比例为
   `2812.0 / 6600.0`。
3. `ScheduleRules.occurrenceStatus` 使用本地时分秒比较：开始前为 `UPCOMING`，开始时刻至结束时刻前
   为 `ONGOING`，结束时刻及之后为 `FINISHED`。必须更新旧有“结束分钟仍进行中”的测试和文档
   表述，增加结束前一秒与结束时刻回归。
4. 缺失节次映射继续安全退化为 `UPCOMING` 且无进度；不得改变课程排序、唯一下一门、停课／调课
   优先级或重复业务 ID 的内部 `OccurrenceKey` 规则。

## 二、可观察时钟、生命周期与手动刷新

1. `ScheduleViewModel` 使用可注入 `() -> LocalDateTime` 的本地时间提供者，持有可观察的当前时间；
   首次学期草稿由同一时间的 `toLocalDate()` 创建，测试不得依赖机器日期、时区或真实等待。
2. 提供只更新内存时间的刷新入口。它不得调用 `ScheduleAppState.load()`、仓库、Room、DataStore、
   保存接口或错误状态，也不得重建首次设置草稿、改变标签或修改课表数据。
3. 生产 `QingKeApp(viewModel)` 在生命周期达到前台可见状态时立即刷新，并按当前 iOS 基准每秒更新；
   离开前台时协程必须取消，返回前台重新立即取时。使用生命周期感知的协程边界，不在 Composable
   重组时重复启动无界任务，不吞掉 `CancellationException`。
4. 今日滚动区域在有课和空状态下都支持 Android 对应的下拉刷新。手动刷新只调用上述内存时间入口，
   重复触发受门禁保护，并短暂显示稳定的“刷新中 / SYNC / LOCAL”反馈；不得伪装成数据仓库重载。
5. 下拉刷新、每秒 tick、Activity 重建和标签切换均复用同一个 Activity 级 ViewModel。瞬时刷新动画
   无需跨系统杀进程恢复，但不能重置当前标签或任何未提交草稿。

## 三、今日页内容与视觉层级

1. `READY` 且有学期、当前标签为 `TODAY` 时，以 `state.data.semester`、`state.data.courses`、
   `state.preferences.academicCalendar` 和可观察当前时间调用既有 `TodaySchedulePresentation.create()`；
   页面不得复制教学周、单双周、停课调课、状态、排序或下一门规则。
2. 主壳底部标签保持 P3-02 的单一所有权和安全区；今日页主体至少包含：
   - 固定在滚动内容外的品牌头，代码 `LOCAL / 01`；
   - 日期 hero、`SCHEDULE :// TODAY`、今日标题、学期／第几教学周与单双周、两位课程数；
   - 有进行中课程时优先展示它，否则展示唯一下一门；全部已结束时不显示 featured 区；
   - featured 状态条、开始／结束时间、课程名、教室／教师、进行中进度条、已进行分钟和 `m:ss` 倒计时；
   - 完整课程序列，按 P3-01 顺序显示 `COMPLETE`、`CURRENT`、`NEXT`、`UPCOMING`、时间和详情；
   - 结束标记 `END OF SCHEDULE` 与最后一项结束时间。
3. 空状态继续直接使用展示模型的三类消息：学期外、学期内无课、停课日。由于 P3-03 不含课程编辑，
   不显示“使用 ADD 录入课程”等当前无法完成的提示。
4. 复用 P3-02 的信号黄、青色、深浅色表面、方正边框和系统栏对比度；课程条使用有效 `#RRGGBB`
   颜色，解析失败安全回退青色。浅色、深色都需保持正文和状态标签对比度，不以颜色作为唯一状态信息。
5. featured 与序列中的课程在本轮是只读面板，不显示无效的编辑箭头，不提供空操作点击。课程点击编辑
   和右下角添加按钮由后续课程编辑任务一次接通后再出现，并在交接中记录这项临时差异。
6. 列表键和 test tag 必须使用 `OccurrenceKey(courseIndex, scheduleIndex)`，不能使用可能重复的
   `course.id` 或 `schedule.id`。同一发生项在 featured 和序列中的标识还须有不同前缀。
7. 大字体与窄屏允许 hero 和卡片调整布局或换行，不得裁掉课程名、时间、倒计时或底部标签；滚动内容
   底部须为标签栏保留空间。

## 四、测试标识与测试矩阵

建议保留或提供以下稳定标识：

- `today-screen`、`today-brand-header`、`today-date-hero`、`today-teaching-week`、
  `today-course-count`；
- `today-refresh-status`、`today-activity-rail`、`today-featured-course`、
  `today-featured-progress`；
- `today-course-sequence`、`today-course-{courseIndex}-{scheduleIndex}`、`today-empty`。

最低测试矩阵：

1. JVM 展示规则回归：共享 `complete-schedule.json` 的 09:41:52 秒级进度、`63:08` 倒计时、开始前、
   开始时刻、结束前一秒、结束时刻，以及缺失节次安全退化；既有排序、下一门和重复 ID 测试继续通过。
2. JVM ViewModel：注入时间的初值和刷新、同一时间提供者用于首次草稿日期、刷新不增加仓库 load／save
   次数、不改变标签／表单／课表、连续刷新门禁，以及生命周期任务取消后不再发布 tick。
3. Compose API 37：固定 09:41:52 的四项列表顺序和状态；进行中项成为 featured 并显示进度与
   `63:08`，无进行中时下一门成为 featured，全部结束时无 featured；教师／教室齐全和为空回退；三类
   空状态；课程颜色；深浅主题；底部标签仍可用。
4. 重复 `course.id`／`schedule.id` 的两个来源项都必须进入语义树，内部 tag 唯一；不能因 LazyColumn key
   重复而丢失或崩溃。
5. 在有课和空状态分别执行真实下拉手势，验证刷新回调恰好一次、反馈可见且结束，不重新读取仓库；
   Activity 重建后仍由同一个 ViewModel 提供当前时间和标签。
6. API 37 ARM64 保存浅色和深色的固定数据截图，至少补充一个 130% 字体或窄屏截图。固定数据截图可以
   来自 Compose 测试宿主，但必须明确标注；另用真实生产入口验证已保存学期进入 `today-screen`、空状态
   可滚动刷新、强停重启仍进入今日页。P3-03 没有课程编辑入口，因此不得把测试宿主的有课截图表述成
   生产 UI 已可自行录入课程。

## 五、允许修改与明确排除

允许执行窗口修改：

- `Android/app/src/main/java/com/qingke/schedule/domain/ScheduleRules.kt`；
- `Android/app/src/main/java/com/qingke/schedule/presentation/SchedulePresentation.kt`；
- `Android/app/src/main/java/com/qingke/schedule/viewmodel/ScheduleViewModel.kt`；
- `Android/app/src/main/java/com/qingke/schedule/ui/QingKeApp.kt`，以及新增职责清晰的今日页／最小共享视觉文件；
- 对应 `Android/app/src/test/`、`Android/app/src/androidTest/`；
- 本任务 `docs/Android/` 交接、证据、截图及必要的 `docs/tests/android-documentation.test.py`。

不应新增依赖或升级现有依赖；如果现有 Material 3 版本确实不能提供所需下拉刷新入口，应先回传实际
编译证据，不自行扩大工具链。不得修改 `MainActivity`、Application、`ScheduleAppState`、Room、DataStore、
草稿、JSON 协议、iOS、Web 或共享 schema／fixtures。

明确不包含课程新增／编辑／删除、课程卡点击路由、周课表页面、现有学期编辑、教学日历设置、外观设置、
提醒、通知调度、导入导出、D01、D03、P4、P5 或 P6；不得提前实现 P3-04 或宣称 A02、A07、P3、完整 App
完成。

## 六、验证、提交与独立审查

执行窗口至少运行：

```bash
cd Android
./gradlew clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug assembleDebugAndroidTest --no-daemon --console=plain
./gradlew connectedDebugAndroidTest --no-daemon --console=plain
cd ..
python3 docs/tests/android-documentation.test.py
bash docs/tests/documentation.test.sh
bash docs/tests/repository-layout.test.sh
git diff --check
```

设备验证必须使用唯一 API 37 ARM64 模拟器，记录 AVD、序列号、SDK、ABI、最终 XML/HTML 的 tests、
failures、errors、skipped，以及新增用例确实进入测试体。截图和生产入口检查按上节区分测试宿主与真实
持久化路径；模拟器结束后正常关闭并核对 ADB 列表。

提交只包含 P3-03 文件并推送 `Android`，核对本地 HEAD、`refs/heads/Android`、`origin/Android` 与远端
同名分支一致。由于本任务改变已确认 P3-01 的时间边界并连接生命周期、Compose 与页面视觉，完成后必须
由分析审查窗口独立复审实际 diff、最新 iOS 对齐、时钟取消、重复 ID 列表键、设备 XML 和截图。复审通过
仍只表示 P3-03 已审查，不等于用户验收 A02／A07、P3 或完整 App。
