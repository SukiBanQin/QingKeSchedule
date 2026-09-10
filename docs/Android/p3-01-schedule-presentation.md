# P3-01 今日与周课表展示模型分析

## 状态与结论

分析日期：2026-09-10。分析基准为
`322227ef29bf1eab08ec74ecf0eb66b19c79b77c`，分支 `Android`；开始时本地 HEAD、
`refs/heads/Android` 与 `origin/Android` 一致，工作区干净。重新获取远端后，`origin/IOS` 为
`fc3ddfb8ffa14b205a591ffdbed5632d5f975001`。用户已接受 P2-04 同窗口复审的限制并确认 P2 阶段结果，
同时授权准备下一项 P3 子任务。

下一项应为 P3-01“今日与周课表展示模型”：先把 iOS `SchedulePresentation.swift` 和所需领域规则
移植为纯 Kotlin、可注入本地时间的确定性模型，再由后续 Compose 页面只负责渲染和交互。这样能在
没有 UI 噪声的情况下先固定 A02、A03 以及 A07 对今日／周表的业务影响。

P3-01 不创建页面、ViewModel、导航或 Activity 接线，也不修改持久化。任务边界明确，但包含日期、
单双周、停课调课、排序、课程状态和矩阵重叠布局的组合推理，建议执行窗口使用 Terra／高。当前
分析窗口只维护分析和交接文档，不修改应用代码。

## 实际 Android 缺口与 iOS 基准

- Android 已有 `ScheduleRules.teachingWeek`、单双周适用和冲突判断，但尚无指定教学周日期、课程发生、
  开始／进行中／结束状态或今日／周表展示模型。
- `AcademicCalendarPreferences` 已持久化周末停课、指定停课日、调课日和午休，但没有把这些设置解析为
  某个日期的授课来源星期，也没有接入今日／周表计算。
- Android `MainActivity` 仍只显示“轻课”。在展示规则未固定前直接编写整页会把业务错误和布局错误混在
  一起，增加后续 A02／A03 对照难度。
- iOS 基准以本地日历日期计算教学周，今日课程按开始节次、结束节次、`zh_CN` 名称和原始稳定顺序排列；
  结束分钟仍为“进行中”，只标记第一门未来课程为“下一门”。
- iOS 周表把选择周限制在 `1..totalWeeks`，学期外不标记当前周；每周生成周一至周日日期，先应用停课／
  调课解析，再按真实生效周过滤课程。调课日显示来源星期的课，但显示列仍是调课发生日期。
- 周矩阵按节次号排序；跨节课程占闭区间行。发生重叠的不同课程分配不同 lane，同一相连重叠分量使用
  稳定 lane 数。午休分隔只在午休完整落在相邻节次之间时出现。

## 可实施契约

### 时间、发生项与基础规则

- 使用 `java.time.LocalDate`／`LocalTime`／`LocalDateTime`。展示入口接收显式 `now: LocalDateTime`，
  不直接读取系统时间、默认时区或 `Clock.systemDefaultZone()`；从系统时钟转换本地时间留给后续 ViewModel。
- 在领域或展示层建立 `CourseOccurrence`，至少包含完整 `Course`、`CourseSchedule` 和能区分重复业务 ID 的
  稳定来源位置。P1 已接受历史重复课程／安排 ID，内部展示键不得假设 `course.id` 或 `schedule.id` 唯一，
  也不得去重或覆盖重复项。
- 扩展 `ScheduleRules`（或等价纯 Kotlin 边界）提供：指定周和星期对应日期、某教学周全部发生项、
  课程状态及时间分钟解析。课程状态为 `FINISHED`、`ONGOING`、`UPCOMING`；开始分钟和结束分钟都算
  `ONGOING`。学期、节次经现有验证器保证合法，不改变 `ScheduleValidator` 的协议规则。
- 教学周继续以学期开始日期所在星期一为第 1 周，学期前后保留 0、负数或大于总周数的计算结果；
  只有展示入口决定是否显示为空或把选择周限制在学期范围。

### 教学日历解析

- 建立纯 Kotlin 的日期解析结果：授课日包含 `sourceDayOfWeek` 和 `isMakeup`，非授课日包含稳定中文原因。
- 优先级严格为：指定停课日 → 指定调课日 → “周末默认停课” → 按日期自身星期授课。停课日期与调课
  日期即使遇到未规范化输入也以停课优先；星期采用周一 1、周日 7。
- 不修改 DataStore 格式或 `AcademicCalendarPreferences.normalized()`；展示模型只消费当前完整偏好。

### 今日展示

- `TodaySchedulePresentation` 至少输出教学周、`isNonTeachingDay`、稳定空状态文案和课程项目列表。
- 只在当前教学周位于 `1..totalWeeks` 且日期解析为授课日时生成课程；调课日按
  `sourceDayOfWeek` 取课。排序依次为开始节次、结束节次、`zh_CN` 名称比较、原始来源顺序。
- 对排序后的课程计算状态；只把第一项 `UPCOMING` 标为下一门。仅 `ONGOING` 项提供进度：从首节开始
  到末节结束计算已过分钟、剩余分钟和限制在 `0.0..1.0` 的比例。
- 学期内停课空状态为“原因，今日不显示课程。”；学期内无课为“今天没有课程，享受空闲时间吧。”；
  学期外为“当前日期不在这个学期内。”。

### 周展示与冲突

- `WeekSchedulePresentation` 将请求周限制在 `1..totalWeeks`，输出限制后的周、学期内才存在的当前周，
  以及固定周一至周日 7 项。`initialWeek` 在学期前返回 1、学期后返回末周、学期内返回当前周。
- 每天输出真实日期、显示星期、解析后的来源星期、是否停课及发生项。停课日为空；调课日按来源星期
  取课，但每项 `displayDayOfWeek` 仍为实际显示列。
- 冲突标记只比较该显示日实际出现的课程，且只在不同课程业务 ID 之间检查闭区间节次相交；所选周的
  单双周已在发生项过滤中生效。排序按开始节次、结束节次、`zh_CN` 名称和稳定来源位置。

### 周矩阵与显示文字

- `WeekMatrixPresentation` 按节次号升序建立行，忽略无法映射到当前节次表的非法发生项；有效跨节课程的
  `rowSpan = endRow - startRow + 1`，周一列为 0、周日列为 6。
- 同一显示日中闭区间行重叠的课程分配不同 lane。按开始行、结束行和稳定发生键排序分配；一组通过
  重叠链连接的课程使用相同 `laneCount`，互不连接的后续分量可以重新从 lane 0 开始。
- 午休启用且起止时间合法时，找到第一节 `startTime >= 午休结束` 的节次；只有其前一节
  `endTime <= 午休开始` 且插入位置不在首行前时输出 `WeekMatrixBreak`。否则不显示分隔。
- 提供页面直接复用的稳定显示文字：周一至周日名称、单节／跨节文案、节次时间范围、
  `MON–SUN / N PERIODS` 和非空“教室 · 教师”紧凑信息。不在本任务加入颜色、尺寸或 Compose 类型。

## 测试矩阵

- 使用共享 `valid/complete-schedule.json` 解码为现有 `ScheduleData`，复现 iOS 基准的今日排序、四种状态
  顺序、唯一下一门、进行中 46／64 分钟及 `46.0 / 110.0` 进度。
- 覆盖学期前、学期末后一周、学期内无课；状态开始前、开始分钟、结束分钟和结束后边界；无对应节次时
  的安全退化。
- 覆盖第一周／末周限制、单双周、周三及周日列、真实日期、学期外 currentWeek、调课显示列和来源星期。
- 覆盖指定停课优先于同日调课、周末停课、普通工作日、停课／调课对今日和周表的一致影响。
- 覆盖不同课程重叠标红、同课程多安排不互相标红、端点相接视为冲突、单双周不同时不同时出现，以及
  重复安排 ID 仍保留独立发生项和稳定内部键。
- 覆盖矩阵跨节行、周日第七列、两项重叠、三项链式重叠、分离分量 lane 复用、稳定顺序、无效节次映射
  忽略；午休合法间隙、禁用、与节次相交、首节前和末节后均按契约处理。
- 覆盖显示文字的空教师／教室组合，不产生多余分隔符。测试不得依赖运行当天、机器默认 Locale／时区
  或随机 UUID。

## 明确不包含

- 不修改 `MainActivity`，不新增 ViewModel、Composable、导航、主题、图标、截图或 UI 测试。
- 不实现首次学期设置、今日页、周表页、课程编辑、学期／教学日历／外观页面；这些拆为后续 P3 任务。
- 不修改 Room、DataStore、`ScheduleAppState`、草稿保存、通知、导入导出、iOS、Web 或共享 fixtures／schema。
- 不决定 D01、D03 或正式发行范围，不进入 P4、P5、P6，也不宣称 A02、A03、A07 或完整 App 完成。

## 验证、提交与审查

执行窗口至少运行：

```bash
cd Android
./gradlew clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug assembleDebugAndroidTest --no-daemon --console=plain
cd ..
python3 docs/tests/android-documentation.test.py
bash docs/tests/documentation.test.sh
bash docs/tests/repository-layout.test.sh
git diff --check
```

P3-01 为纯 Kotlin 展示模型，不要求 `connectedDebugAndroidTest`，但 AndroidTest APK 仍须编译。提交只包含
本任务生产代码、JVM 测试和必要 Android 文档／证据，推送 `Android` 并核对本地／远端一致。由于矩阵 lane、
调课来源星期和重复 ID 内部身份会直接决定后续页面可见结果，实施后必须由分析窗口独立复审实际 diff、
共享 fixture 测试和最终 XML；通过也只关闭 P3-01，不等于 A02、A03、A07、P3 或完整 App 验收。
