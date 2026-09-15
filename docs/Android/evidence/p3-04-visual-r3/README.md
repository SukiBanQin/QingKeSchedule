# P3-04 视觉 R3 与首次设置月历证据

本目录记录 2026-09-15 在 `qingke-api37-r3-arm`（API 37、`arm64-v8a`）完成的生产入口复拍。它是技术验证证据，不表示 Sol 独立审查或用户视觉验收。

- `onboarding-calendar-light-100.png`：清除生产 App 数据后的首次设置；开始日期已展开为页面内中文月历。
- `today-light-100.png`：保存首次学期后的 TODAY 与 64dp ADD。
- `course-validation-light-100.png`：CREATE 路径的校验状态复拍。
- `course-success-light-100.png`：编辑保存后 TODAY 的成功通知、ADD 与 tab 栈。
- `today-dark-130.png`：深色及 `font_scale=1.3` 下的 TODAY 与 ADD。

自动化：Debug／Release JVM 各 90 tests 通过；`connectedDebugAndroidTest --rerun-tasks` 为 63 tests、0 failures/errors/skipped；`lintDebug` 为 0 errors、21 warnings。生产验证后已恢复 light／1.0，App 停在 TODAY，并保留 `VisualR3` 课程供后续目视验收。logcat 未发现 `FATAL EXCEPTION` 或目标 App ANR。
