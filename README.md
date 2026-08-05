# MiFreeformUnbounded

[中文](README.md) · [English](README_EN.md)

面向 HyperOS 3 / Android 16 的自由窗口边界模块。项目使用 Kotlin、Jetpack Compose、MIUIX 和 Modern LibXposed API 102，通过经过签名校验的 Hook 规则改善自由窗口拖动时的边缘保护与横向移动体验。

> 当前正式版本：**v2.0.0**（版本号：**20260805**）
> [下载正式版 Release](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v2.0.0) · [查看更新日志](CHANGELOG.md)

## 主要功能

- 为 HyperOS 3 自由窗口提供可配置的最小可见边缘距离。
- 对横向拖动、稳定偏移、动画目标和最终窗口边界进行针对性处理。
- 采用 Modern LibXposed API 102，使用静态作用域运行。
- Hook 前校验类名、方法名、参数类型和返回类型；不匹配时保持系统原行为并写入诊断日志。
- 支持 Hook 热重载，并在关键路径提供限频日志，方便定位不同 HyperOS 构建差异。
- 提供首页状态、设置、关于三个页面，以及模块状态卡片和重启 SystemUI 操作。
- 设置页支持边缘距离滑条、精细调节、深色/浅色模式、系统 Monet、色彩风格和色彩规范。
- 支持悬浮底栏、液态玻璃效果、导航栏状态角标和预测性返回手势。
- 模块未激活时仍可进入设置页；边缘调节区域会置灰，点击会提示“模块未激活”。

## 兼容性

| 项目 | 要求 |
| --- | --- |
| 系统 | HyperOS 3 / Android 16（目标环境） |
| 最低 Android | Android 12（API 31） |
| Hook 框架 | 支持 Modern LibXposed API 102 的管理器 |
| 静态作用域 | `android`、`com.android.systemui` |
| 构建工具 | JDK 21、Android SDK 37、Gradle Wrapper |

不同 HyperOS 小版本可能调整类名或方法签名。模块会在运行时进行严格匹配，未匹配的规则不会强行注入。

## 安装与启用

1. 从 [v2.0.0 Release](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v2.0.0) 下载正式版 APK。
2. 使用支持 Modern LibXposed API 102 的模块管理器安装 APK。
3. 为模块启用静态作用域：`android` 和 `com.android.systemui`。
4. 重启设备，或按应用内提示重启 SystemUI。
5. 打开应用首页，确认状态卡显示“工作中”或“待重启”。

## 使用说明

### 边缘距离

设置页的“最小可见距离”控制自由窗口拖到屏幕边缘时保留的最小可见像素，默认值为 `196px`，可调范围为 `8px` 至 `320px`。精细调节会降低滑条每次拖动的有效步进，适合微调。

当模块尚未激活时，设置页仍然可打开，但边缘调节卡片会被禁用；模块激活或待重启后，控件恢复可用。

### 主题与底栏

- 深色模式：跟随系统、浅色、深色。
- Monet：使用系统壁纸动态色；可选强调色、色彩风格和色彩规范。
- 悬浮底栏：可切换普通模式、模糊和液态玻璃效果。
- 预测性返回：在支持的 Android 版本上启用预测性返回回调。

## 从源码构建

Windows PowerShell：

```powershell
$env:JAVA_HOME = "D:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease --offline --console=plain
```

输出文件：

```text
app/build/outputs/apk/release/app-release.apk
```

本仓库的 Release 构建使用项目中配置的发布签名配置。正式发布前请确认签名密钥、版本号和 `module.prop` 一致。

## 项目结构

```text
app/src/main/java/com/freeform/unbounded/
├─ FreeformHook.kt              # LibXposed 入口与 Hook 安装
├─ HookProfiles.kt               # 经过签名校验的 Hook 规则
├─ BoundsPolicy.kt               # 边缘保护与阻尼计算
├─ ModuleStatusRepository.kt     # 模块/作用域/运行状态
├─ ConfigRepository.kt           # 跨进程配置读写
└─ ui/                           # Compose + MIUIX 界面
```

## 故障排查

- **首页显示未激活**：确认两个静态作用域均已启用，并重启设备或 SystemUI。
- **显示待重启**：模块已安装但目标进程尚未重新加载，按提示重启 SystemUI。
- **拖动效果没有变化**：检查当前 HyperOS 构建是否仍使用已知方法签名，并查看 Xposed 日志中的匹配结果。
- **设置滑条不可用**：模块未激活时这是预期行为；激活或待重启后会自动恢复。
- **升级后异常**：先关闭旧模块实例并重新启用作用域，再收集日志和系统版本信息。

## 相关项目

- [KernelSU](https://github.com/tiann/KernelSU)：主题、导航和部分界面交互的参考来源。
- [MIUIX](https://github.com/compose-miuix-ui/miuix)：Compose UI 组件与主题系统。
- [Modern LibXposed API](https://github.com/libxposed/api)：模块接口。

## 许可证与贡献

本项目基于 [GNU Affero General Public License v3.0](LICENSE) 发布。欢迎提交 Issue、兼容性报告和 Pull Request；提交问题时请附上 HyperOS 版本、Android 版本、LibXposed 管理器版本以及相关日志。

## 版本与文档

- [v2.0.0 更新日志](CHANGELOG.md)
- [GitHub Releases](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases)
- [English README](README_EN.md)

[返回 English README →](README_EN.md)
