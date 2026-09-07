# 轻课 Android

`Android/` 是原生 Android 实现。本阶段只包含 P1 的 Compose 工程、领域规则和版本 1 JSON 契约骨架，不是完整 App。共享 schema 与 fixtures 保留在 `../ios/Shared/`，测试直接读取它们，不复制或修改共享文件。

## 当前固定工程组合

| 组件 | 当前版本 | 状态 |
| --- | --- | --- |
| JDK / Kotlin JVM target | 17 | 已用于构建 |
| Gradle Wrapper | 8.10.2 | 已固定 |
| Android Gradle Plugin | 8.8.2 | 已固定 |
| Kotlin / Compose compiler plugin | 2.0.21 | 已固定 |
| compileSdk / targetSdk | 35 | 当前实现，非 D02 最新稳定性结论 |
| minSdk | 26 | 用户已确认 |
| Android SDK Platform / Build Tools | android-35 revision 2 / 35.0.0 | 已在临时 SDK 中验证 |
| Compose BOM | 2024.12.01 | 已固定 |
| Kotlin serialization JSON | 1.7.3 | 已固定 |

## 通用环境配置

安装 JDK 17、Android Studio 或 Android command-line tools，并通过 `sdkmanager` 安装所需 SDK。不要提交机器相关的 `local.properties`；它已被 Git 忽略。

```bash
export JAVA_HOME="/absolute/path/to/jdk-17"
export ANDROID_HOME="/absolute/path/to/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

macOS 也可以使用 `export JAVA_HOME="$(/usr/libexec/java_home -v 17)"`。Windows 使用等价环境变量和 `gradlew.bat`。

## 构建、测试与报告

在 `Android/` 目录执行：

```bash
./gradlew assembleDebug
./gradlew test
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`；JVM 报告位于 `app/build/reports/tests/`，XML 结果位于 `app/build/test-results/`。

`ScheduleDataDecoderTest` 从 `../ios/Shared/fixtures/manifest.json` 读取全部有效和无效 fixture，并覆盖显式 `semester: null`、严格版本/字段/业务校验与 5 MiB 输入上限。未知字段当前按严格 schema 拒绝，而 Swift 实际宽容接受；该兼容策略仍未由产品决定。

## SDK 稳定渠道核查（2026-09-07）

实际查询命令：

```bash
"$ANDROID_HOME/cmdline-tools/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --list
```

稳定渠道清单显示已安装 `platforms;android-35`，同时可用稳定平台至少包含 `android-36`、`android-36.1`、`android-37.0`、`android-37.1`（另有 beta 条目）。因此 API 35 只能作为当前可复现组合，不能声称满足 D02“环境可用的最新稳定版本”。

当前 AGP 8.8.2 的官方兼容说明支持 API 35、Gradle 8.10.2 和 JDK 17。若要跟随稳定渠道升级，建议先由 Astra 审查最小组合：目标 API 取查询结果中的最高稳定平台，配套同代稳定 AGP/Gradle、Build Tools 和 Kotlin/Compose 插件；这会影响 `compileSdk`/`targetSdk`、插件迁移、缓存和 CI，故本任务不实施升级。API 36.1/AGP 9 仅是待审查方向，不是已核实结论。

官方依据：

- [AGP 8.8 release notes](https://developer.android.com/build/releases/agp-8-8-0-release-notes)
- [Android SDK setup](https://developer.android.com/about/versions/15/setup-sdk)
- [SDK command-line tools](https://developer.android.com/tools/sdkmanager)

## P1-02 启动验证限制

本机为 Apple Silicon（aarch64）。API 35 `google_apis;x86_64` system image 可列出但不能由本机 QEMU2 启动，错误为 `Avd's CPU Architecture 'x86_64' is not supported by the QEMU2 emulator on aarch64 host`。ARM64 image 在当前网络中未完成下载。因此本任务未取得模拟器或真机安装、冷启动、重启、截图和 logcat 崩溃证据；不得将构建通过写成启动通过。详见 `../docs/Android/p1-02-validation.md`。
