# MacBook Codex iOS 开发接续说明

## 1. 接续目标

这份文档供 MacBook 上新打开本仓库的 Codex 使用。当前主产品是 `ios/` 下的原生 iPhone 应用；`web/` 仅作为未来 Web 重做的空目录保留。目标是继续维护可重复的 Xcode 构建、测试和免费 Personal Team 真机安装流程。

不要只根据本文件开始写代码。必须先按下文顺序读取仓库事实来源，并在每个阶段执行测试和创建 Git commit。

## 2. 必读顺序

MacBook 上的 Codex 开始工作前必须依次完整阅读：

1. `AGENTS.md`：仓库级变更、测试和提交要求。
2. `docs/ios-product-design.md`：iOS MVP 产品范围和验收标准。
3. `docs/ios-technical-solution.md`：iOS 技术选型与实施约束。
4. `ios/Shared/schedule-data.schema.json`：`schemaVersion: 1` 数据协议。
5. `ios/QingKeSchedule/Domain/ScheduleDTOs.swift`：Swift Codable DTO。
6. `ios/QingKeSchedule/Domain/ScheduleRules.swift` 与 `ScheduleValidation.swift`：核心业务规则和校验行为。
7. `ios/QingKeScheduleTests/` 与 `ios/QingKeScheduleUITests/`：已验证行为。
8. `docs/product-design.md` 与 `docs/technical-solution.md`：已归档 Web MVP 的历史背景与架构边界。

长期有效的项目背景以已提交文件为准，不依赖 Windows 上原聊天记录。官方 OpenAI 文档也建议把长期项目指导保存在 `AGENTS.md` 或已提交文档中，供未来 Codex 任务读取。

## 3. 仓库交接状态

当前状态：

- iOS MVP 的共享协议、领域规则、SwiftData、主要页面、导入导出、本地通知和真机安装循环均已实现。
- `schemaVersion: 1` 的当前事实来源位于 `ios/Shared/`，Swift 实现与测试位于 `ios/`。
- 旧 Web MVP 曾在提交 `0b97a6d` 中实现，完整实现可从 Git 标签 `pre-ios-web-restructure` 恢复；工作树中的 `web/` 当前有意保持为空。
- Web 历史产品和技术文档继续保留在 `docs/`，供未来重做时参考。
- GitHub 仓库仍为 `https://github.com/SukiBanQin/School_timetable.git`。
- 仓库为 Public；只提交源代码、测试和设计文档，不提交个人课表导出文件、Apple 凭据或设备信息。

在 Mac 上不要假设以上状态仍未变化。先使用 Git 命令检查当前事实，尤其要确认 GitHub 推送后的最新提交和工作区是否干净。

## 4. MacBook 首次准备

### 4.1 用户需要完成

- 从 Mac App Store 安装当前稳定版 Xcode。
- 首次打开 Xcode，接受许可协议并安装所需 iOS Simulator runtime。
- 安装并登录 Codex。
- 使用 `git clone https://github.com/SukiBanQin/School_timetable.git` 克隆本仓库，不通过 iCloud Drive 复制活动中的 `.git` 目录。
- 在 Xcode 中登录免费 Apple Account；只做模拟器阶段时可以稍后登录。
- 真机测试时准备一台 iPhone 和连接线，或完成无线配对。

Apple Account 密码、双重认证码和证书私钥只能由用户处理，不得写入仓库、脚本、日志或 Codex 提示词。

### 4.2 Codex 第一次只读检查

先运行并记录结果：

```bash
pwd
git status --short
git branch --show-current
git log --oneline --decorate -10
git remote -v
xcodebuild -version
swift --version
xcrun simctl list devices available
```

如果仓库有与当前任务无关的未提交修改，必须保留并绕开，不得重置或覆盖。如果 Xcode 尚未完成首次初始化，应准确告诉用户需要在 Xcode 中完成什么操作，然后继续所有不依赖该步骤的工作。

## 5. 开发边界

### 5.1 必须遵守

- iOS 工程位于仓库根目录 `ios/`。
- 产品名暂用 `QingKeSchedule`；显示名称可保持“青课”或等待用户确认。
- 首版原生技术栈为 Swift、SwiftUI、SwiftData、Observation 和 UserNotifications。
- 默认最低部署版本为 iOS 17；先确认用户 iPhone 系统版本。
- 只支持 iPhone，不扩展 Android、iPad 专用界面、Widget 或云同步。
- `web/` 在新的 Web 方案确定前保持为空；重做 Web 时使用独立任务和提交。
- 所有日期、单双周和冲突行为以 `ios/Shared/` 的版本 1 协议及 Swift 测试为准，并保持对已归档 Web 导出文件的兼容。
- 每项改动都要更新测试、运行验证并创建对应 Git commit。

### 5.2 不得擅自决定

- 不替用户创建最终 Bundle Identifier；真机签名前询问或让用户确认。
- 不提交 `DEVELOPMENT_TEAM`、证书、Provisioning Profile、设备 UDID 或 Apple 凭据。
- 不安装 Homebrew 包、Tuist、XcodeGen 或第三方 Swift 包，除非现有工具无法满足明确任务并获得用户同意。
- 不改成 React Native、Flutter、Capacitor 或 WebView 壳。
- 不开启 iCloud、CloudKit、远程通知或其他付费账号能力。
- 不创建 App Store Connect 记录，不提交 TestFlight 或 App Store，除非用户明确要求。

## 6. 推荐首轮实施计划

首轮不要直接同时实现所有页面。按以下可独立验证的阶段推进：

### 阶段一：Xcode 工程与构建循环

- 在 `ios/` 创建 SwiftUI App target。
- 同时创建 Swift Testing unit test target 和 XCUITest target。
- 使用 `xcodebuild -list` 验证 scheme。
- 自动发现一个可用 iPhone Simulator，完成首次 build 和 test。
- 添加最小的 `ios/scripts/ios-build.sh` 和 `ios/scripts/ios-test.sh`。
- 提交一个只包含脚手架、脚本和测试的 commit。

### 阶段二：共享协议与领域规则

- 从当时的 TypeScript 类型提取 `ios/Shared/schedule-data.schema.json`。
- 建立跨实现共用的 JSON fixtures，现保存在 `ios/Shared/fixtures/`。
- Swift DTO 保持 `schemaVersion: 1` 字段完全兼容。
- 用 Swift 纯函数实现教学周、单双周、今日筛选、状态和冲突检测。
- 让 Swift Testing 和当时的 Vitest 对相同 fixtures 得出一致结论。
- 单独提交。

### 阶段三：持久化与基本页面

- 实现 SwiftData schema、仓库协议和应用状态。
- 依次完成首次使用、今日、课表、课程编辑和设置。
- 每个功能按测试闭环拆分提交，不堆积为一个巨大 commit。

### 阶段四：导入导出

- 为当时的 Web 增加兼容 JSON 导出并测试。
- iOS 实现文件导入、预览、确认替换和失败回滚。
- iOS 实现文件导出和系统分享。
- 使用真实 Web 导出文件完成交叉验证。

### 阶段五：通知与免费签名

- 通过协议封装 `UNUserNotificationCenter`。
- 实现稳定通知 ID 和滚动协调策略。
- 模拟器验证生成逻辑，真实 iPhone 验证投递和取消。
- 首次手动 Automatic Signing 成功后添加 `ios/scripts/ios-install.sh`。
- 记录免费描述文件到期后的重新构建步骤。

## 7. 构建与测试约定

不要硬编码模拟器名称或 UDID。先发现可用设备，再构造 destination。至少保留以下检查能力：

```bash
xcodebuild -list -project ios/QingKeSchedule.xcodeproj
xcrun simctl list devices available
xcodebuild build -project ios/QingKeSchedule.xcodeproj -scheme QingKeSchedule -destination "platform=iOS Simulator,id=<discovered-udid>"
xcodebuild test -project ios/QingKeSchedule.xcodeproj -scheme QingKeSchedule -destination "platform=iOS Simulator,id=<discovered-udid>"
```

实际参数以本机 Xcode 输出为准。脚本应启用严格错误处理、返回非零失败码，并把构建产物放在 Git 忽略目录。不要用脚本隐藏测试失败。

领域规则、导入导出、存储和通知协调必须可以在没有真实系统权限的情况下通过协议替身测试；系统通知真正投递、文件选择器和签名仍需真机或交互验证。

## 8. 免费 Apple Account 签名

### 8.1 首次配置

在完成模拟器版本后，再由用户在 Xcode 中：

1. 登录 Apple Account。
2. 选择 Personal Team。
3. 开启 Automatic Signing。
4. 确认最终用于个人测试的唯一 Bundle Identifier。
5. 连接 iPhone 并按提示启用开发者模式和信任。
6. 从 Xcode 首次成功运行。

不要尝试通过读取 Keychain、复制证书或保存登录令牌来自动化这一步。

### 8.2 后续七天续签

免费 Personal Team 的描述文件通常在 7 天后到期。首次真机运行成功后，安装脚本可以使用本机已有的 Automatic Signing 配置重新 build、install 和 launch，但不能保证在账号需要重新认证时完全无人值守。

保持 iPhone 解锁并连接后，在仓库根目录运行：

```bash
./ios/scripts/ios-install.sh
```

脚本通过 `devicectl` 动态发现唯一一台已配对、已启动且已连接的 iPhone，使用 Automatic Signing 覆盖构建和安装，并从构建产物读取 Bundle Identifier 后启动 App。它不会把设备标识、Team ID 或 Apple Account 凭据写入仓库；如果同时连接多台 iPhone，会要求断开多余设备而不是猜测目标。

重新签名失败时，先在 Xcode 的 Signing & Capabilities 中确认 Apple Account 仍已登录、Personal Team 和原 Bundle Identifier 仍然有效，再重新运行脚本。不要通过更换 Bundle Identifier 或删除 App 来规避签名错误。

每次覆盖安装应保持相同 Bundle Identifier。不得为了修复签名问题删除 App，因为删除会清除 SwiftData 本地课表；先导出备份，再处理确需删除的情况。

## 9. Git 与 GitHub 工作流

所有环境通过同一个 GitHub 仓库同步，不直接复制工作目录：

```text
未来 Web：仅在 web/ 内重做
             ↕ push / pull
GitHub 仓库：唯一共享提交历史
             ↕ push / pull
MacBook：ios/ 开发、Xcode 构建、模拟器和真机验证
```

每次开始工作先 `git pull --ff-only`，提交前运行 `git status --short`，只暂存本任务文件。推送前确认测试结果和提交信息。两台电脑不要同时修改同一分支的同一文件；并行工作时使用独立 `codex/` 功能分支。

MacBook 克隆完成后，在 Codex 中把仓库根目录设为本地项目主文件夹。Codex 会从主文件夹发现 `AGENTS.md` 和已提交文档，因此本文件和 iOS 技术方案就是跨设备接续的长期上下文。

当前远端仍指向同一仓库；本机可使用 SSH 地址，仓库主页为：

```text
https://github.com/SukiBanQin/School_timetable.git
```

## 10. 每个阶段的交付格式

Mac 上的 Codex 每次完成任务时应报告：

- 实现了什么。
- 修改了哪些关键文件。
- 运行了哪些构建和测试命令及结果。
- 是否执行了模拟器或真机验证。
- 新 Git commit 的哈希和消息。
- 仍存在的真实限制或需要用户完成的系统操作。

不能仅因为代码已经生成就宣布完成；必须有与风险相称的验证证据。

## 11. 给 MacBook Codex 的后续提示词

后续在 MacBook Codex 打开仓库根目录后，用户可以发送：

```text
请先完整阅读 AGENTS.md、docs/ios-product-design.md、docs/ios-technical-solution.md 和 docs/macbook-codex-handoff.md，再检查当前 Git 状态与 ios/ 下的实现和测试。只完成我本次指定的功能，保持 web/ 为空；自动发现可用的 iPhone Simulator，确保相关测试、iOS build 和 iOS test 通过，并按仓库要求创建独立 Git commit。不要提交任何 Apple Account、Team ID、证书或设备信息。
```

## 12. 官方参考

- [OpenAI Docs：项目和聊天](https://learn.chatgpt.com/zh-Hans/docs/projects)
- [OpenAI Docs：本地环境](https://learn.chatgpt.com/docs/environments/local-environment)
- [OpenAI Docs：使用 Codex 构建 iOS 应用](https://learn.chatgpt.com/use-cases/native-ios-apps)
- [Apple Developer：会员类型与免费 Personal Team 限制](https://developer.apple.com/support/compare-memberships/)
