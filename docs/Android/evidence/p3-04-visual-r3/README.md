# P3-04 视觉 R3 与首次设置月历证据

本目录记录 2026-09-15 在 `qingke-api37-r3-arm`（API 37、`arm64-v8a`）以最终修正代码完成的生产入口复拍。它是技术验证证据，不表示 Sol 独立审查或用户视觉验收。

- `onboarding-calendar-light-100.png`：清除生产 App 数据后的首次设置；页面内中文月历及清晰、无外框的黄色前后月 chevron。
- `today-light-100.png`：保存首次学期后的 TODAY；64dp ADD 的内框与右上折角均实际可见。
- `chooser-light-100.png`：TODAY ADD chooser；`plus.square` 使用黑色正文前景，cyan 仅保留为 panel 左线。
- `course-validation-light-100.png`：CREATE 空名称保存后的危险校验卡，含 coral 左线和图标。
- `course-success-light-100.png`：创建 `VisualR3` 后的成功通知、ADD 与 tab 栈。
- `danger-footer-light-100.png`：EDIT 页的删除按钮面板及其卡外危险说明 footer。
- `delete-modal-light-100.png`：浅色删除确认层；高不透明 modal surface 遮蔽背景内容。
- `delete-modal-dark-130.png`：深色及 `font_scale=1.3` 下的删除确认层，确认文字、按钮和不透明 surface 的可读性。
- `today-dark-130.png`：深色及 `font_scale=1.3` 下的 TODAY 与 ADD。

自动化：Debug／Release JVM 各 90 tests 通过；最终 `connectedDebugAndroidTest --rerun-tasks` 为 64 tests、0 failures/errors/skipped；`lintDebug` 为 0 errors、21 warnings。生产验证后已恢复 light／1.0，App 停在 TODAY，并保留 `VisualR3` 课程供后续目视验收。logcat 未发现 `FATAL EXCEPTION` 或目标 App ANR。
