# MiFreeformUnbounded

[中文](README.md) · [English](README_EN.md)

适用于 HyperOS 3/4 的自由窗口、锁屏玻璃时钟与超级壁纸景深模块。

[下载正式版 v3.1.0](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v3.1.0) · [下载预发布版 v3.1.1-rc.1](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v3.1.1-rc.1) · [更新日志](CHANGELOG.md)

## 功能

1. **自由窗口边界保护**：调整窗口拖到屏幕边缘时保留的最小可见距离，范围为 8–2000px，默认为 196px。
2. **锁屏玻璃时钟**：在适配的锁屏场景中启用系统原本限制的玻璃时钟，可能增加功耗，部分数字材质不适用。
3. **超级壁纸景深**：让锁屏时钟被超级壁纸中的前景遮挡。目前主要在雪山壁纸上验证，其他壁纸和系统版本的效果可能不同。

## 使用要求

- HyperOS 3/4，Android 12 或更高版本。
- 已安装并启用支持 Modern LibXposed API 102 的框架。
- 功能适配取决于具体系统版本，不保证所有 HyperOS 机型均可使用。

## 安装与使用

1. 下载 APK 并安装，在 LSPosed 中启用模块。
2. 打开模块，在“配置”中开启所需功能。
3. 按首页提示重启相关作用域，使模块及配置生效。

静态作用域包括系统界面、息屏与锁屏编辑、MiWallpaper，以及月球、雪山、几何、土星、地球、火星超级壁纸。未安装的壁纸无需补装。

## 升级

v3.1.0、v3.0.2-beta.2 及 CI.94 的同签名版本可覆盖升级。公开 v3.0.0 和部分 Debug 测试包使用不同签名，无法直接覆盖安装；请先保留配置，勿直接卸载或清除数据。

预发布版用于测试，正式版用户可继续使用 v3.1.0。每个发布页提供 APK、校验文件和该版本的已知问题。

## 已知问题

- AOD 转入锁屏时，可能短暂出现前景轮廓与时钟遮罩不同步。
- 整机冷启动及首次解锁前的景深恢复尚未完成验证。

具体限制见[发布说明](releases/v3.1.1-rc.1.md)。

## 反馈

提交问题时，请附上机型、HyperOS 和 Android 版本、模块与框架版本，以及复现步骤。涉及景深问题时，请附上转场录像。

## 许可证

[GNU Affero General Public License v3.0](LICENSE)
