# CI#17 严格 Hook 覆盖矩阵（修正版）

上一版只 Hook 了 SystemUI 的观察点，没有覆盖深度帧生产端，因此不能证明链路完成。本矩阵是后续实现的硬性清单。

| 阶段 | 真实类 | 真实方法/字段 | Hook 进程 | 允许改写 |
|---|---|---|---|---|
| 壁纸身份 | `MiuiKeyguardWallPaperManager` | `isDepthVideoEnable(): boolean`；`magicType`、`type`、`mDepthVideoReady` | SystemUI | 第一阶段只观察；确认输入后才局部映射 |
| SystemUI 深度入口 | `KeyguardDepthInteractor` | `initDepthBitmapAvoid(Bitmap,Uri)` | SystemUI | 不伪造 Bitmap；记录是否走视频分支 |
| 静态路径 | `KeyguardDepthInteractor` | `updateDeductedImageView()` | SystemUI | 切换时清理旧 session，不能把旧 Bitmap 传给 Super |
| 视频 Surface | `KeyguardDepthInteractor` | `updateVideoDepthSurface()`、`updateVideoDepthVisibility(int,boolean,boolean)`、`removeVideoDepthSurface()` | SystemUI | 只在当前 Super 会话有真实深度帧时允许 |
| Surface 状态 | `VideoDepthSurfaceHolder` | 全部构造、Surface、alpha、visibility 方法 | SystemUI | 记录 holder identity 与 session identity |
| 深度帧生产 | `FastPlayer` | `getDepthFrameAtTime(String,long): Bitmap`、`getLastDepthFrame(String): Bitmap` | MiWallpaper | 不替换 native 结果 |
| 深度管理 | `VideoDepthManager` | `a()`、`g()` 及实际公开/合成方法 | MiWallpaper | 先从 dex 确认签名，不按混淆名猜写入 |
| 视频引擎 | `VideoDepthEngineImpl` / `KeyguardVideoDepthEngineImpl` | Surface 创建、资源加载、回调注册方法 | MiWallpaper | 不把 SuperWallpaper 强行当视频资源 |
| Binder 回调 | `IMiuiVideoDepthLastFrameCallback` 实际 Stub/Proxy/调用者 | `onGetLastDepthFrameSuccess(Bitmap,int)`、`onGetLastDepthFrameFailed(int,String)` | SystemUI + MiWallpaper | 记录 Bitmap identity、错误码、帧序号 |
| 时钟布局 | `MiuiClockController` | `isWallpaperSupportDepth()`、`getDepthAvoidRect()` | SystemUI | 只消费当前会话结果，禁止手工替换为固定 Rect |

## 必须同时启用的 Xposed 作用域

```text
com.android.systemui
com.miui.aod
com.miui.miwallpaper
```

只启用前两个作用域时，深度帧生产端没有 Hook，CI#17 不能通过。

## 必须看到的日志顺序

```text
CI17 wallpaper-session new generation=N type=super_wallpaper
CI17 manager isDepthVideoEnable stock=false magicType=... type=... ready=...
CI17 FastPlayer.getLastDepthFrame source=... result=Bitmap identity=...
CI17 callback onGetLastDepthFrameSuccess frame=... identity=...
CI17 KeyguardDepthInteractor.initDepthBitmapAvoid video=true session=N
CI17 updateVideoDepthSurface holder=... session=N
CI17 updateVideoDepthVisibility ... session=N
CI17 clock avoid result session=N
```

缺少 `FastPlayer` 结果或 Binder success 回调时，禁止把 `isDepthVideoEnable` 改成 true；缺少 `updateVideoDepthSurface` 时，禁止声称超级壁纸已产生景深。

## 切换污染判定

静态图片 → 超级壁纸后，必须同时满足：

1. session generation 递增。
2. `deductedImageView` 的 Bitmap identity 不再属于静态 session。
3. 旧 `VideoDepthSurfaceHolder` 已移除或明确失效。
4. 新的 Super 会话获得自己的深度帧或明确报告“不支持”。
5. `getDepthAvoidRect()` 不得返回旧 session 的 Rect。

否则判定为失败，不得继续添加兼容映射。
