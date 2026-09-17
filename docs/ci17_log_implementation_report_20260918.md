# CI17 日志与技术实现汇报（2026-09-18）

## 结论

本次两份手机端导出日志**有效，但未形成完整的真实视频深度链路**。

已经确认：

- SystemUI 中的 FreeformUnbounded CI17 观察 Hook 正在运行。
- SystemUI 确实执行了 `KeyguardDepthInteractor#initDepthBitmapAvoid`。
- 静态景深文件被成功解析过。
- 日志期间确实运行了 Moon/Snowmountain 超级壁纸进程。

尚未确认：

- `FastPlayer#getDepthFrameAtTime(String, long)` 被调用。
- `IMiuiVideoDepthLastFrameCallback` 成功或失败回调被调用。
- MiWallpaper 产生的深度 Bitmap 被送入 SystemUI。
- `VideoDepthSurfaceHolder` 收到新的真实视频深度帧。

因此“超级壁纸景深无效”是符合当前代码状态的：现在仍然只有 SystemUI 消费端观察，真实视频帧没有进入消费端。

## 文件判断

### `重启之后.txt`

有效。文件包含超级壁纸运行日志和 SystemUI 景深调用，但它不是纯粹的“重启后立即采集”结果，因为内容混入了大量其它进程日志。它可以证明设备进入了超级壁纸场景，不能证明视频深度帧已产生。

### `问题复现（复现失败，但是仍然景深无效）.txt`

有效。它包含复现期间的 `KeyguardDepthInteractor` 调用和超级壁纸偏移/渲染日志。它没有出现真实视频深度帧入口或 Binder 回调，因此“复现失败但景深无效”的结论成立：复现动作没有触发本模块可接入的生产链。

## 关键证据

### SystemUI 消费端已运行

日志出现：

```text
CI17 observed initDepthBitmapAvoid[class android.graphics.Bitmap, class android.net.Uri]
KeyguardDepthInteractor: updateDeductedImageView: finish parse depth file, success
```

这说明 SystemUI 当前仍在走静态壁纸景深文件解析路径。`initDepthBitmapAvoid(Bitmap, Uri)` 的输入来源仍是静态抠像流程，不是视频深度帧。

### 超级壁纸进程已运行

日志出现：

```text
MiWallpaper-SnowmountainSuperWallpaper: Engine onOffsetsChanged
MiWallpaper-MoonSuperWallpaper: Engine onOffsetsChanged
sendFilamentMessage real Offset_...
```

这只能证明超级壁纸渲染器在运行，不能证明它调用了视频深度算法。偏移和 Filament 消息不是深度 Bitmap。

### 关键生产链路仍然缺失

两份日志均未发现：

```text
CI17 depth source getDepthFrameAtTime
CI17 depth callback onGetLastDepthFrameSuccess
CI17 depth callback onGetLastDepthFrameFailed
```

这表示当前运行过程中没有捕获到真实深度帧方法或 Binder 回调。

## 根因判断

当前问题不是静态作用域，也不是 SystemUI Hook 未加载。SystemUI 观察入口已经生效，超级壁纸进程也在运行。

当前根因有两个可能性，优先级如下：

1. 设备实际使用的深度生产类不是当前配置的 `FastPlayer`/`VideoDepthEngineImpl` 路径，真实入口位于超级壁纸具体包或 native/Filament 层。
2. `getDepthFrameAtTime` 只在锁屏视频壁纸编辑器或特定视频资源场景调用，超级壁纸不会调用它；因此不能把动态视频壁纸 API 直接假设为超级壁纸 API。

## 本次代码修改的实际边界

Debug 包新增的一键导出功能工作正常，能够把设备端日志导出到分享面板。它没有改变景深渲染逻辑。

上一版本新增的 Hook 也只做观察并保留原始结果：

```text
getLastDepthFrame
getDepthFrameAtTime
onGetLastDepthFrameSuccess
onGetLastDepthFrameFailed
```

由于这些方法在本次场景中没有产生调用记录，所以没有发生任何 Bitmap 注入或 Surface 更新。这解释了为什么安装后画面仍无变化。

## 下一步技术实现

下一步不应继续等待同一组日志，而应改为直接定位超级壁纸自身的渲染输入：

1. 对 `com.miui.miwallpaper.snowmountain.superwallpaper.SnowmountainSuperWallpaper`、`MoonSuperWallpaper` 等实际 Engine 类建立专用 Hook。
2. 枚举并记录这些 Engine/Renderer 的 `Bitmap`、`ByteBuffer`、纹理上传和深度纹理相关方法。
3. 将深度纹理/深度 Bitmap 与当前锁屏显示会话绑定，而不是继续读取静态 `Uri` 抠像结果。
4. 在 SystemUI 侧清理旧的 `deductedImageView`，再触发视频 Surface 更新。
5. 只有当日志同时出现“超级壁纸 Engine 输入”和“SystemUI 新深度输入”后，才开启实际结果替换。

## 最终判断

本次日志有效地证明了：

```text
模块已加载 + SystemUI 静态景深路径已运行 + 超级壁纸正在渲染
```

但没有证明：

```text
超级壁纸真实深度数据 -> SystemUI 景深消费
```

所以当前版本仍不能实现显示效果。下一版本必须从超级壁纸 Engine/Renderer 的真实纹理输入定位，而不是继续围绕 `FastPlayer#getDepthFrameAtTime` 等动态视频壁纸接口增加观察日志。
