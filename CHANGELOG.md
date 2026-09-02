# Changelog

[中文](#中文) · [English](#english) · [返回中文 README](README.md) · [English README](README_EN.md)

## 中文

### v3.0.1 · 未发布

- 修复首页状态卡在启用 Monet 时仍固定使用红黄绿的问题，统一跟随动态强调色主色系；关闭 Monet 时保留原有语义色。
- 修复强制使用玻璃时钟确认弹窗动画，改为从底部滑入、回弹和自然淡入淡出。
- 参考 KernelSU 的分页弹簧和滑条交互修复预测性返回进度，新增最大预测返回进度滑条（20%–50%，默认 35%）。
- 修复最小可见距离手动输入框在 Monet 主题下被染成绿色的问题，输入区域固定为白色。
- 开发版本号更新为 `3.0.1`，版本号 `20260902`。
- 超级壁纸编辑器接入静态时钟编辑数据，支持颜色、尺寸、位置和景深；SystemUI 深度支持状态在超级/景深壁纸下保持开启；Glass 材质保持用户选择，不因适配逻辑强制开启。

### v3.0.0 · 2026-09-01 · 正式版

- 将自由窗口边界保护归类到独立的二级设置页面，新增顶部总开关；关闭时边缘距离和精细调节会灰显并且不可操作。
- 保留边缘距离、最小可见距离输入和精细调节功能，统一二级页面的交互和说明。
- 强制使用玻璃时钟开启前增加功耗与数字材质限制确认弹窗，避免误开启后无法使用其他时钟材质。
- 补齐一级设置页的自由窗口边界保护图标，并统一一级、二级设置页图标与文字的对齐方式。
- 设置页和二级设置页图标统一使用 MIUIX 主题主色，启用 Monet 时跟随壁纸动态取色。
- 发布版本名 `3.0.0`，版本号 `20260901`。


### v2.2.6 · 2026-08-29 · 开发版

- 将自由窗口边缘距离、精细调节移动到独立的二级设置页面。
- 新增自由窗口边界保护总开关；关闭时下方设置灰显且不可操作。
- 开启强制玻璃时钟前增加功耗与数字材质限制确认弹窗。


### v2.2.5 · 2026-08-29 · 开发版

- 统一 UI 用词：使用“系统界面”“息屏与锁屏编辑”“强制使用玻璃时钟”和“锁屏玻璃时钟”。
- 两项功能默认关闭，边缘距离默认值仍为 `196px`。
- 首页、设置页和关于页统一增加悬浮底栏避让间距。
- 简化关于页说明，直接列出两个目标应用及各自功能，并补充 HyperOS 3/4 与 HyperOS 4 的兼容范围。


### v2.2.4 · 2026-08-29 · 开发版

- 设置页新增自由窗口边界保护与 AOD Glass 字体放行开关，均默认开启并通过 remote preferences 同步到目标进程。
- 首页新增分别重启 `com.android.systemui` 和 `com.miui.aod` 的按钮，AOD 编辑器同时覆盖 `keyguardeditor` 子进程。
- 增强配置页的边缘距离说明、恢复默认配置入口和动态状态摘要。
- 完善关于页的模块作用域、Hook 策略、开关生效方式与 ROOT 权限说明。


### v2.2.3 · 2026-08-29 · 开发版

- 补齐 SystemUI 的 `getClockBeanFromSetting(String)` 读取路径；当设置重新读出带 Glass 特征但 `clockEffect=1` 的时钟时，在应用前恢复为 `clockEffect=5`。
- 保留 `setClockBean` 接收路径兜底，避免动态壁纸应用后因设置刷新再次回退到混色效果。

### v2.2.2 · 2026-08-29 · 开发版

- 兼容当前设备的 `isWallpaperSupportGlassFilter(String)` 签名，动态 `video` 参数现在会正确进入 Glass 放行路径。

### v2.2.1 · 2026-08-29 · 开发版

- 补齐 `com.android.systemui` 的 `MiuiClockController.setClockBean` 接收路径，在动态/深度壁纸下恢复被降级的 Glass effect。
- 仅匹配 all-in-one Glass 特征字段和 `isWallpaperSupportDepth=true`，静态壁纸及其他时钟样式保持原行为。

### v2.2.0 · 2026-08-29 · 开发版

- 根据设备运行日志补齐 AOD 动态壁纸路径：保留 Glass effect ID、放开玻璃滤镜支持判断，并跳过动态壁纸上的滤镜清理。
- 移除首包限制，确保 `com.miui.aod:keyguardeditor` 进程能够安装 hook。

### v2.1.0 · 2026-08-29 · 开发版

- 新增 `com.miui.aod` Modern LibXposed API 102 Hook。
- 解除 all-in-one 息屏时钟在动态壁纸和超级壁纸下的 Glass 字体效果限制。
- 只改写已被系统判定为禁用的 Glass 效果；静态壁纸、其它模板和其它效果保持原行为。
- 目标环境更新为 HyperOS 4，版本号 `20260829`。

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

### v3.0.1 · Unreleased

- Fixed the home status card using hard-coded red, yellow, and green colors when Monet is enabled; it now uses dynamic theme container roles while preserving semantic colors when Monet is disabled.
- Reworked the forced Glass clock confirmation dialog with a bottom slide, spring overshoot, and natural fade transitions.
- Followed KernelSU's pager spring implementation to correct predictive-back progress and added a configurable maximum progress setting (20%–50%, 35% by default).
- Fixed the minimum-visible-distance editor being tinted green under Monet; its input area is now explicitly white.
- Bumped the development version to `3.0.1` with version code `20260902`.
- Reused the static-clock editor data in the Super wallpaper editor for color, size, position, and depth; SystemUI depth support stays enabled for Super/depth wallpapers, while Glass remains the user's choice and is never forced by the adapter.

### v3.0.0 · 2026-09-01 · Stable

- Grouped freeform boundary protection into a dedicated secondary settings page with a master switch; edge distance and fine adjustment are dimmed and non-interactive when it is off.
- Kept the edge-distance slider, minimum-distance input, and fine adjustment while aligning the secondary-page interaction and descriptions.
- Added a power-consumption and material-limit confirmation dialog before enabling the forced glass-clock feature, reducing accidental activation.
- Restored the freeform boundary protection icon on the main Settings page and aligned the feature icons with their labels on both pages.
- All Settings and secondary-page feature icons now use the MIUIX theme primary color and follow wallpaper-derived Monet colors.
- Released version `3.0.0` with version code `20260901`.


### v2.2.6 · 2026-08-29 · Development

- Moved freeform edge distance and fine adjustment into a dedicated secondary settings page.
- Added a master switch that disables and dims all subordinate controls when off.
- Added a power-consumption and material-limit confirmation dialog before enabling the forced glass-clock feature.


### v2.2.5 · 2026-08-29 · Development

- Standardized the UI terminology for the System UI, lock-screen editor, and lock-screen glass clock features.
- Both module features now default to disabled; the edge-distance default remains `196px`.
- Unified floating navigation-bar avoidance spacing on Home, Settings, and About.
- Simplified About to name the two hooked applications and their user-facing functions, with the HyperOS compatibility split.


### v2.2.4 · 2026-08-29 · Development

- Added Settings switches for freeform boundary protection and AOD Glass passthrough. Both remain enabled by default and sync through remote preferences.
- Added separate Home actions to restart `com.android.systemui` and `com.miui.aod`; the AOD action also handles the `keyguardeditor` child process.
- Improved configuration descriptions, reset-to-default action, and live status summaries.
- Expanded About with module scopes, runtime hook behavior, switch activation rules, and root permission details.


### v2.2.3 · 2026-08-29 · Development

- Hook the SystemUI `getClockBeanFromSetting(String)` reload path and restore `clockEffect=5` before a Glass-marked bean is applied after settings refresh.
- Keep the `setClockBean` receive-path fallback so dynamic-wallpaper applications do not fall back to the mixed-color effect on reload.

### v2.2.2 · 2026-08-29 · Development

- Support the device's `isWallpaperSupportGlassFilter(String)` signature so the dynamic `video` argument reaches the Glass allow path correctly.

### v2.2.1 · 2026-08-29 · Development

- Added the `com.android.systemui` `MiuiClockController.setClockBean` receive-path hook to restore a downgraded Glass effect for dynamic/depth wallpapers.
- The override is limited to all-in-one Glass marker fields with `isWallpaperSupportDepth=true`; static wallpapers and other clock styles remain unchanged.

### v2.2.0 · 2026-08-29 · Development

- Completed the AOD dynamic-wallpaper path using runtime evidence: preserve the Glass effect ID, allow the Glass filter support check, and skip filter cleanup for dynamic wallpapers.
- Removed the first-package gate so the `com.miui.aod:keyguardeditor` process can install its hooks.

### v2.1.0 · 2026-08-29 · Development

- Added a Modern LibXposed API 102 hook for `com.miui.aod`.
- Enabled the all-in-one AOD clock's Glass font effect for dynamic and Super wallpapers.
- Only stock-disabled Glass decisions are overridden; static wallpapers, other templates, and other effects are untouched.
- Updated the target environment to HyperOS 4 and version code to `20260829`.

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
