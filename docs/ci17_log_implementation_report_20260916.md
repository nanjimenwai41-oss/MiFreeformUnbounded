# CI17 日志与技术实现汇报（2026-09-16）

## 结论

本批材料**部分有效**。

有效部分：已经证明 FreeformUnbounded 在 SystemUI、AOD 和 MiWallpaper 进程中被 LSPosed 加载；SystemUI 的 CI17 观察 Hook 已运行；MiWallpaper 的生产端 Hook 已进入，但只有一个目标方法成功匹配。

无效部分：不能作为“重启前后对比”证据。`super_wallpaper_depth_20260916_230901.markers.txt` 没有 `BeforeReboot` 标记，只有 `AfterReboot`。因此这次采集没有覆盖重启前状态，也不能据此判断重启是否刷新了静态作用域。

## 文件有效性

| 材料 | 判断 | 原因 |
| --- | --- | --- |
| `230901.log` | 有效（运行时证据） | 包含模块加载、CI17 调用和系统景深日志 |
| `230901.summary.txt` | 有效（筛选证据） | 能定位关键事件，但不是完整日志 |
| `230901.markers.txt` | 不完整 | 缺少 `BeforeReboot`，无法形成前后对照 |
| `230901.start.txt` / `end.txt` | 有效（设备快照） | 记录了设备、壁纸服务和进程状态 |
| `230645.log` | 参考材料 | 没有形成一组完整的开始/结束/标记文件 |
| `LSPosed_20260916_231146.zip` | 有效（最关键） | 包含 LSPosed 的模块级加载和 Hook 匹配结果 |

## 已确认的运行链路

### 1. AOD

LSPosed 记录：

```text
Loaded in com.miui.aod
com.miui.aod: installed 3 HyperOS 3 hook(s)
```

AOD 作用域有效。

### 2. SystemUI

LSPosed 记录：

```text
Loaded in com.android.systemui
com.android.systemui: installed 15 HyperOS 3 hook(s)
```

以下 SystemUI 目标均已成功匹配：

```text
KeyguardDepthInteractor#initDepthBitmapAvoid(Bitmap, Uri)
KeyguardDepthInteractor#removeVideoDepthSurface()
KeyguardDepthInteractor#updateDeductedImageView()
KeyguardDepthInteractor#updateVideoDepthSurface()
KeyguardDepthInteractor#updateVideoDepthVisibility(int, boolean, boolean)
```

运行时还出现：

```text
CI17 observed updateDeductedImageView[]
CI17 observed updateVideoDepthSurface[]
CI17 observed removeVideoDepthSurface[]
CI17 observed initDepthBitmapAvoid[Bitmap, Uri]
```

这证明 SystemUI 消费端观察 Hook 已经执行。

### 3. MiWallpaper

LSPosed 记录：

```text
Loaded in com.miui.miwallpaper
Hooked com.miui.fastplayer.FastPlayer#getLastDepthFrame(String):Bitmap
com.miui.miwallpaper: installed 1 HyperOS 3 hook(s)
```

这证明 MiWallpaper 静态作用域已经生效，问题不是“没有加入作用域”。

但同时存在以下失败：

```text
VideoDepthManager: class exists but no known signature matched
VideoDepthEngineImpl: class not found
wallpaperservice.impl.VideoDepthEngineImpl: class exists but no known signature matched
keyguard.KeyguardVideoDepthEngineImpl: class exists but no known signature matched
```

因此当前代码只观察到了 `FastPlayer#getLastDepthFrame`，没有建立完整的视频深度生产链。

## 未出现的关键证据

本批日志没有发现以下事件：

```text
FastPlayer#getDepthFrameAtTime
IMiuiVideoDepthLastFrameCallback.onGetLastDepthFrameSuccess
IMiuiVideoDepthLastFrameCallback.onGetLastDepthFrameFailed
VideoDepthManager -> VideoDepthEngineImpl
MiWallpaper depth frame -> SystemUI Binder callback
```

因此目前不能声称“超级壁纸已经使用真实视频景深帧”。当前实现仍然是：

```text
SystemUI 消费端观察 + MiWallpaper 部分生产端观察
```

不是：

```text
FastPlayer 深度帧 -> Binder 回调 -> KeyguardDepthInteractor -> VideoDepthSurfaceHolder
```

## 复现状态判断

主日志中出现：

```text
WallpaperTypeUtils: wallpaperType = image
KeyguardImageEngineImpl
DesktopImageEngineImpl
```

这些记录说明采集期间至少有一段时间仍处于静态壁纸引擎，不能证明超级壁纸视频引擎已经完成切换并进入深度帧流程。

## 下一次必须补齐的采集

### 第一次：重启前单独采集

1. 连接设备。
2. 确认 LSPosed 三个静态作用域。
3. 启动日志采集。
4. 先标记 `BeforeReboot`。
5. 不操作壁纸，停止采集。

这次只回答：重启前三个进程是否已经加载模块。

### 第二次：重启后单独采集

1. 完整重启设备。
2. 等待系统稳定。
3. 启动新的日志采集。
4. 标记 `AfterReboot`。
5. 静态壁纸开启景深。
6. 切换到超级壁纸并等待 10 秒。
7. 标记 `ReproduceDepthIssue`。
8. 停止采集。

这次只回答：重启后是否进入 MiWallpaper 视频深度生产链，以及错误前景是否来自旧的静态抠像结果。

## 下一步代码实现顺序

1. 根据当前设备真实类名修正 `VideoDepthEngineImpl` 和 `KeyguardVideoDepthEngineImpl` 的匹配规则。
2. 增加 `FastPlayer#getDepthFrameAtTime(String, long):Bitmap` 的观察和返回值记录。
3. 增加 `IMiuiVideoDepthLastFrameCallback` 成功/失败回调观察。
4. 确认 Binder 回调中的 Bitmap 是否进入 SystemUI，而不是继续使用 `initDepthBitmapAvoid(Bitmap, Uri)` 的静态抠像结果。
5. 在确认真实帧链路完整后，才实现 Super wallpaper 的输入替换；当前阶段不得继续强制布尔值或直接复用静态前景 Bitmap。

## 最终判断

本次日志已经排除“MiWallpaper 未加入静态作用域”这一假设，但尚未证明真实视频景深算法移植完成。当前最明确的技术缺口是 MiWallpaper 端视频深度引擎签名不匹配，以及 `getDepthFrameAtTime` 和 Binder 回调没有进入可观测链路。
