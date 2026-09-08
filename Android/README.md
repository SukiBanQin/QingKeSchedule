# 轻课 Android

`Android/` 是原生 Android 实现。本阶段只包含 P1 的 Compose 工程、领域规则和版本 1 JSON 契约骨架，不是完整 App。共享 schema 与 fixtures 保留在 `../ios/Shared/`，测试直接读取它们，不复制或修改共享文件。

## 当前固定工程组合

| 组件 | 当前版本 | 状态 |
| --- | --- | --- |
| JDK / Kotlin JVM target | 17 | 已用于构建 |
| Gradle Wrapper | 9.6.0 | 已固定 |
| Android Gradle Plugin | 9.4.0 | 已固定 |
| built-in Kotlin / Compose / serialization plugin | 2.2.10 | 与 AGP 运行时 KGP 对齐 |
| compileSdk / targetSdk | 37（API 37.0） | P1-03 已授权目标 |
| minSdk | 26 | 用户已确认 |
| Android SDK Platform / Build Tools | android-37.0 / 36.0.0 | AGP 9.4 官方兼容组合 |
| Compose BOM | 2024.12.01 | 已固定 |
| Kotlin serialization JSON | 1.7.3 | 已固定 |

AGP 9 默认启用 built-in Kotlin，因此工程不再应用
`org.jetbrains.kotlin.android`。Compose compiler 与 serialization 编译插件保留，
并使用 AGP 9.4 运行时所带 KGP 的 2.2.10 版本。为继续执行 Debug／Release
双变体 JVM 测试，`gradle.properties` 显式关闭
`android.onlyEnableUnitTestForTheTestedBuildType` 默认限制。

## 通用环境配置

安装 JDK 17、Android Studio 或 Android command-line tools，并通过 `sdkmanager` 安装所需 SDK。不要提交机器相关的 `local.properties`；它已被 Git 忽略。

```bash
export JAVA_HOME="/absolute/path/to/jdk-17"
export ANDROID_HOME="/absolute/path/to/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
```

macOS 也可以使用 `export JAVA_HOME="$(/usr/libexec/java_home -v 17)"`。Windows 使用等价环境变量和 `gradlew.bat`。

## 构建、测试与报告

在 `Android/` 目录执行：

```bash
./gradlew --version
./gradlew clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest
./gradlew lintDebug
./gradlew --offline clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest
```

Debug/Release APK 位于 `app/build/outputs/apk/`；JVM 报告位于
`app/build/reports/tests/`，XML 结果位于 `app/build/test-results/`，lint 报告位于
`app/build/reports/lint-results-debug.html`。

`ScheduleDataDecoderTest` 从 `../ios/Shared/fixtures/manifest.json` 读取全部有效和无效 fixture，并覆盖显式 `semester: null`、严格版本/字段/业务校验与 5 MiB 输入上限。未知字段当前按严格 schema 拒绝，而 Swift 实际宽容接受；该兼容策略仍未由产品决定。

## API 37 工具链依据

实际查询命令：

```bash
"$ANDROID_HOME/cmdline-tools/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --list
```

稳定渠道可安装 `platforms;android-37.0`。Android Developers 的 AGP 9.4
兼容表明确其最高支持 API 37，要求 Gradle 9.6.0、Build Tools 36.0.0 和
JDK 17。官方 built-in Kotlin 迁移说明要求 AGP 9 工程移除
`org.jetbrains.kotlin.android`。这组参数已获 P1-03 授权；构建与设备验证结果
须以本任务交接记录为准，不能由版本声明反推成功。

官方依据：

- [AGP 9.4 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Compose compiler plugin](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [SDK command-line tools](https://developer.android.com/tools/sdkmanager)

## 设备验证边界

仓库不提交 AVD 或设备配置。P1-03 必须使用 API 37 ARM64 模拟器或等效设备
重新验证安装、两次冷启动、Activity／进程和无崩溃 logcat；历史 API 35
启动证据不能替代本轮结果。
