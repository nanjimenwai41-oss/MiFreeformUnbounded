# CI#17 设备证据初报（annibale / OS4.0.0.31.XPKCNXM）

采集目录：`C:/Users/Zachian/ci17`

## 已拉取材料

- `packages/MiuiSystemUI/MiuiSystemUI.apk`（SystemUI，含 3 个 dex）
- `packages/MIUIAod/MIUIAod.apk`（AOD/editor）
- `packages/MiWallpaper/MiWallpaper.apk`（壁纸管理服务）
- `packages/{snowmountain,moon,geometry,saturn,earth,mars}.apk`（超级壁纸）
- `reports/dumpsys_{systemui,aod,miwallpaper,wallpaper,SurfaceFlinger,processes}.txt`
- `reports/systemui_dexdump.txt`（SystemUI 三个 dex 的 dexdump）
- `logs/ci17_capture_now.txt`

## 已确认的真实 SystemUI 类和字段

SystemUI dex 中存在完整的 `com.android.keyguard.depth.KeyguardDepthInteractor`，并非只有一个布尔接口。类的关键字段包括：

- `depthEffectEnableInner: boolean`
- `depthVideoEnable: boolean`
- `isDepthFileParsing: boolean`
- `deductedImageView: android.widget.ImageView`
- `keyguardBackgroundLayer: android.view.ViewGroup`
- `keyguardForegroundLayer: android.view.ViewGroup`
- `videoDepthSurfaceHolder: com.miui.keyguard.VideoDepthSurfaceHolder`
- `videoDepthDimBlurRenderEffect: android.graphics.RenderEffect`
- `miuiKeyguardWallPaperManager: com.android.keyguard.wallpaper.MiuiKeyguardWallPaperManager`

已发现的关键方法：

- `initDepthBitmapAvoid(Bitmap, Uri)`
- `onDensityChanged(...)`
- `onViewAttachedToWindow(...)`
- `removeVideoDepthSurface(...)`
- `runAfterDepthParseJobComplete(...)`
- `setDepthTransitionAlpha(...)`
- `setVideoDepthWallpaperBlackStatus(...)`
- `updateAvoidStatus(...)`
- `updateDeductedImageView()`
- `updateVideoDepthNotification(...)`
- `updateVideoDepthSurface(...)`
- `updateVideoDepthVisibility(...)`

在 `initDepthBitmapAvoid` 和 `reInitDepthBitmapAvoid` 的字节码中，SystemUI 会调用：

```text
MiuiKeyguardWallPaperManager.isDepthVideoEnable(): boolean
```

随后根据该结果选择普通 bitmap 避让或视频深度 Surface 路径。`updateVideoDepthSurface`、`VideoDepthSurfaceHolder`、`keyguardForegroundLayer` 和 `keyguardBackgroundLayer` 是必须继续追踪的真实渲染链。

## 与用户现象直接对应的证据

`log(14).txt` 中可见：

```text
MiuiKeyguardWallPaperManager: isDepthVideoEnable magicType:0 type:super_wallpaper mDepthVideoReady:true enable:false
KeyguardDepthInteractor: isDepthVideoEnable:true
```

因此当前状态是：管理器原始判断为 false，下游交互器被告知 true。此时不能生成真实视频深度 Surface，极易继续使用此前静态图片的 `deductedImageView` / mask / bitmap。

模板数据同时显示超级壁纸为 `resourceType=super_wallpaper`、`isDepthVideo=false`、`depthVideo=null`。这证明 CI#16 的兼容层没有建立视频资源输入。

## 下一步必须完成

1. 从 dexdump 中提取 `MiuiKeyguardWallPaperManager` 的完整字段、构造方法和 `isDepthVideoEnable` 方法体。
2. 提取 `KeyguardDepthInteractor.updateVideoDepthSurface`、`updateDeductedImageView`、`initDepthBitmapAvoid` 的完整字节码和调用者。
3. 提取 `VideoDepthSurfaceHolder` 的字段、构造方法、Surface 生命周期和 alpha 更新方法。
4. 对 `MiWallpaper.apk` 做同样的 dexdump，追踪 `VideoDepth`、`getLastDepthFrameBitmap`、`getMiuiLockPartWallpaperIsDeep` 的 Binder/服务实现。
5. 在设备上以 observe-only 方式记录上述方法的真实参数、返回值和对象 identity，再决定 CI#18 的实现入口。

## 壁纸服务侧的新证据

`reports/miwallpaper_dexdump.txt` 已确认真实视频深度并非由 `ClockDepthAvoidRuleUtils` 计算，而是由壁纸服务的视频深度引擎提供：

- `com.miui.fastplayer.FastPlayer` 有 native 方法：
  `getDepthFrameAtTime(String, long): Bitmap`
- `FastPlayer.getLastDepthFrame(String): Bitmap` 直接调用上述 native 方法并传入时间 `-1`。
- 壁纸服务存在 `com.miui.miwallpaper.container.videodepth.VideoDepthManager`，以及 `VideoDepthEngineImpl`、`KeyguardVideoDepthEngineImpl`、`DesktopVideoDepthEngineImpl`。
- SystemUI/壁纸服务之间存在 Binder 回调接口：
  `com.miui.miwallpaper.IMiuiVideoDepthLastFrameCallback`
  - `onGetLastDepthFrameSuccess(Bitmap, int)`
  - `onGetLastDepthFrameFailed(int, String)`
  - transaction 1 为成功，transaction 2 为失败。

这说明真实链路至少是：视频壁纸引擎/`FastPlayer` 生成深度帧 → 壁纸服务通过 `IMiuiVideoDepthLastFrameCallback` 返回 Bitmap → SystemUI `KeyguardDepthInteractor` 接收并更新 `deductedImageView` / `VideoDepthSurfaceHolder`。超级壁纸当前没有提供同等的 `FastPlayer` 深度帧或回调，单纯把 `isDepthVideoEnable()` 改成 true 不可能完成移植。

在完成上述五步前，不应继续添加 `true` 返回值 Hook 或手工拼接 `Rect`。
