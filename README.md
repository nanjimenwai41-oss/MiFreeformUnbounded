
# MiFreeformUnbounded

面向 HyperOS 3 的自由窗口边界模块，完全使用 Kotlin 和 Modern LibXposed API 102。

## 要求

- 支持 Modern LibXposed API 102 的模块管理器
- HyperOS 3 / Android 16
- 作用域：`android`、`com.android.systemui`

安装 APK 后，在模块管理器中启用上述静态作用域并重启设备。模块只对经过类名、方法名和返回类型校验的已知 HyperOS 3 Hook 点生效；找不到兼容 Hook 点时会保留系统原行为并记录日志。

## 构建

项目使用 Kotlin、JDK 21、Gradle Wrapper：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

本机默认使用 Android Studio 自带的 JBR：`D:/Program Files/Android/Android Studio/jbr`。

## 作者说明
本项目目前完全使用Codex完成，望大佬们谅解。
BUG：开启小窗之后会闪烁一下。

## 画饼
1.增加安全拖动距离限制，避免小窗失控
2.为澎湃OS增加更多功能（远期）



