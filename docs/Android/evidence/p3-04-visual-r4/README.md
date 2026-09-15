# P3-04 视觉 R4 局部对齐证据

本目录记录 2026-09-15 在 `qingke-api37-r3-arm`（API 37、`arm64-v8a`）以最终 Android debug APK 完成的生产入口证据。仅覆盖 chooser 的新建图标、课程成功通知和 TODAY 字体层级；它不表示 Sol 集中复审或用户视觉验收。

- `chooser-light-100.png`：有课程时打开 ADD chooser；22dp 平台 Canvas 图标的方框及横、竖十字共享中心，使用浅色正文前景。
- `today-light-100.png`：浅色 100% 的 TODAY 日期、课程序列和 ADD。
- `today-featured-light-100.png`：测试时钟 08:30 的浅色 100% featured card，显示 32/13 的 featured 时间、20sp 加粗课程名和 24/11 的 sequence 时间层级。
- `today-featured-dark-100.png`：深色 100% featured card。
- `today-featured-dark-130.png`：深色 `font_scale=1.3` featured card；日期、featured、sequence 和 tab/ADD 未裁切或重叠。
- `success-edit-light-100.png`：编辑保存后的代表性成功通知，精确文案为 `SYSTEM // 课程修改已保存`，并保留黄色左线、圆点、勾和 ADD/tab 堆叠。

自动化同时覆盖四条精确通知：`SYSTEM // 课程添加成功`、`SYSTEM // 添加上课安排成功`、`SYSTEM // 课程修改已保存`、`SYSTEM // 课程删除成功`；以及 chooser 图标浅/深色前景、几何中心、TODAY 规范和浅/深/130% 布局。最终完整验证结果以本轮交接为准。
