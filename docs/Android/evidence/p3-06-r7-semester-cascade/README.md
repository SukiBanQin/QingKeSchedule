# P3-06-R7 学期设置保存证据（2026-09-19）

本目录只记录 P3-06-R7（节次变更的课程级联确认、原子保存与设置保存错误统一弹窗）及其 R1 修正，
不覆盖 A06／R2—R6 与 A07／R1 的历史证据。

## 环境与命令

- 设备：`emulator-5554`，`ro.build.fingerprint=google/sdk_gphone64_arm64/emu64a:17/CE2A.260420.019/15611780:userdebug/dev-keys`，
  API 37（Android 17），ABI `arm64-v8a`，型号 `sdk_gphone64_arm64`。
- 显示：`wm size` 物理 1080x1920，覆盖为项目基准 **1080x2400**；`wm density=420`；`font_scale=1.0`；
  系统浅色模式（`secure ui_night_mode=1`）。
- 主机：macOS 26.6.2（Build 25G83），JDK 17.0.20.1（Microsoft build）。
- 截图与节点记录由**同一份安装到设备的 debug APK**产生：设备测试
  `com.qingke.schedule.ui.QingKeAppTest#p3r06R7SettingsSaveDialogEvidence` 与
  `com.qingke.schedule.ui.QingKeAppTest#p3r06R7ReversedPeriodsBlockedEvidence` 通过真实
  `ScheduleViewModel` + Room 兼容的仓库假件驱动设置页，并在关键状态截图，同时把 Compose 语义节点
  bounds 写入 `node-verification-20260919.txt`／`node-verification-reversed-20260919.txt`。
- 运行命令（工作区根目录）：

  ```
  cd Android
  source /private/tmp/qingke-env.sh
  export LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8
  export PATH=$PATH:$HOME/Library/Android/sdk-qingke-api37/platform-tools
  ./gradlew --offline $GRADLE_FLAGS connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class='com.qingke.schedule.ui.QingKeAppTest#p3r06R7SettingsSaveDialogEvidence'
  ```

  AGP 会把 `additionalTestOutputDir` 内容回收到
  `Android/app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/emulator-5554 - 17/p3-06-r7-semester-cascade/`，
  本目录即该目录的副本。
- 全量设备验证（R1 修正后重跑）：`./gradlew --offline $GRADLE_FLAGS connectedDebugAndroidTest` →
  **121 tests、0 failures／errors／skipped**（R7 基线 120，R1 新增反序编号回归 1 项）。

## 截图与逐张结论

| 文件 | 场景 | 程序化核对结论 |
| --- | --- | --- |
| `p3-06-r7-01-blocked-top-save-error.png` | 顶部「保存」触发不可继续校验（学期名称为空） | 居中 `semester-save-error` 面板 870x518px（屏幕 1080 宽，四周留 105px），只有 1 个「返回修改」按钮（870x121px ≈ 46dp，危险红占比 98.0%）；同屏断言标题「无法保存学期设置」与文案「请填写学期名称」可见 |
| `p3-06-r7-02-blocked-bottom-save-error.png` | 底部「保存学期设置」触发同一错误 | 面板 bounds 与 01 **完全一致**（l=105,t=941,r=975,b=1459），按钮尺寸与红色占比亦一致，证明两个入口共用同一状态与同一弹窗 |
| `p3-06-r7-03-cascade-confirm.png` | 删除被课程引用的节次后的破坏性汇总确认 | 面板同尺寸；「确认保存并级联」按钮危险红 93.2%，「返回修改」为描边按钮（危险红 0.0%、面板色 88.9%），两个按钮各 424／425x121px；断言标题、两个按钮文案与「仅重新编号」汇总行可见 |
| `p3-06-r7-04-return-keeps-draft.png` | 点「返回修改」之后 | 无任何危险红（0.0%），草稿仍是删除后的 2 节，持久化学期未被写入 |
| `p3-06-r7-05-cascade-saved-week.png` | 确认级联后切到课表页 | 周表出现 `week-item-5:0:0`（课程仍在），`week-period-1-start` 文本为 `08:55`——原第 2 节按节次身份重映射为第 1 节；`week-period-3` 已不存在；整图无危险红 |
| `p3-06-r7-06-cascade-saved-today.png` | 确认级联后今日页 | 今日页 `today-featured-course-0-0` 仍显示该课程（无重启、无重新加载） |
| `p3-06-r7-07-saved-settings.png` | 保存成功后的设置页 | 青色面板竖线仍在（1.2% 青色），无危险红、无错误弹窗；成功提示由既有成功条展示 |
| `p3-06-r7-08-reversed-periods-blocked.png` | **R1 新增**：合法反序编号 9、4、20 + 安排 4–9，删除第 20 节后点保存 | 仍为同一红色错误弹窗，但面板因文案换行增高到 870x644px（l=105,t=878,r=975,b=1522），单个「返回修改」按钮 870x121px、危险红 98.0%；断言「无法按新节次顺序安全重映射」与「第4-9节」文案可见，且**没有**出现级联确认框（`semester-cascade` 计数 0）、`repository.writes == 0`；两个入口都弹同一错误框，关闭后草稿（2 节）与已存学期（9、4、20）都保持原样 |

## 像素／墨迹分类记录

`pixel-verification-20260919.txt` 记录同一批截图的区域分类与弹窗内墨迹行带：

- 三个 blocked 弹窗的单个红色操作按钮危险红占比均为 **98.0%**，高 121px（≈46dp），按钮带由像素自动定位
  （01／02 为 y 1336..1456，08 为 y 1399..1519）。
- 级联确认框的确认按钮 93.2%、返回按钮 0.0%（仅面板色）；关闭弹窗后的页面与成功页面危险红均为 0.0%。
- 01 与 02 的墨迹行带完全相同；08 的最大横向墨迹为 x=965、按钮行带完整位于屏幕内，未出现横向截断，
  也无需滚动即可看到按钮。

## 无法伪造或注入的部分

1. **真实写入失败**：生产 App 无法在运行中注入 Room／DataStore 故障，因此本目录不提供“写入失败”
   截图，不伪造。该路径由设备测试
   `failedCascadeWriteKeepsTheErrorDialogAndARetryableConfirmation`（错误框与可重试确认框同时存在、
   已存数据与草稿不变、修复后重试成功）以及
   `RoomScheduleRepositoryTest#failedOrCancelledAtomicWriteRollsBackSemesterAndCourses`
   （同一事务回滚、重开数据库仍为旧数据）断言覆盖。
2. **观感验收**：本轮由程序化核对（Compose 断言 + 节点 bounds + 像素／墨迹分类）产生证据，不能替代
   用户对红色弹窗与文案的实际观感验收。
3. **R1 的确认旁路修正无视觉变化**：`confirmSemesterCascade` 的“仅 AwaitingCascade 可确认、计划变化
   先刷新摘要、无影响时清除过期确认”都发生在同一状态机内，弹窗外观与按钮不变，因此复用 01—03 的视觉
   证据，由 JVM ViewModel 测试断言（`ScheduleViewModelTest` 7 项）。
