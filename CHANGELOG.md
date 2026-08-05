# Changelog

[中文](#中文) · [English](#english) · [返回中文 README](README.md) · [English README](README_EN.md)

## 中文

### v2.0.0 · 2026-08-05 · 正式版

这是 MiFreeformUnbounded 的完整 2.0 正式版本，重点完成了 HyperOS 3 自由窗口边界策略、Modern LibXposed API 102 接入，以及基于 Compose/MIUIX 的管理界面重构。

#### 核心能力

- 完成 HyperOS 3 自由窗口边界保护逻辑。
- 增加可配置的最小可见边缘距离，默认 `196px`，范围 `8px–320px`。
- 支持横向拖动、稳定偏移、动画目标和最终边界的精确处理。
- 使用 `Rect`、参数类型、返回类型和方法签名进行 Hook 前校验。
- 未匹配兼容规则时保留系统原行为，并输出限频诊断日志。
- 支持 Modern LibXposed API 102、静态作用域和 Hook 热重载。

#### 管理界面

- 使用 Kotlin、Jetpack Compose 和 MIUIX 重构首页、设置页和关于页。
- 增加模块状态卡片：工作中、待重启、未激活三种状态。
- Tab2 设置页在模块未激活时仍可进入。
- 未激活时边缘调节卡片置灰，点击显示“模块未激活”，避免误修改配置。
- 完成边缘距离滑条和精细调节逻辑。
- 增加 SystemUI 重启入口和实时模块状态刷新。
- 增加关于页版本号、项目链接、参考项目和许可证信息。

#### 主题与交互

- 支持跟随系统、浅色、深色和 AMOLED 相关主题逻辑。
- 接入系统 Monet、强调色、调色板风格和色彩规范选项。
- 增加 KernelSU 风格悬浮底栏。
- 支持模糊、液态玻璃效果和导航栏状态角标。
- 支持 Android 新版本预测性返回手势。
- 三态状态卡保持稳定的语义图标颜色，避免状态图标被 Monet 意外染色。

#### 工程与发布

- 版本名：`2.0.0`。
- 版本号：`20260805`。
- 更新模块元数据 `module.prop`。
- 增加中英文 README，并提供双向 Markdown 超链接定位。
- 增加完整构建、安装、排障、兼容性和贡献说明。
- 完成 Release APK 构建与正式版发布准备。

#### 已知限制

- HyperOS 不同小版本可能变更自由窗口内部类名或方法签名；模块会安全跳过不匹配规则。
- 某些设备需要完整重启，而不是仅重启 SystemUI，才能重新加载静态作用域。

## English

### v2.0.0 · 2026-08-05 · Stable

MiFreeformUnbounded 2.0 is the complete stable release focused on HyperOS 3 freeform-window edge policies, Modern LibXposed API 102 integration, and a rebuilt Compose/MIUIX management UI.

#### Core

- Implemented HyperOS 3 freeform-window boundary protection.
- Added a configurable minimum visible edge distance with a `196px` default and an `8px–320px` range.
- Added targeted handling for horizontal dragging, stable offsets, animation targets, and final bounds.
- Added pre-hook validation for classes, methods, parameter types, return types, and signatures.
- Preserved stock behavior and emitted rate-limited diagnostics when a rule does not match.
- Added Modern LibXposed API 102, static scopes, and hook hot-reload support.

#### Manager UI

- Rebuilt Home, Settings, and About with Kotlin, Jetpack Compose, and MIUIX.
- Added Working, Pending restart, and Inactive module status cards.
- Kept the Settings tab reachable while the module is inactive.
- Disabled and dimmed the edge-adjustment card while inactive; tapping it shows “模块未激活”.
- Added the edge-distance slider and fine-adjustment behavior.
- Added a SystemUI restart entry and event-driven module status refresh.
- Added version, project links, reference projects, and license information to About.

#### Theme and interaction

- Added system, light, dark, and AMOLED-related theme handling.
- Added system Monet, seed color, palette style, and color specification options.
- Added a KernelSU-style floating navigation bar.
- Added blur, liquid-glass effects, and navigation status badges.
- Added predictive back support on compatible Android versions.
- Kept the three status-card glyphs on stable semantic colors so Monet cannot unexpectedly recolor them.

#### Engineering and release

- Version name: `2.0.0`.
- Version code: `20260805`.
- Updated `module.prop` metadata.
- Added Chinese and English README files with bidirectional Markdown links.
- Added complete build, installation, troubleshooting, compatibility, and contribution documentation.
- Prepared the signed Release APK for the stable publication.

#### Known limitations

- HyperOS minor releases may change internal freeform classes or method signatures; incompatible rules are skipped safely.
- Some devices require a full reboot instead of only restarting SystemUI to reload static scopes.

[← Back to English README](README_EN.md)
