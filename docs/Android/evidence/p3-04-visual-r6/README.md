# P3-04 视觉 R6 证据

2026-09-15，API 37 ARM64 `qingke-api37-r3-arm` 的最终 debug APK 生产入口截图。范围仅为用户授权的 TODAY 日期和 ADD、ADD chooser 及课程编辑器 RGB 滑块；不代表 Sol 集中技术复审或用户视觉验收。

- `today-light-100.png`：浅色 100% TODAY；日期数字为实际 weight 100 condensed face，64dp ADD 没有黑色内框，黄色表面右上角为半透明白色折角和轻影。
- `today-dark-100.png`：深色 100% TODAY；日期、ADD 和其点击位置保持可见。
- `today-dark-130.png`：深色 `font_scale=1.3` TODAY；日期没有裁切，ADD 仍保持 64dp 视觉与折角。
- `chooser-light-100.png`：浅色 100% ADD chooser；15% 圆角 22dp 图标、居中加号和细体“新建一门课程”可见。
- `rgb-sliders-light-100.png`：浅色 100% RGB 滑块；`R 040`、`G 123`、`B 116` 分别与其滑轨保持单行同一基线。
- `rgb-sliders-light-130.png`：浅色 `font_scale=1.3` RGB 滑块；三行标签、三位数和滑轨均未换行、裁切或挤没。

截图已逐张目视核验。自动化还以像素回归证明 ADD 的旧黑框消失、白色半透明折角与可区分阴影存在；chooser 继续验证加号中心，并验证圆角角点无墨、边缘有墨；RGB 回归验证三行同基线和拖动后红色通道写回。最终模拟器恢复为 light／`font_scale=1.0`、保留 `VisualR5`，停在 TODAY，供用户查看。
