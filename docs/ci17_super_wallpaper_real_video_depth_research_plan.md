# CI#17 超级壁纸真实视频景深算法取证与移植计划

## 0. 先给结论

### 0.1 是否已经找到动态视频景深算法

没有。

截至 CI#16 和当前 `log(14).txt`，我们只确认了几个“算法参与者”和一个布尔接口，尚未找到动态视频景深的真实计算方法、真实输入对象、深度帧来源、渲染 Surface 绑定过程，也没有证明超级壁纸进入了视频景深分支。

当前实现做的是：

1. 对 `MiuiClockController.isWallpaperSupportDepth()` 强行返回 `true`。
2. 对 `MiuiKeyguardWallPaperManager.isDepthVideoEnable()` 强行返回 `true`，但没有建立它真正读取的 `mDepthVideoReady`、资源类型、深度视频配置和深度帧状态。
3. 从 `ClockDepthAvoidRuleUtils.getCurrentSafeBounds(Context)` 取通用安全区，并拼出一个 `Rect`。
4. 复用或修改时钟/静态图片路径已经产生的字段和缓存。

这不等于移植视频景深算法。尤其不能把 `isDepthVideoEnable=true` 当作算法成功证据。

### 0.2 `log(14).txt` 已证明的事实

以下证据来自 `C:/Users/Zachian/Downloads/log(14).txt`：

- `23:08:34.452` 的模板数据中，锁屏图片是 `resourceType=image`、`magicType=20000`、`isDepthVideo=false`、`depthVideo=`；超级壁纸只出现在 `homeInfo`，为 `resourceType=super_wallpaper`，同样 `isDepthVideo=false`、`depthVideo=null`。
- `23:08:34.453`：
  `MiuiKeyguardWallPaperManager: isDepthVideoEnable magicType:20000 type:image mDepthVideoReady:true enable:false`
  随后出现：
  `KeyguardDepthInteractor: isDepthVideoEnable:true`
  这说明模块改了调用结果，但上游管理器的原始判断仍是 `false`。
- `23:08:34.513`：
  `MiuiKeyguardWallPaperManager: isDepthVideoEnable magicType:0 type:super_wallpaper mDepthVideoReady:true enable:false`
  随后又出现：
  `KeyguardDepthInteractor: isDepthVideoEnable:true`
  这说明超级壁纸没有被管理器识别为视频景深资源，只是下游消费者被强行告知“可用”。
- 同一段日志中多次出现 `MiuiClockController ... isWallpaperSupportDepth on change false`，证明时钟控制器的景深状态在壁纸切换后没有稳定建立。
- `VideoDepth: getLastDepthFrameBitmap ... return null`，说明真实视频景深所需的深度帧没有取得。
- `MiuiWallpaperData` 显示 `wallpaperType='super_wallpaper'`，但 `supportMatting=false`、`partIsDeep={...}` 来自壁纸区域/图片处理状态，不能证明视频景深算法成功。
- 看到 `SnowmountainSuperWallpaper` 的 `Engine onOffsetsChanged` 只能证明超级壁纸引擎在运行，不能证明 SystemUI 的视频景深算法已连接到该引擎。
- 日志没有出现本模块可以证明算法移植成功的完整序列：真实上游类型判定、深度资源准备、深度帧取得、视频 Surface 连接、几何计算、时钟应用结果。

### 0.3 是否需要 SystemUI 以及哪些组件

需要，而且仅修改 AOD 编辑器进程不够。CI#17 必须同时取得并研究以下组件：

| 组件 | 必须确认的内容 | 进程/来源 |
|---|---|---|
| `com.android.systemui` | 时钟控制器、Keyguard 壁纸管理器、KeyguardDepthInteractor、深度 Surface/回调、最终布局应用 | SystemUI dex/oat/vdex |
| `com.miui.aod` / `com.miui.keyguard.editor` | 模板数据如何生成，`isDepthVideo`、`depthVideo`、`resourceType` 如何写入 | AOD/editor dex |
| `com.miui.miwallpaper` | 壁纸类型、组件、壁纸数据、深度帧/预览 IPC、超级壁纸与视频壁纸的分流 | wallpaper service APK/dex |
| `MiuiWallpaperManagerService` | SystemUI 获取壁纸信息、深度区域、深度帧和 Surface 的 Binder 接口 | system_server 或 miwallpaper service |
| `KeyguardDepthInteractor` | 谁调用 `isDepthVideoEnable`、谁请求深度帧、谁生成/应用前景层 | SystemUI |
| `MiuiKeyguardWallPaperManager` | `magicType`、`type`、`mDepthVideoReady` 的真实赋值链和 `enable` 计算链 | SystemUI |
| `MiuiClockController` / Clock View | 避让矩形只是布局结果，必须追溯它的输入和应用者 | SystemUI/clock dex |
| `SurfaceFlinger` / SurfaceControl | 只用于确认 Surface 层级和 buffer 是否存在，不能把日志当算法实现 | native/runtime |

CI#17 的第一目标不是继续猜类名，而是把上述组件之间的真实调用链和对象字段记录出来。

---

## 1. CI#17 目标

本轮只接受以下目标：

1. 从设备当前版本的 SystemUI、AOD 和壁纸服务 dex 中找出动态视频景深真实入口。
2. 通过运行时观测确认动态视频壁纸在真实场景下的完整调用序列。
3. 对比“静态图片景深”“动态视频景深”“超级壁纸”三种场景的输入、输出和 Surface。
4. 明确超级壁纸是否能复用视频景深算法；如果不能，明确缺失的是深度帧、视频配置、Surface、资源协议还是算法本身。
5. 在未获得足够证据前，禁止继续扩大 `true` 返回值 Hook。
6. 产出下一轮实现所需的准确类名、方法签名、字段类型、调用顺序和回退条件。

CI#17 不以“看到时钟前景层”作为成功标准。必须证明前景层来自当前超级壁纸会话，而不是上一个静态壁纸会话的缓存。

---

## 2. 设备准备：傻瓜式指令

以下命令在 Windows PowerShell 执行。每一步都必须保存输出文件，不要只看屏幕。

### 2.1 建立工作目录

```powershell
$work = "C:\Users\Zachian\ci17"
New-Item -ItemType Directory -Force $work, "$work\logs", "$work\dex", "$work\diff", "$work\reports" | Out-Null
```

### 2.2 确认设备和系统版本

```powershell
adb devices -l | Tee-Object "$work\reports\adb_devices.txt"
adb shell getprop ro.build.version.incremental | Tee-Object "$work\reports\build_incremental.txt"
adb shell getprop ro.build.version.hyperos | Tee-Object "$work\reports\build_hyperos.txt"
adb shell getprop ro.product.device | Tee-Object "$work\reports\device.txt"
adb shell pm path com.android.systemui | Tee-Object "$work\reports\systemui_paths.txt"
adb shell pm path com.miui.aod | Tee-Object "$work\reports\aod_paths.txt"
adb shell pm path com.miui.miwallpaper | Tee-Object "$work\reports\miwallpaper_paths.txt"
```

如果 `adb devices` 没有一台状态为 `device` 的设备，立即停止，不要继续分析电脑上的 APK。

### 2.3 导出 APK、oat、vdex 和运行时信息

```powershell
adb pull /system_ext/priv-app/MiuiSystemUI $work\dex\MiuiSystemUI 2>&1 | Tee-Object "$work\reports\pull_systemui.txt"
adb pull /system_ext/priv-app/MiuiKeyguard $work\dex\MiuiKeyguard 2>&1 | Tee-Object "$work\reports\pull_keyguard.txt"
adb pull /system/priv-app/MiuiAod $work\dex\MiuiAod 2>&1 | Tee-Object "$work\reports\pull_aod.txt"
adb pull /system_ext/priv-app/MiuiWallpaper $work\dex\MiuiWallpaper 2>&1 | Tee-Object "$work\reports\pull_wallpaper.txt"
adb shell dumpsys package com.android.systemui > "$work\reports\dumpsys_systemui.txt"
adb shell dumpsys package com.miui.aod > "$work\reports\dumpsys_aod.txt"
adb shell dumpsys package com.miui.miwallpaper > "$work\reports\dumpsys_miwallpaper.txt"
adb shell dumpsys wallpaper > "$work\reports\dumpsys_wallpaper.txt"
adb shell dumpsys SurfaceFlinger > "$work\reports\dumpsys_surfaceflinger.txt"
```

如果设备路径不同，不得猜路径。先使用 `pm path` 返回的每一条路径逐条 `adb pull`，并在报告中记录实际路径。

### 2.4 准备反编译和 dex 搜索工具

必须使用与当前设备 dex 兼容的工具，例如 JADX、baksmali、dexdump 或 apktool。把工具版本写入报告：

```powershell
jadx --version | Tee-Object "$work\reports\jadx_version.txt"
java -version 2>&1 | Tee-Object "$work\reports\java_version.txt"
```

如果本机没有 JADX，不要用网络上不同系统版本的源码代替；先安装或使用本仓库已有工具，并记录版本。

---

## 3. 日志采集：必须做三个独立基线

每次测试前都清空日志并重启相关进程。三个基线必须使用同一设备、同一屏幕方向、同一时钟模板。

### 3.1 基线 A：静态图片景深

操作顺序：

1. 在系统设置中选择一张普通静态图片。
2. 开启景深/主体抠像。
3. 应用锁屏时钟和景深效果。
4. 锁屏一次，再亮屏一次。
5. 保持静态图片 20 秒，不切换壁纸。

采集：

```powershell
adb logcat -c
adb shell am force-stop com.android.systemui
adb shell am force-stop com.miui.aod
adb shell am force-stop com.miui.miwallpaper
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
adb logcat -v threadtime -b all -d > "$work\logs\baseline_static.txt"
```

### 3.2 基线 B：真正动态视频壁纸景深

操作顺序：

1. 选择系统能够确认是视频资源的动态壁纸，不要选择超级壁纸。
2. 确认模板数据中的 `resourceType` 是 `video` 或设备实际使用的动态视频类型。
3. 开启与静态图片相同的时钟景深设置。
4. 锁屏/亮屏各一次，保持 20 秒。

采集文件命名为 `baseline_video.txt`。

### 3.3 基线 C：超级壁纸

操作顺序：

1. 选择 `snowmountain` 或另一款系统超级壁纸。
2. 不修改时钟模板，直接应用。
3. 锁屏/亮屏各一次，保持 20 秒。
4. 再从静态图片切换到超级壁纸，重复一次用户报告的复现步骤。

采集文件命名为 `baseline_super.txt`。

### 3.4 只筛选证据，不凭整份日志猜

```powershell
foreach ($name in @("baseline_static", "baseline_video", "baseline_super")) {
  rg -n -i "MiuiKeyguardWallPaperManager|KeyguardDepthInteractor|VideoDepth|MiuiClockController|Depth|depthVideo|isDepthVideo|resourceType|magicType|SurfaceControl|SurfaceFlinger|WallpaperInfo|mDepthVideoReady|supportMatting" "$work\logs\$name.txt" > "$work\logs\$name.filtered.txt"
}
```

每个基线都必须回答：

- 谁第一次决定这是 image/video/super_wallpaper？
- `isDepthVideo` 从哪里读、在哪里写？
- `mDepthVideoReady` 何时变为 true，依赖哪个对象？
- 深度帧从哪里取得，返回的是 bitmap、buffer、Surface 还是 Binder 对象？
- 哪个方法把深度结果交给 KeyguardDepthInteractor？
- 哪个方法计算前景层/后景层，哪个方法只负责时钟避让？
- 哪个 Surface 显示了景深结果，层名和 owner 是什么？

---

## 4. 静态分析：必须找到真实调用链

### 4.1 在 dex 中搜索已知字符串

对 SystemUI、AOD 和壁纸服务分别反编译，再搜索：

```text
isDepthVideoEnable
mDepthVideoReady
isDepthVideo
depthVideo
getLastDepthFrameBitmap
KeyguardDepthInteractor
VideoDepth
getMiuiLockPartWallpaperIsDeep
getMiuiWallpaperPreview
resourceType
magicType
supportMatting
getDepthAvoidRect
isWallpaperSupportDepth
```

不要只记录命中方法名。必须记录：

- 完整类名。
- 完整方法签名。
- 参数的实际含义。
- 返回值的实际含义。
- 读取的每个字段及类型。
- 写入的每个字段及写入时机。
- 调用者和被调用者各一层。
- 异常或空值时的回退分支。

### 4.2 优先研究 `MiuiKeyguardWallPaperManager`

从 `isDepthVideoEnable` 的方法体开始，向上追踪：

1. `magicType` 的来源和取值表。
2. `type` 的来源，是字符串、枚举还是 WallpaperInfo/resourceType。
3. `mDepthVideoReady` 的声明、写入点和清理点。
4. `enable` 的完整布尔表达式。
5. `isDepthVideoEnable` 的所有调用者。
6. 调用者在返回 true 后做了什么，而不是只看该方法本身。

当前日志已经表明 `enable:false`，所以 CI#17 必须找出使它变为 true 的完整条件，不能再直接改返回值。

### 4.3 研究 `KeyguardDepthInteractor`

必须找出以下方法的真实实现或调用链：

- `getLastDepthFrameBitmap`。
- `updateDeductedImageView`。
- 任何 `depth`、`matting`、`front`、`back`、`mask`、`deducted`、`surface` 相关方法。
- 任何将 bitmap/buffer 设置给 ImageView、Surface 或 RenderNode 的方法。

如果深度帧来自 `com.miui.miwallpaper` 的 Binder 接口，必须记录接口 descriptor、transaction code、参数和返回对象；不能只记录日志字符串。

### 4.4 研究模板数据生成链

从 AOD/editor 的 `WallpaperInfo` 追踪：

- `isDepthVideo` 的赋值点。
- `depthVideo` 对象的类型、字段和序列化方式。
- `wallpaperInfoForVideo` 的赋值点。
- `resourceType`、`videoType`、`magicType` 的归一化方法。
- 静态图片开启主体抠像后，哪些字段写入了 `cropSubject`、`mask`、`frontBack`。
- 切换到超级壁纸后，哪些字段仍然来自旧静态模板。

CI#17 的一个重点是验证是否存在“静态模板数据未失效”的缓存污染。如果存在，必须先解决会话身份和模板清理，再研究视频算法。

---

## 5. 运行时 Hook 取证方案

### 5.1 只观察，不修改返回值

CI#17 第一版诊断 Hook 必须是 observe-only：

- 不返回 `true` 替换原结果。
- 不写 `mDepthVideoReady`。
- 不写 `isDepthVideo`。
- 不写全局 Settings。
- 不修改 WallpaperInfo 类型。
- 不修改 `getDepthAvoidRect()` 的结果。

每个观察日志必须包含：进程、线程、对象 identityHashCode、壁纸会话 identity、原始返回值、关键字段快照和调用顺序编号。

### 5.2 必须记录的最小方法集合

实际签名以设备 dex 为准，下面是最低候选集合：

```text
MiuiKeyguardWallPaperManager.isDepthVideoEnable(...)
MiuiKeyguardWallPaperManager.set...Depth...
MiuiKeyguardWallPaperManager.update...Wallpaper...
KeyguardDepthInteractor.getLastDepthFrameBitmap(...)
KeyguardDepthInteractor.updateDeductedImageView(...)
MiuiClockController.isWallpaperSupportDepth(...)
MiuiClockController.getDepthAvoidRect(...)
MiuiWallpaperManagerService.getMiuiWallpaperPreview(...)
MiuiWallpaperManagerService.getMiuiLockPartWallpaperIsDeep(...)
MiuiWallpaperManagerService.updateWallpaperComponent(...)
所有创建/销毁/绑定 Surface 的方法
```

### 5.3 每次调用必须打印的字段

```text
eventId
timestamp
processName
className#methodSignature
thisIdentity
wallpaperType
resourceType
magicType
isDepthVideo
depthVideo
mDepthVideoReady
supportMatting
componentName
wallpaperSessionIdentity
surfaceIdentity/layerName
inputWidth/inputHeight/density/rotation
returnValue
exception
```

### 5.4 调用顺序编号

不要依赖日志时间猜顺序。诊断器必须在模块内生成单调递增的 `eventId`，并把同一壁纸会话的事件写成如下格式：

```text
CI17 event=0001 session=abc original=image method=...
CI17 event=0002 session=abc original=image method=...
CI17 event=0003 session=def original=super_wallpaper method=...
```

切换壁纸、SystemUI 重启、Surface 重建都必须生成新 session，不允许沿用旧 session 的 event 序列。

---

## 6. 三种场景的逐字段对照表

对静态、视频、超级壁纸各生成一张 CSV 或 Markdown 表，至少包含：

| 项目 | 静态图片 | 动态视频 | 超级壁纸 | 结论 |
|---|---|---|---|---|
| WallpaperInfo.resourceType |  |  |  |  |
| magicType |  |  |  |  |
| isDepthVideo |  |  |  |  |
| depthVideo 对象 |  |  |  |  |
| wallpaperInfoForVideo |  |  |  |  |
| mDepthVideoReady |  |  |  |  |
| 深度帧来源 |  |  |  |  |
| 深度帧类型 |  |  |  |  |
| 视频 Surface |  |  |  |  |
| 前景层 Surface/View |  |  |  |  |
| 后景层 Surface/View |  |  |  |  |
| safe-area 输入 |  |  |  |  |
| getDepthAvoidRect 输出 |  |  |  |  |
| 时钟最终应用位置 |  |  |  |  |
| 会话代次 |  |  |  |  |

如果某字段无法取得，必须填 `UNKNOWN` 并写明原因，不能填推测值。

---

## 7. 解决用户复现步骤的专门检查

用户复现步骤是：

1. 静态壁纸开启景深。
2. 切换到超级壁纸。
3. 观察到静态壁纸算出的景深前层被贴到超级壁纸上。
4. 超级壁纸自身没有出现景深。

CI#17 必须针对这四步逐项回答：

### 7.1 切换前

- 记录静态壁纸 session、WallpaperInfo identity、mask/front/back/depth frame identity。
- 记录前景层的 bitmap/buffer/Suface identity。
- 记录时钟和景深 View 的 owner。

### 7.2 切换瞬间

- 记录旧 session 是否失效。
- 记录 `mDepthVideoReady` 是否清零。
- 记录旧 front/mask/depth frame 是否释放或替换。
- 记录新的 `WallpaperInfo` 是否完整更新为 `super_wallpaper`。
- 记录 AOD 模板中的 `isDepthVideo/depthVideo/wallpaperInfoForVideo` 是否仍是旧值。

### 7.3 切换后

- 记录超级壁纸的真实 component 和 Engine/Surface identity。
- 记录真实视频算法是否请求深度帧。
- 记录深度帧返回是否为 null。
- 记录前景层是否仍引用静态壁纸 session 的 identity。
- 记录时钟景深 Rect 是否来自旧缓存。
- 记录超级壁纸是否有自己的前景/后景输出，还是只有普通 Wallpaper Surface。

### 7.4 判定

只要切换后任意一个前景层、mask、depth frame 或 Rect 的 session identity 仍属于静态壁纸，就判定为缓存污染，不能通过 CI#17。

---

## 8. 代码改造边界

CI#17 取证完成前，代码只能增加诊断，不得增加更多算法伪造。必须先做以下隔离：

1. 暂时关闭 `isDepthVideoEnable=true` 的强制返回，记录原始结果。
2. 暂时关闭 `isWallpaperSupportDepth=true` 的末端强制返回，除非测试专门验证时钟回退。
3. 禁止 `VideoDepthPolicy.applyVideoDepth()` 在未知真实算法入口前写入时钟缓存字段。
4. `getDepthAvoidRect()` 只记录原始结果和调用链，不返回自拼 Rect。
5. 所有诊断以 feature flag 控制，关闭时完全不改变系统行为。
6. 只有当真实动态视频场景的字段和调用链被证明后，才新增针对同一字段/同一对象实例的兼容映射。

---

## 9. SystemUI 需要的最小证据包

下一轮实现前，必须收集并保存以下文件；缺一不可：

1. 当前设备 `MiuiSystemUI.apk` 或对应 split APK。
2. 当前设备 SystemUI 的 `classes*.dex`、oat/vdex（如果可读）。
3. `com.miui.aod` 的 APK/dex。
4. `com.miui.miwallpaper` 的 APK/dex。
5. `dumpsys wallpaper`、`dumpsys SurfaceFlinger`、SystemUI 进程日志。
6. 静态、动态视频、超级壁纸三份过滤日志。
7. 三种场景的模板 JSON/WallpaperInfo 字段快照。
8. 运行时 Hook 打印的完整方法签名和字段类型。

没有这些 SystemUI 证据，不允许声称已经找到视频景深算法，也不允许继续做“兼容类型映射”。

---

## 10. 验收标准

### 10.1 研究完成标准

- 能指出动态视频景深的真实入口类、方法签名和调用者。
- 能指出 `mDepthVideoReady` 从哪里变为 true，以及为什么超级壁纸当前为 false。
- 能指出深度帧的真实来源和返回类型。
- 能指出前景层/后景层的真实渲染路径。
- 能证明 `KeyguardDepthInteractor.isDepthVideoEnable=true` 不是算法本身，只是下游判断。
- 能证明静态壁纸切换到超级壁纸时旧 session、旧 mask 和旧 depth frame 被清理。
- 能在不修改返回值的 observe-only 模式下复现三种基线。

### 10.2 实现前必须拒绝的状态

- 只有 `isWallpaperSupportDepth=true`。
- 只有 `KeyguardDepthInteractor.isDepthVideoEnable=true`。
- `MiuiKeyguardWallPaperManager` 原始结果仍为 `enable:false`。
- `isDepthVideo=false`、`depthVideo=null`，却声称视频算法已运行。
- 深度帧返回 null，却声称超级壁纸拥有景深。
- 前景层 identity 来自静态壁纸 session。
- 只改 `Rect` 或 safe-area，不知道前景/后景如何渲染。
- 没有 SystemUI dex 和运行时调用顺序证据。

### 10.3 最终功能验收

只有同时满足以下条件，CI#17 才能进入实现阶段：

1. 超级壁纸拥有独立的新 session。
2. 超级壁纸的深度输入不是静态壁纸遗留对象。
3. 真实视频景深入口被调用，且输入合法。
4. 深度帧或等价深度资源真实返回。
5. 前景/后景输出绑定到当前超级壁纸 Surface 或明确的 SystemUI 渲染层。
6. 时钟避让矩形来自当前会话计算，而不是旧 Rect。
7. 普通静态和动态视频壁纸行为不变。
8. 关闭模块后所有原始结果恢复。

---

## 11. CI#17 执行顺序

1. 保存 `log(14).txt` 的关键证据和本计划。
2. 关闭所有强制 `true` 的景深 Hook，重新采集三份基线。
3. 导出 SystemUI/AOD/miwallpaper 的实际 dex 和版本信息。
4. 静态搜索 `isDepthVideoEnable`、`mDepthVideoReady`、`getLastDepthFrameBitmap` 的调用图。
5. 加入 observe-only 运行时 Hook，重启 SystemUI 后采集事件序列。
6. 完成静态/视频/超级壁纸逐字段对照表。
7. 定位旧静态前景层被复用的具体字段或对象 identity。
8. 定位超级壁纸缺少的真实视频输入或渲染 Surface。
9. 只有证据闭环后，编写 CI#18 实现计划或直接实现真实兼容层。
10. 在 CI#17 未完成前，不触发新的算法伪造版本发布。

## 12. 一句话结论

CI#16 的问题不是“超级壁纸的 Rect 算错”，而是根本没有找到动态视频景深算法；当前日志证明我们只把下游布尔判断改成了 true，却没有让 `MiuiKeyguardWallPaperManager`、`KeyguardDepthInteractor`、深度帧来源和 SystemUI 渲染链真正产生超级壁纸的景深。CI#17 必须先取得 SystemUI/AOD/miwallpaper 的真实算法证据，再谈移植。
