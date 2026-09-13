# P3-03-R2 今日页与共享主壳视觉对齐分析

## 状态、决定与基准

分析日期：2026-09-13。Android 分支基准为
`ef0ed3a0cfe9bbac1b3e164e959b8f773012afac`；分析开始时本地 HEAD、`origin/Android`
与远程同名分支一致，工作区干净。最新 iOS 基准仍为
`fc3ddfb8ffa14b205a591ffdbed5632d5f975001`。独立 iOS 工作区
`/Users/takagisan/课表软件-IOS` 已核对为干净的 `IOS...origin/IOS`，可用于运行当前 iOS App
和取得同状态截图，不得切换共享 Android 工作区来代替。

P3-03-R1 的功能、生命周期、颜色和 API 37 验证仍然有效，且已通过最终独立复审；但用户在
API 37 模拟器中人工查看生产 App 后，明确认为今日页与 iOS 的视觉差异较大，决定 **P3-03 暂不验收**，
先完成 P3-03-R2。R2 是视觉对齐修正，不进入 P3-04，也不推翻已经验证的业务与时间语义。

用户确认的视觉目标不是“借鉴”或重新设计，而是：除 Android 系统栏、返回方式、字体渲染和平台
控件行为等不可避免的平台差异外，Android 的品牌、内容结构、相对尺寸、颜色、边框、背景、间距、
选中态和信息层级应与当前 iOS App 一致。实现中不能以 Material 默认外观替代已有 iOS 组件设计。

## 一、权威参考与素材

参考优先级如下：

1. 用户本轮提供的 iOS 今日页截图，用于确认最终观感和组件之间的整体关系；
2. `origin/IOS` `fc3ddfb8` 的真实 iOS 源码，尤其是
   `ios/QingKeSchedule/Features/AppRootView.swift`、
   `ios/QingKeSchedule/Features/TodayScheduleView.swift` 和
   `ios/QingKeSchedule/Features/CourseStyle.swift`；
3. 当前 iOS App 在模拟器或真机中的实际渲染。执行窗口遇到截图与理解不一致时，应启动
   `/Users/takagisan/课表软件-IOS` 的当前 `IOS` 分支核对，而不是猜测或参考 Web Demo；
4. 以下用户指定的原始素材，不得用文字、占位图、Material 默认图标或重新生成的品牌图替代：
   - App 图标：`source/cover.png`，1254×1254、RGB；
   - 页面品牌 Logo：`source/qingke-logo-q-matrix-preview.png`，1672×941、RGBA。

`source/` 中原图保持不变。Android 资源可以由它们生成密度／自适应图标和适合布局的副本，但不得
改变 Logo 的比例、图形结构、品牌文字或青色识别块。深色模式若需提高对比度，可依据 iOS
`QingKeLogo` 的明暗变体只调整非青色前景的明暗表现；不能换成另一套 Logo。App 启动器标签同步为
“青课”，不能继续显示当前 Manifest 中的“轻课”。

## 二、已确认的现状差距

本轮 API 37 实机预览和既有 `p3-03-r1-today-light-api37.png` 表明，当前 Android 虽已包含 P3-03
要求的数据和状态，但至少存在以下结构性差距：

- 顶部使用整块深色栏和纯文字“青课”；iOS 是画布上的横向品牌 Logo、右侧灰色 `LOCAL / 01`
  与底部分隔线；
- Android 日期只显示 `MM/dd`，且标题堆叠在左侧；iOS 是月份缩写、超大日号、年份／星期、竖分隔线、
  `SCHEDULE :// TODAY`、今日标题／教学周以及右侧两位课程数的三段结构；
- Android 没有 iOS 的 24pt 网格、圆弧和环境渐变背景，也没有半透明／分层面板、细边框和阴影；
- Android featured、活动条、课程序列标题和课程行只是简化块，未复制 iOS 的状态头、编号、时间列、
  分隔线、进度底栏、课程色边和文字层级；
- Android 底部使用 Material `NavigationBarItem` 的整栏深色、纯文字和黄色胶囊；iOS 是画布上的有边框
  浅色／亚克力容器，每项有图标、标题和 `01/02/03`，只有选中项为独立深色矩形，并带黄色短线；
- 当前 Android 没有显式应用启动器图标资源，应用标签还是“轻课”。

这些差距不能留到全部页面完成后统一返工：品牌背景、主题 token 和底部标签栏是后续周课表、设置及
编辑页面的共同基础，应在 P3-04 前关闭。课程新增／编辑能力尚未实现造成的按钮差异则继续如实保留。

## 三、共享视觉基础与应用身份

1. 建立可复用的 Android 视觉 token／组件，数值和层级以 iOS `QingKeVisualSpec`／`QingKeTheme`
   为基准：信号黄 `#FFD400`、青色 `#28B9D6`（深色可用 iOS 的 `#35C8E5`）、浅色画布
   `#E3EBEB`、深色画布 `#081113`、反相表面 `#091113`／`#182427`，以及 iOS 对应的表面、边框、
   阴影、进度轨和危险色。方角继续为 0，不得被 Material 默认圆角覆盖。
2. 实现与 iOS `TerminalBackdrop` 同构的背景：约 24dp 网格、右上／中部三圈圆弧和从透明到环境色的
   对角渐变。背景不参与点击和无障碍语义，内容滚动时保持视觉稳定。
3. 终端数字／英文使用 Android 可合法提供的窄体等宽或 `sans-serif-condensed` 组合，调整字重、字距
   和行高逼近 iOS `Avenir Next Condensed`。不得复制未授权的 Apple 字体文件；确有无法消除的字体
   栅格差异，在对照证据中单独列明，不能据此改动布局设计。
4. 使用 `source/cover.png` 生成 Android 启动器需要的普通及 adaptive icon 资源，保持主体安全区，
   在 API 37 启动器中不得被异常裁掉；Manifest 明确设置 `android:icon`、`android:roundIcon`（如提供）
   和正确应用名“青课”。
5. 使用 `source/qingke-logo-q-matrix-preview.png` 建立可复用品牌头。轻色背景显示用户指定原图；深色
   背景的对比处理必须保持同一造型。布局对齐 iOS 的约 154×54pt 视觉框、左右留白、右侧 code 和
   底部分隔线，不能再以纯文字代替 Logo。

## 四、底部标签栏必须现在对齐

1. 保持 `ScheduleViewModel` 对 `MainTab` 的单一所有权、默认今日、Activity 重建保留和既有
   `today-tab`／`schedule-tab`／`settings-tab` 标识，不引入第二套导航状态。
2. 不再使用会产生 Material 胶囊 indicator 的默认 `NavigationBarItem` 视觉。按 iOS
   `TerminalTabBar` 构建方形标签栏：外层水平约 20dp 留白、顶部约 8dp、内层约 6dp、有 1dp 边框，
   每项最小高度约 62dp，三项等宽。
3. 每个标签展示对应图标、中文标题和编号：今日／`01`、课表／`02`、设置／`03`。图标语义与 iOS
   `square.grid.2x2`、`calendar`、`slider.horizontal.3` 一致，可用 Android VectorDrawable 或 Compose
   绘制，不引入无关依赖。
4. 选中项使用独立反相深色矩形、浅色图标／标题、黄色编号和底部约 30×4dp 黄色短线；未选中项使用
   透明背景、主文字色及次要编号色。整个导航容器不能再是当前整条纯深色块。
5. 触控目标至少 48dp，TalkBack 标签和 selected 状态清楚；系统导航栏及 gesture inset 使用 Android
   方式处理，但不能挤压、遮挡或改变 iOS 主体比例。

## 五、今日页视觉对齐

1. 顶部品牌头、滚动区和底部标签栏的结构应与 iOS `TodayScheduleView` 相同；品牌头固定，内容区可
   下拉刷新，底部为标签栏保留空间。
2. 日期 hero 按 iOS 三段布局实现：左侧月份缩写／大号日／年份与英文星期，中间竖分隔线和
   `SCHEDULE :// TODAY`／“今日”／教学周，右侧 `COURSE`／两位课程数／`/ DAY`。不得继续使用
   合并的 `MM/dd`。中文、日期和课程数在同尺寸下的相对高度与用户截图一致。
3. 活动条按 iOS 使用反相底色、顶部 4dp 状态色、左侧圆点和“当前课程／下一门课程”，右侧显示
   “进度更新于 HH:mm”。刷新反馈仍只更新时间且保留 R1 门禁。
4. featured 卡对齐 iOS：信号黄／青色状态头、`CURRENT/NEXT`、位置 `NN // NN`、独立开始与结束
   时间列、竖分隔线、课程名称和详情、底部进度轨与反相剩余时间栏。R1 的状态、秒级倒计时和颜色
   规则不能回归。
5. “课程序列”使用 iOS 的青色序号、标题、右侧 `QUEUE / ALL DAY` 和下划分隔线；课程行包含旋转序号、
   起止时间列、分隔线、彩色状态标签、课程名称／详情及左侧课程色边，完成项透明度降低。保持 R1
   的 `OccurrenceKey(courseIndex, scheduleIndex)` 唯一 key/tag。
6. 三类空状态也必须放在相同背景、品牌头、日期 hero、序列标题和面板体系中，不能因为无课程而退回
   简化页面。
7. P3-03 尚不含课程编辑，因此本次 **不显示** 无法工作的 `ADD` 按钮、featured/课程行编辑箭头或
   点击动作。它们应在课程编辑任务真正接通时按 iOS 原位出现；这是唯一已知的功能依赖型临时视觉差异，
   必须继续记录，不能用假按钮追求截图相似。

## 六、对照方法与验收矩阵

实施前先从干净的 `IOS` `fc3ddfb8` 启动 iOS App，结合用户截图和源码建立同状态参考。不能只看 Web，
也不能只凭记忆调整。Android 与 iOS 设备比例不同时，以安全区内的内容宽度归一化，比较组件层级、
相对尺寸和间距；允许系统状态栏／导航栏高度、字体字形栅格和 Android 原生返回行为不同，不允许品牌、
色值、组件结构或选中态不同。

最低验证：

1. 自动化测试继续覆盖 R1 的 79 项 Debug／Release JVM 和 36 项现有 API 37 connected 用例；更新受到
   视觉结构影响的 Compose 测试，不得删弱颜色、下拉、生命周期、四状态、重复 ID 和空状态断言。
2. 新增稳定检查：品牌 Logo 存在；月份／日／年份星期分别存在；活动条时钟；标签图标和 `01/02/03`；
   选中态在三标签切换及 Activity 重建后正确；应用名和 icon 资源配置正确。
3. 固定同一数据、日期／时间和主题保存成对截图：iOS 参考、Android 浅色、Android 深色和 Android
   130% 字体／窄屏。至少包含当前课程 featured、完整序列和空状态；另保存 API 37 启动器上的 App 图标。
4. 对每组成对截图写逐项检查结果：Logo／code、背景、hero、活动条、featured、序列、课程行、标签栏、
   系统安全区；不得只写“看起来接近”。明显结构偏差必须在交付前迭代，不留到 P3-04。
5. 在 API 37 生产入口验证保存学期后的今日空状态、下拉刷新、三标签切换、force-stop 重启和 launcher
   图标／名称；logcat 无 `FATAL EXCEPTION`／ANR。测试宿主的固定课程截图仍需明确标注，不能冒充生产
   已具备课程录入。
6. 浅色、深色和 130% 字体下不得裁掉 Logo、日期、课程名、倒计时、三标签标题／编号；触控和 TalkBack
   语义仍可用。必要的 Android 平台差异逐项写入证据并说明原因。

## 七、允许修改与排除范围

允许执行窗口修改：

- `Android/app/src/main/AndroidManifest.xml`；
- `Android/app/src/main/res/` 中应用名、launcher icon、Logo 及本任务需要的 drawable/font 配置；
- `Android/app/src/main/java/com/qingke/schedule/ui/QingKeApp.kt`，或拆出职责明确的主题、背景、品牌头、
  标签栏和今日页视觉组件；
- 对应 `Android/app/src/test/`、`Android/app/src/androidTest/`；
- `docs/Android/` 的 R2 交接、验证文字、Android/iOS 对照截图，以及必要的文档测试。

执行窗口可以读取 `source/` 和干净独立 iOS 工作区，复制／机械生成 Android 所需资源；不得修改
`source/`、`ios/`、`web/` 或共享 schema／fixtures。不得修改领域规则、P3-01 展示规则、Room、DataStore、
JSON、通知、导入导出或构建工具链，也不得新增第三方 UI／字体依赖。若现有边界不足，先回传具体原因，
不能自行扩大。

本任务不实现课程新增／编辑／删除、课程点击、周课表、完整设置、通知或导入导出，不进入 P3-04。
不得将视觉 R2 完成表述为 A02、A11、P3、完整 App 或用户验收完成。

## 八、执行、提交与独立复审

建议执行窗口使用 **Terra／高**：业务规则已经稳定，但本任务涉及共享视觉组件、图片资源、平台字体／
图标替代、多个尺寸和成对截图迭代，低档或只靠静态代码检查容易再次遗漏整体观感。执行完成后必须交回
**Sol／高**独立复审，因为这是用户明确指出的跨端视觉差异，且会成为后续核心页面的共同基础。

至少运行：

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

使用正确的 API 37 ARM64 AVD 时必须显式设置已验证的 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 和
`ANDROID_AVD_HOME=/Users/takagisan/.android/qingke-api37-r3-avd`，核对 `target=android-37`、HVF、SDK 37、
`arm64-v8a` 和唯一设备序列，避免再次命中历史 `target=android-0` 同名 AVD。测试后正常关闭设备。

提交只包含 P3-03-R2 文件，推送 `Android` 并核对本地、tracking ref 和远程同名分支一致。交接需写明
实际变更、原始素材如何进入 Android、iOS 对照方式、成对截图、自动化结果、平台差异和未实现的功能型
控件。最终独立复审通过后仍须由用户再次打开模拟器确认视觉；用户确认前不得开始 P3-04。
