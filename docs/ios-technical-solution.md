# 课表软件 iOS MVP 技术方案

## 1. 文档目的

本文档依据[iOS MVP 产品设计文档](./ios-product-design.md)，并结合仓库中已经完成的 Web MVP，明确原生 iPhone 应用的技术选型、模块边界、跨端数据协议、本地通知、导入导出、测试和个人免费签名方案。

本文档是 iOS 实施基线。实际开发应同时遵守仓库根目录的 `AGENTS.md`，每次改动都必须补充或更新测试、完成验证并创建独立 Git commit。

## 2. 当前项目基线

仓库当前已经包含可运行的 Web MVP，而不只是产品文档。iOS 实现必须先阅读并对齐以下现有代码：

- `src/domain/types.ts`：Web 端领域类型和跨端数据字段的当前事实来源。
- `src/domain/rules.ts`：教学周、单双周、课程筛选、状态和冲突检测规则。
- `src/domain/validation.ts`：学期与课程输入校验。
- `src/storage/localStorageRepository.ts`：`schemaVersion: 1` 数据解析和本地保存格式。
- `tests/unit/rules.test.ts`：核心规则的已验证样例。
- `tests/unit/storage.test.ts`：数据格式的已验证样例。

Web 端目前尚未实现课表文件导出。iOS 可以先支持手动创建课表，但在“从 Web 导入 iOS”的端到端验收前，需要为 Web 版另行增加兼容导出功能，并以独立变更提交。

## 3. 技术选型结论

### 3.1 推荐技术栈

- **Swift**：应用和领域逻辑的主要语言。
- **SwiftUI**：今日、课表、设置、课程表单和导航界面。
- **SwiftData**：学期、节次、课程和上课安排的设备本地持久化。
- **Observation**：使用 `@Observable` 管理应用级状态和依赖协调。
- **UserNotifications**：请求通知权限并安排、查询和取消本地上课提醒。
- **Codable + Foundation**：实现与 Web 版兼容的 JSON 数据文件。
- **SwiftUI fileImporter / fileExporter / ShareLink**：接入“文件”应用和系统分享面板。
- **Swift Testing**：领域逻辑、导入导出和存储集成测试。
- **XCTest / XCUITest**：关键界面流程测试。
- **xcodebuild、simctl 和 devicectl**：命令行构建、模拟器和真机开发循环。
- **Swift Package Manager**：未来确有必要时管理依赖；MVP 默认不引入第三方包。

### 3.2 平台范围

- 首版只支持 iPhone，不建设 iPad 专用布局。
- 建议最低部署版本为 iOS 17，以直接使用 SwiftData 和 Observation。
- 在 Mac 上开始工程时先检查用户 iPhone 的实际系统版本；可以提高最低版本，但未经用户确认不降低到 iOS 16 或改用 Core Data。
- 使用当前稳定版 Xcode 和 Xcode 自带 Swift 工具链，不在文档中锁定易过时的小版本号。
- 应用以竖屏体验为主，但不应通过代码强制锁定方向，除非真机验证后有明确理由。

### 3.3 明确不采用的方案

- 不使用 React Native、Flutter 或 Capacitor，因为当前没有 Android 计划，且提醒、文件选择、分享和未来 Widget 都更适合原生实现。
- 不把现有 Web 页面直接包装进 WKWebView。
- 不建设登录、服务端 API、云数据库或 APNs 远程推送。
- 不为了复用 TypeScript 而在 iOS 中嵌入 JavaScript 引擎。
- 不在 MVP 初期引入大型架构框架、依赖注入框架或第三方数据库。

## 4. 总体架构

采用轻量的分层架构。SwiftUI 界面只负责展示和交互，业务规则保持为纯 Swift，系统能力通过协议隔离以便测试。

```text
SwiftUI Views
      ↓
AppStore / Feature Models（@Observable，@MainActor）
      ↓
Domain Models + Pure Rules
      ↓
Repositories / Notification Scheduler / Import-Export Services
      ↓
SwiftData / UserNotifications / File System
```

### 4.1 界面层

负责 `TabView`、`NavigationStack`、表单、列表、课表网格、弹窗、空状态和错误提示。界面不得直接操作 SwiftData `ModelContext`、`UNUserNotificationCenter` 或安全作用域文件 URL。

### 4.2 应用层

使用一个标记为 `@MainActor` 的 `@Observable` 应用状态对象协调：

- 启动加载和首次使用状态。
- 当前学期、课程列表和所选教学周。
- 新增、修改、删除课程。
- 导入确认、替换数据和导出。
- 保存成功后的通知重建。
- 系统权限、存储或文件错误到用户提示的转换。

应用状态依赖协议而不是系统单例，使单元测试可以注入内存仓库、固定时钟和伪通知调度器。

### 4.3 领域层

使用不依赖 SwiftUI、SwiftData 和 UserNotifications 的 Swift 值类型与纯函数实现：

- 教学周和教学周日期计算。
- 每周、单周、双周规则。
- 一门课程的多个上课安排。
- 今日课程筛选、排序和状态。
- 课程冲突检测。
- 下一次及未来课程发生时间计算。
- 学期、节次和课程数据校验。

### 4.4 基础设施层

基础设施实现以下协议：

- `ScheduleRepository`：读取、整体替换和保存课表数据。
- `NotificationScheduling`：读取权限、安排、查询和取消课表通知。
- `ScheduleFileCoding`：编码、解析并验证跨端 JSON 文件。
- `Clock`：提供当前时间，生产环境使用系统时钟，测试使用固定时钟。

## 5. 建议工程结构

iOS 项目放在仓库根目录的 `ios/` 下，与现有 Web 工程并存：

```text
ios/
  QingKeSchedule.xcodeproj/
  QingKeSchedule/
    App/
    Domain/
    Data/
    Notifications/
    ImportExport/
    Features/
      Today/
      WeekSchedule/
      CourseEditor/
      Settings/
      Onboarding/
    SharedUI/
    Resources/
  QingKeScheduleTests/
  QingKeScheduleUITests/
  Config/
scripts/
  ios-build.sh
  ios-test.sh
  ios-install.sh
shared/
  fixtures/
  schedule-data.schema.json
```

初次实现时不要求一次创建所有空目录；目录应在出现对应代码时建立。Xcode 工程文件需要提交到 Git。用户专属的 Team ID、证书、设备标识和本地签名配置不得提交。

## 6. 跨端数据协议

### 6.1 版本 1 的事实来源

在正式创建 `shared/schedule-data.schema.json` 前，版本 1 以 Web 端 `src/domain/types.ts` 和 `src/storage/localStorageRepository.ts` 为准。iOS 的 `Codable` DTO 必须保留以下字段名和枚举原始值：

```swift
enum RepeatRule: String, Codable {
    case every
    case odd
    case even
}

struct ScheduleDataDTO: Codable {
    let schemaVersion: Int
    let semester: SemesterDTO?
    let courses: [CourseDTO]
    let updatedAt: String
}
```

对应 JSON 结构为：

```json
{
  "schemaVersion": 1,
  "semester": {
    "id": "UUID string",
    "name": "2026 秋季学期",
    "startDate": "2026-09-01",
    "totalWeeks": 18,
    "periods": [
      {
        "number": 1,
        "startTime": "08:00",
        "endTime": "08:45"
      }
    ]
  },
  "courses": [
    {
      "id": "UUID string",
      "name": "高等数学",
      "teacher": "张老师",
      "color": "#287B74",
      "schedules": [
        {
          "id": "UUID string",
          "dayOfWeek": 1,
          "startPeriod": 1,
          "endPeriod": 2,
          "startWeek": 1,
          "endWeek": 18,
          "repeat": "every",
          "classroom": "教学楼 101"
        }
      ]
    }
  ],
  "updatedAt": "2026-09-02T12:00:00.000Z"
}
```

### 6.2 协议约定

- `schemaVersion` 当前必须为整数 `1`。
- ID 均为非空字符串；新数据使用 UUID 字符串。
- `dayOfWeek` 使用 `1...7`，分别表示星期一到星期日。
- `startDate` 固定为 `yyyy-MM-dd`，表达本地日历日期，不进行 UTC 日期偏移。
- `startTime` 和 `endTime` 固定为 `HH:mm` 24 小时制。
- `updatedAt` 使用 ISO 8601 UTC 时间字符串。
- `repeat` 只能是 `every`、`odd`、`even`。
- 颜色使用 Web 端允许的十六进制色值集合，不能把任意字符串作为 SwiftUI 颜色解析。
- 解码成功不代表数据有效；导入时仍需执行与 Web 端等价的业务校验。
- 未知 `schemaVersion` 必须拒绝导入并显示可理解的错误，不能静默按当前版本解释。

### 6.3 iOS 专属设置

提醒开关、提前分钟数、首次引导状态等 iOS 专属设置不加入版本 1 跨端文件，使用 `UserDefaults` 保存：

- `remindersEnabled: Bool`，默认 `false`。
- `reminderLeadMinutes: Int`，默认 `10`。
- `hasCompletedOnboarding: Bool`。

导入 Web 课表时保留当前 iOS 提醒设置，并在数据替换成功后重新协调通知。

### 6.4 共享契约测试

在实现导入导出前建立 JSON Schema 和共享 fixtures，至少包含：

- 有效的完整课表。
- `semester: null` 的空课表。
- 每周、单周、双周安排。
- 一门课程的多个安排。
- 边界周和冲突课程。
- 缺字段、非法日期、非法枚举和未知版本。

Web Vitest 和 iOS Swift Testing 必须读取同一组 fixtures，并对有效/无效结论和关键规则结果给出一致判断。

## 7. 本地持久化

### 7.1 SwiftData 模型

使用 SwiftData 保存：

- 一个当前学期。
- 学期下有序的节次列表。
- 课程列表。
- 每门课程下的多个上课安排。

删除课程时级联删除其安排。替换导入数据应在一个受控操作中完成：先完整解码和校验，在内存中构造新数据，确认无误后再替换当前 SwiftData 数据，任何失败都保留原课表。

### 7.2 仓库边界

SwiftUI 页面不直接执行查询和保存。`SwiftDataScheduleRepository` 将 SwiftData 模型映射为领域模型，对应用层提供：

- `load() throws -> ScheduleData`
- `replace(with:) throws`
- `saveSemester(_:) throws`
- `saveCourse(_:) throws`
- `deleteCourse(id:) throws`

测试使用内存 `ModelContainer` 或 `InMemoryScheduleRepository`。SwiftData schema 发生变化时使用显式版本和迁移计划，不依赖页面层兼容旧数据。

### 7.3 数据安全

- 本地写入失败必须显示错误，不得假装保存成功。
- 导入替换必须二次确认。
- 导出失败必须保留当前数据并提供重试。
- 不将课表、教师、教室或 Apple Account 信息发送到网络。
- 卸载 App 可能清除本地数据，设置页需要提示定期导出备份。

## 8. 核心业务规则

### 8.1 日历约定

- 使用 Gregorian Calendar。
- 每周固定从星期一开始，不依赖设备地区的 `firstWeekday` 默认值。
- 学期开始日期所在周为第 1 周，即使开始日期不是星期一。
- 日期早于第 1 周或晚于 `totalWeeks` 时视为学期外。
- 日历运算使用 `Calendar` 和本地时区，不用固定秒数跨越夏令时。

### 8.2 重复与课程发生

安排在某周生效需同时满足周次范围和重复规则：

- `every`：范围内每周。
- `odd`：范围内奇数教学周。
- `even`：范围内偶数教学周。

今日课程按开始节次、结束节次、课程名称稳定排序。状态边界与 Web 一致：早于开始时间为未开始，开始时间至结束时间（包含结束分钟）为进行中，之后为已结束。

### 8.3 冲突检测

两个安排只有在星期相同、节次范围相交，并且存在至少一个同时满足双方周次范围和单双周规则的教学周时才冲突。保存前展示冲突课程和周次，用户确认后仍允许保存。

### 8.4 跨端一致性

Swift 规则实现不是对 TypeScript 源码逐行翻译，而是对同一行为契约的独立实现。所有边界规则必须用共享 fixtures 与 Web 测试交叉验证。

## 9. 本地通知方案

### 9.1 权限流程

- 首次启动不立即请求通知权限。
- 用户主动开启提醒时，先解释用途，再调用 `UNUserNotificationCenter` 请求 `.alert` 和 `.sound` 权限。
- 每次安排通知前读取当前授权状态，因为用户可以在系统设置中随时修改权限。
- 拒绝或关闭权限后，课表其他功能继续正常使用，并显示前往系统设置的入口。

### 9.2 通知内容与标识

每个未来课程发生生成一次性日历通知：

- 标题：课程名称。
- 正文：上课时间和教室；教室为空时不显示多余分隔符。
- 触发时间：课程开始时间减去统一提前分钟数。
- 标识建议：`schedule.<courseId>.<scheduleId>.week.<teachingWeek>`。

稳定标识允许课程修改或删除时精确取消旧通知。不要仅依赖重复星期触发器，因为起止周和单双周规则会导致学期外错误提醒。

### 9.3 通知协调

在以下时机执行 reconcile：

- 应用启动并加载数据后。
- 应用从后台回到前台。
- 新增、修改或删除课程成功后。
- 学期和节次设置变更后。
- 导入数据替换成功后。
- 提醒开关或提前分钟数变化后。

协调器读取带本应用前缀的待发送通知，取消已经无效的项目，按时间顺序补充未来通知。为适应系统待发送通知数量限制，首版只维护最近的固定数量，例如 60 条，并在应用进入前台时滚动补充。具体上限和长期不打开 App 时的表现必须在当前 iOS 真机验证并记录。

关闭提醒、权限不可用或学期结束时，取消所有带本应用前缀的待发送通知。通知调度失败必须记录可诊断信息，但不能破坏已经保存的课程数据。

### 9.4 测试边界

模拟器和单元测试验证通知请求的日期、内容、标识和取消集合；可靠投递、权限变化、锁屏显示、时区变化和设备重启后的行为必须在真实 iPhone 上验证。

## 10. 导入导出方案

### 10.1 文件格式

- MVP 使用 UTF-8 JSON，建议文件名为 `qingke-schedule-YYYY-MM-DD.json`。
- JSON 内容就是版本化 `ScheduleDataDTO`，不导出 SwiftData 数据库文件。
- 编码输出使用稳定字段和 ISO 8601 `updatedAt`。
- 文件中不包含通知授权状态、Apple Account、设备信息或其他隐私数据。

### 10.2 导入

通过 SwiftUI `fileImporter` 选择单个 JSON 文件：

1. 获取安全作用域访问权并确保及时释放。
2. 限制文件大小，防止异常大文件造成内存压力。
3. 解码 JSON 并检查 `schemaVersion`。
4. 执行全部业务校验。
5. 展示学期名称、课程数量和更新时间摘要。
6. 用户确认“替换当前课表”后执行原子替换。
7. 保存成功后更新界面并重建通知。

取消、解析失败或校验失败都不能修改当前课表。

### 10.3 导出

把当前领域数据转换为 DTO，通过 `fileExporter` 或临时文件配合 `ShareLink` 交给系统分享面板。导出后保持 App 内数据不变，并对没有学期、磁盘错误和分享取消提供清晰反馈。

## 11. 页面技术方案

### 11.1 应用导航

根界面使用 `TabView`：

1. 今日：默认选中。
2. 课表：教学周浏览。
3. 设置：学期、节次、提醒和数据管理。

每个 Tab 内使用独立 `NavigationStack`。课程详情和设置子页面通过值类型路由进入；新增或编辑表单可使用系统 sheet，但必须正确处理未保存退出确认。

### 11.2 今日课程

- 使用可滚动列表展示当天课程。
- 顶部突出下一门课程。
- 使用系统语义颜色和文字同时表达已结束、进行中和未开始。
- 通过固定时钟依赖生成状态，避免 View 内散落 `Date()` 调用。

### 11.3 周课表

- 优先采用按日期横向分页或横向滚动的手机布局。
- 节次纵向排列，课程块按起止节次计算高度。
- 冲突课程需要可见提示并保证都可点击。
- 不照搬桌面七列压缩网格。

### 11.4 表单与无障碍

- 使用系统 `Form`、`DatePicker`、`Picker`、`Toggle` 和确认对话框。
- 适配 Dynamic Type、VoiceOver、安全区域、浅色和深色模式。
- 可点击区域满足 iOS 常规触控尺寸。
- 颜色不作为课程状态或错误的唯一表达方式。
- 所有删除和数据替换操作都需要明确确认。

## 12. 测试与质量保障

### 12.1 Swift Testing 单元测试

重点覆盖：

- 星期一开周和学期开始日期位于任意星期的教学周计算。
- 学期前后、第一周和最后一周边界。
- 每周、单周、双周和多个安排。
- 今日课程排序和三个状态的时间边界。
- 节次、周次和重复规则组合下的冲突。
- JSON 编解码、未知版本、缺字段和非法数据。
- SwiftData 内存容器的保存、删除和替换回滚。
- 通知标识、触发时间、滚动窗口和取消集合。

### 12.2 XCUITest 流程测试

至少覆盖：

1. 首次启动后手动创建学期和第一门课程。
2. 今日与周课表显示一致。
3. 新增、修改和删除课程后界面同步。
4. 冲突提示允许取消或继续。
5. 导入有效文件、拒绝无效文件和取消替换。
6. 导出文件入口可用。
7. 提醒权限被拒绝时 App 仍可使用。

### 12.3 真机检查

- 首次通知授权、拒绝后说明和跳转设置。
- 应用在后台或未打开时的本地通知。
- 修改、删除课程后旧通知不再触发。
- 从“文件”应用导入并通过系统分享面板导出。
- 免费 Personal Team 签名后的安装和重新签名。
- 深色模式、动态字体、VoiceOver 和小屏 iPhone。

### 12.4 每次提交的验证

Mac 环境建立后，至少执行：

```text
xcodebuild build（iOS Simulator）
xcodebuild test（单元与集成测试）
```

涉及导航和界面流程时运行 UI tests；涉及通知、文件或签名时补充真实 iPhone 验证。所有命令应由脚本发现实际 scheme 和 simulator，不把某台设备 UDID 写入仓库。

## 13. 构建与免费个人签名

### 13.1 模拟器

模拟器构建不依赖 Apple Developer Program。开发脚本应先使用 `xcodebuild -list` 获取 scheme，再通过 `xcrun simctl list devices available` 选择可用模拟器，避免假设某个 iPhone 型号一定存在。

### 13.2 真机首次配置

首次真机安装需要用户完成：

1. 在 Xcode 登录免费 Apple Account。
2. 为 App target 选择 Personal Team 和 Automatic Signing。
3. 确认唯一 Bundle Identifier。
4. 连接或配对 iPhone，并按系统提示启用开发者模式。
5. 在设备上处理信任和通知权限提示。

这些账号和设备操作不能由脚本绕过。首次从 Xcode 成功运行后，再建立 `scripts/ios-install.sh` 封装后续构建和安装命令。

### 13.3 七天重新签名

免费 Personal Team 的描述文件有效期为 7 天。脚本的目标是减少重复操作，不是绕过 Apple 限制：

- 检查 Xcode 和已配对设备。
- 使用 Automatic Signing 重新构建。
- 在需要时允许 Xcode 更新描述文件。
- 安装并启动相同 Bundle Identifier 的 App。
- 失败时输出下一步可执行的诊断信息。

脚本不得保存 Apple Account 密码、双重认证码、证书私钥或设备秘密。用户删除 App 会删除其本地课表，因此重新签名前应优先保持覆盖安装，并定期导出备份。

## 14. 建议实施顺序

1. 在 MacBook 安装并初始化 Xcode，确认模拟器和命令行构建可用。
2. 在 `ios/` 创建 SwiftUI App、Swift Testing 和 UI Testing targets。
3. 建立跨端 JSON Schema 与共享 fixtures，并为 Web 补充导出能力。
4. 用 Swift 纯函数实现领域模型、校验和业务规则，通过共享 fixtures。
5. 实现 SwiftData 仓库和应用级状态。
6. 完成首次使用、今日、周课表、课程编辑和设置页面。
7. 实现导入导出并做替换安全测试。
8. 实现本地通知协调器并进行真机验证。
9. 建立模拟器构建、测试和免费签名安装脚本。
10. 完成响应式、无障碍、深色模式和完整验收。

每一步使用独立、可回滚的 Git commit，不把整个 iOS MVP 压在一个提交中。

## 15. 主要风险与应对

| 风险 | 应对 |
| --- | --- |
| Web 与 iOS 数据字段逐渐不一致 | JSON Schema、共享 fixtures 和两端契约测试 |
| 日历与时区导致教学周偏移 | 固定星期一规则，使用 Calendar 做日期运算，覆盖时区测试 |
| 待发送通知数量受系统限制 | 按时间滚动维护最近通知，前台时补充，真机记录行为 |
| 课程修改后旧通知残留 | 稳定通知 ID，加统一 reconcile 流程 |
| 导入破坏当前课表 | 先解码校验和预览，确认后原子替换，失败回滚 |
| 免费签名到期 | 保持相同 Bundle Identifier，提供重新构建安装脚本和备份提醒 |
| Codex 在 Windows 修改 iOS 后无法验证 | iOS 代码只在具有 Xcode 的 Mac 环境中完成和提交 |

## 16. 完成定义

iOS MVP 只有在以下条件同时满足时才算完成：

- iOS 产品文档中的 MVP 验收标准全部满足。
- 与 Web `schemaVersion: 1` 数据文件双向兼容。
- 所有 Swift Testing、集成测试和 UI tests 通过。
- 模拟器构建稳定通过。
- 本地通知、导入导出和免费签名在真实 iPhone 验证通过。
- 没有把 Apple 凭据、设备 UDID 或本地签名文件提交到 Git。
- 每项改动都有对应测试和独立 Git commit。

## 17. 官方参考

- [Apple SwiftData ModelContainer](https://developer.apple.com/documentation/swiftdata/modelcontainer)
- [Apple UserNotifications](https://developer.apple.com/documentation/usernotifications)
- [Apple 通知权限](https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications)
- [Apple SwiftUI ShareLink](https://developer.apple.com/documentation/swiftui/sharelink)
- [Apple Swift Testing](https://developer.apple.com/documentation/testing)
- [Apple 免费与付费开发者会员对比](https://developer.apple.com/support/compare-memberships/)
- [OpenAI Docs：使用 Codex 构建 iOS 应用](https://learn.chatgpt.com/use-cases/native-ios-apps)
