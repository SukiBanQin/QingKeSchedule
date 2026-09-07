# QingKeSchedule Android

This directory is the native Android implementation. It currently contains the P1
build and domain/JSON-contract foundation, not a finished application. The shared
version-1 JSON schema and fixtures remain in `../ios/Shared/`; Android tests read
them directly and do not copy them.

## Pinned build toolchain

| Component | Pinned version |
| --- | --- |
| JDK / Kotlin JVM target | 17 |
| Gradle Wrapper | 8.10.2 |
| Android Gradle Plugin | 8.8.2 |
| Kotlin / Compose compiler plugin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Android SDK platform / Build Tools | android-35 / 35.0.0 |
| Compose BOM | 2024.12.01 |
| Kotlin serialization JSON | 1.7.3 |

API 35 is a reproducible **current-project** choice: Android's AGP 8.8 release
notes specify API 35 as its maximum supported API and specify Gradle 8.10.2,
Build Tools 35.0.0, and JDK 17. Android's Android 15 setup guide specifies
`compileSdk = 35` and `targetSdk = 35` and documents installing Platform 35 and
Build Tools 35.x.

As checked on 2026-09-07, this does **not** establish that API 35 still meets D02's
"latest stable SDK available in the implementation environment" rule: stable
Android tooling has moved past it. Moving the project to API 36.1 requires a
separate reviewed upgrade, at least AGP 9.0.x, Gradle 9.1.0, Build Tools 36.0.0,
and migration from the applied Kotlin Android Gradle plugin to AGP's built-in
Kotlin support. JDK 17 remains compatible. Do not make that upgrade as part of a
contract-only change.

Official references:

- [AGP 8.8 compatibility](https://developer.android.com/build/releases/agp-8-8-0-release-notes)
- [Android 15 / API 35 SDK setup](https://developer.android.com/about/versions/15/setup-sdk)
- [AGP 9.0 compatibility and migration changes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)

## Local setup

Install a JDK 17 and the Android SDK command-line tools (or Android Studio), then
install Platform 35 and Build Tools 35.0.0. Point Gradle at the SDK using exactly
one of the following approaches; do not commit a machine-specific `local.properties`.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"     # macOS example
export ANDROID_HOME="/absolute/path/to/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"                # optional compatibility alias
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Alternatively, create `Android/local.properties` locally with
`sdk.dir=/absolute/path/to/android-sdk`; that file is ignored by Git. On Windows,
set the equivalent environment variables and use `gradlew.bat`.

## Build, test, and reports

From this directory:

```bash
./gradlew assembleDebug
./gradlew test
```

The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. JVM HTML test reports are written to
`app/build/reports/tests/testDebugUnitTest/index.html` and
`app/build/reports/tests/testReleaseUnitTest/index.html`. Test XML is under
`app/build/test-results/`.

`ScheduleDataDecoderTest` reads the shared fixture manifest from
`../ios/Shared/fixtures/manifest.json`. It also records the current strict Android
unknown-field behavior; Swift currently accepts those fields, and that policy
difference is intentionally unresolved rather than a product decision. Duplicate
IDs and period-number ordering are likewise not tightened here.

No emulator or device is configured by this repository. A successful JVM build or
test does not prove installation or startup; use an authorized emulator/device for
that separate P1 verification.
