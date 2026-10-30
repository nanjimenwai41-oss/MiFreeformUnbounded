# Changelog

[中文](#中文) · [English](#english) · [返回中文 README](README.md) · [English README](README_EN.md)

## 中文

### v3.0.2-ci.94 · 内部测试
- 接收端晚于壁纸启动或重建时，自动补发当前会话静止壁纸的最后有效遮罩；独立 bootstrap 协议保留真实采样时间，仅允许新接收端首个输入恢复。Provider 支持首次解锁前的内存端点交换，避免访问未解锁的外观配置。
- 同一窗口的所有时钟在一次绘制前统一采用遮罩快照，再提交对应壁纸事务；已超时释放或过期事务不再更新旧遮罩/回退壁纸。配对实验仍默认关闭，完整开机与逐帧验收另行记录。

### v3.0.2-ci.93 · 内部测试
- 根据实机调用补充壁纸 Engine 的 SurfaceHolder 观察点；配对传输先确认接收端协议，避免旧版 SystemUI 忽略缓冲事务。实验仍默认关闭，等待真机同帧呈现与逐帧轮廓验收。

### v3.0.2-ci.92 · 内部测试
- 验证壁纸 BLAST 缓冲区与同帧时钟遮罩配对提交的实验路径，避免仅降低读回延迟仍留下运动窄缝；实验通过设备调试属性启用，尚未作为边缘验收通过。

### v3.0.2-ci.91 · 内部测试
- 只在精确 ELF、driver/PlatformEGL vtable 及当前 context 身份一致时，复制 Framework 已成功使用的 EGL 创建参数。设备实际请求 ES2/no-error，GL 查询却报告3.2/flags=0，CI90 因此不能重建真实共享参数。

### v3.0.2-ci.90 · 内部测试
- 根据设备 Framework 的 EGL 创建路径，匹配宿主 GLES 版本、实际 no-error/debug/robust 标志及 reset 策略；CI89 的共享 context 被 EGL_BAD_MATCH 拒绝，本版继续进行真机验证。

### v3.0.2-ci.89 · 内部测试
- 兼容 Filament 无 EGLConfig 的共享 context，避免把 EGL_CONFIG_ID=0 当作可选择配置；CI88 实机未产生采样，不能作为有效验收。

### v3.0.2-ci.88 · 内部测试
- 将 GPU fence 等待与 PBO 映射移入共享 EGL context 的专用 worker，不再等下一次 renderer 回调才读回，减少壁纸运动时遮罩落后一帧的窄缝；渲染线程不等待 worker，只保留一个在途采样。
- CI87 原始录像仍观察到动态轮廓错位，本版须重新进行真实帧龄、性能与原始帧验收，尚不宣称全部解决。

### v3.0.2-ci.87 · 内部测试
- 将已验证的天空/山体深度簇分离移到 GPU，并从原始屏幕尺寸深度生成 alpha；四个 alpha 像素打包为一个 RGBA 读回纹素，避免低分辨率轮廓放大及 CPU 对每个深度像素执行对数转换。
- native worker 保持 JNI 连接，直接复制单通道掩码到共享内存；时钟在绘制前采用刚完成解码的最新帧，减少额外等待。该实验仍以真机原始帧检查为准，不宣称已消除所有单帧缺口。

### v3.0.2-ci.86 · 内部测试
- 修复本地手工拷贝的旧 native 库覆盖 Gradle 新编译库；只打包当前 native 构建产物，并在 CI 和发布流程中校验 APK 与编译库的 ELF build ID。
- CI85 真机检查发现仍加载旧的 512 遮罩，故 CI85 不作为采样和边缘改进的有效验收；以本版重新测量，景深单帧缺口仍未宣称全部解决。

### v3.0.2-ci.85 · 内部测试
- 景深遮罩长边从 512 增至 1024，减少低分辨率轮廓放大；CPU 发布与 GPU 采集独立运行，只保留最新待处理帧，避免发布阻塞下一次采样。
- 采样改用保持相位的 120Hz 上限，避免微小帧间抖动使采样率减半；补充节拍、分辨率及传输边界回归。完整 AOD 转场的边缘和单帧缺口仍须真机逐帧验收。

### v3.0.2-ci.84 · 内部测试
- 顶栏保留 MIUIX 背景取样，改用全分辨率原生高斯的多档渐变，分别遮罩背景模糊层，不模糊标题和操作按钮；不支持运行时着色器的平台采用实色顶栏。
- 该实现须在真机上比较块状感、渐变衔接和滚动帧时间；景深边缘仍未通过逐帧验收。

### v3.0.2-ci.83 · 内部测试
- 首页版本信息使用编辑图标表示息屏与锁屏编辑、图层图标表示 MiWallpaper；右上角使用圆环箭头重启图标。
- 验证 MIUIX 官方渐进模糊实现；AOD 到锁屏景深边缘仍在原始录像逐帧核验，未宣称通过。

### v3.0.2-ci.82 · 内部测试
- 根据真机反汇编和内存证据，修复雪山 driver 缓存旧的深度丢弃入口，导致进程重建后不再生成景深遮罩的问题；同时校验精确 ELF 与 driver 身份，保留已安装的第三方入口。
- 增加缓存入口幂等、driver 重建及拒绝覆盖第三方入口的宿主回归，并扩充只读设备诊断。真机回归进行中。

### v3.0.2-ci.81 · 内部测试
- 设置一级页仅保留入口，主题、颜色、模糊、导航及手势全部收入二级外观页；首页移除三项功能开启提示，并区分系统界面与 AOD 的版本图标。
- 顶栏直接采用 MIUIX 渐进模糊默认管线，移除额外混色；修正关于页镂空图标右侧裁切，快速组合改用动态简洁勾选，每次打开弹窗复位至顶部。
- 将边缘安全距离上限由 320px 扩大至 2000px，保留 196px 默认值及精细调节。

### v3.0.2-ci.80 · 内部测试
- 重启弹窗隐藏导航、调整组合按钮和右上角图标；首页仅保留当前超级壁纸版本，其余收进更多弹窗。
- 将外观移至第三个一级设置页，原设置改名配置，整理模块说明，并采用 KernelSU 的名称与镂空图标流光混色链。
- 重启使用单次 ROOT 会话逐项核对所有包及子进程，区分未运行与失败，SystemUI 最后执行；不再 force-stop 壁纸。
- 景深接收端后台续期、GL 错误退避恢复，避免输入持续更新使解码饿死；保留单壁纸遮罩渲染路径。真机回归进行中。

### v3.0.2-beta.2 · 预发布
- 保持 beta.1 的 HyperOS 4 景深、前景遮挡、玻璃时钟与 AOD 转场适配，并修正 GitHub Actions 的 NDK 发布构建配置。
- 统一 MIUIX 主题与主界面布局，整理重启操作，补齐页面缩放、导航手势、横屏侧栏及系统状态联动；保留原有翻盖动画和四分之一有效步进微调。

### v3.0.2-beta.1 · 预发布
- 完成 HyperOS 4 超级壁纸景深、前景遮挡与玻璃时钟适配，改善动态帧传输的流畅度和锁屏转入 AOD 时的时钟遮罩衔接。
- 增加九个静态作用域的单项及一键重启，并补齐设备诊断导出与保护逻辑。

### v3.0.2-ci.77 · 内部测试
- 在 OS4 已反汇编确认的锁屏到 AOD 联动入口执行前重新核实超级壁纸并保留最后有效时钟遮罩，并用生命周期代际阻止晚到的旧清理任务抹掉新帧；尚待真机多次转场验证。

### v3.0.2-ci.73 · 内部测试
- 按 OS4 实际 DEX 的调用顺序，在有效前景帧成功提交到原厂 Surface 后补充检查并恢复被父方法隐藏的锁屏前景容器；沿用已有的前景帧不早于景深锚点判定，增加恢复结果与状态门诊断。尚待真机验证。

### v3.0.2-ci.71 · 内部测试
- 根据 HyperOS 4 实际 DEX 的调用顺序，在系统完成锁屏前景父容器可见性更新后重新显示有效景深前景，修复子 TextureView 已恢复但父层随后被系统隐藏的回归。

### v3.0.2-ci.70 · 内部测试
- 在恢复山体前景层前增加 OS4 实际使用的 `PowerManager.isInteractive()` 状态门，保留锁屏遮挡修复并避免屏幕进入 Dozing/AOD 时强行显示前景。

### v3.0.2-ci.69 · 内部测试
- 修复系统可见性刷新将真实前景纹理重新隐藏的问题；仅在同帧景深/前景有效、锁屏显示且超级壁纸可见时重新启用遮挡层，其他状态跟随系统层级。

### v3.0.2-ci.68 · 内部测试
- 前景帧传输复用线程局部像素缓冲，移除每帧整图 Bitmap 克隆；颜色与深度统一采用最近采样，并实际剔除近黑异常像素，针对前景卡顿、轮廓错位和黑色伪影继续优化。

### v3.0.2-ci.58 · 内部测试
- SnowMountain 插件加载崩溃定位到 CI57 的 loader GOT 替换后，移除 loader GOT 写入，改为只读记录已反编译确认的 `dlopen`、`dlsym`、`eglGetProcAddress` 槽位。

### v3.0.2-ci.57 · 内部测试
- 按已确认的设备 GOT relocation 替换 `libFramework.so` 的 `dlopen`、`dlsym`、`eglGetProcAddress` 调用，以追踪真实 loader 路径；该实验导致 SnowMountain `_DoLoadPlugin` 空指针崩溃，CI58 已移除。

### v3.0.2-ci.56 · 内部测试
- 尝试通过 ByteHook 跟踪 Vulkan `vkGetInstanceProcAddr` / `vkGetDeviceProcAddr`；设备返回的 status 10 且无原函数地址，确认该 PLT 路径未安装有效 hook，CI57 改为基于设备 GOT 证据继续调查。

### v3.0.2-ci.55 · 内部测试
- 在 SnowMountain renderer 初始化前注册只读 Vulkan `dlsym` 跟踪，记录设备实际解析的 Vulkan API 符号及函数地址，为后续深度附件读回确定真实调用链；原函数参数、返回值和渲染输出保持不变。

### v3.0.2-ci.54 · 内部测试
- 修复 SnowMountain native 库延迟加载时 camera probe 因已有 ByteHook stub 提前返回、跳过 GOT fallback 的问题；renderer 初始化重试现在会再次解析并安装已验证 relocation。

### v3.0.2-ci.53 · 内部测试
- 按设备 `libFramework.so` 已确认的唯一 `R_AARCH64_JUMP_SLOT` relocation 增加 GOT patch 回退；不依赖 ShadowHook linker 初始化，记录真实相机 RenderTarget/View 样本并原样调用原函数。

### v3.0.2-ci.52 · 内部测试
- 将 ShadowHook 从 ByteHook 传递的 1.1.1 升级到官方 2.0.1；官方版本支持 Android API 37，用于修复设备上 `Init linker mod failed` 的初始化失败。

### v3.0.2-ci.51 · 内部测试
- 在设备 ELF 已确认的 Filament 相机符号上增加 ShadowHook 地址级只读观测回退，保留 ByteHook PLT 探针并记录真实 RenderTarget/View 样本。

### v3.0.2-ci.50 · 内部测试
- 依据设备 `libFramework.so` 唯一相机 `R_AARCH64_JUMP_SLOT` relocation，改用 SnowMountain 进程内 ByteHook 全 caller 方式捕获实际运行路径，并记录 render 样本。

### v3.0.2-ci.49 · 内部测试
- 移除 Filament 相机 PLT 探针过窄的 callee 路径限制；继续仅接受 SnowMountain 实际 ELF 调用方，并记录安装状态与 RenderTarget 样本。

### v3.0.2-ci.48 · 内部测试
- 按 Android APK 内 native library 的实际路径调整相机 PLT 探针，精确限定 `libFramework.so` callee，并记录 ByteHook 返回的安装状态。

### v3.0.2-ci.47 · 内部测试
- 按 HyperOS 4 设备 ELF 符号与 relocation 结果，将 SnowMountain 相机只读探针改为 ByteHook 精确拦截 `libFramework.so` 的 Filament 相机渲染 PLT 调用，并保留原函数返回值。

### v3.0.2-ci.46 · 内部测试
- 为 HyperOS 4 SnowMountain 的 Filament 相机增加只读 native 诊断，记录相机是否绑定独立 RenderTarget；Vulkan 渲染期间不调用 GLES，也不改变帧输出。

### v3.0.2-ci.45 · 内部测试
- 关闭 Snowmountain Vulkan renderer 上无效且会触发 GLES 驱动崩溃的深度探针，保留运行时观察日志。

### v3.0.2-ci.44 · 内部测试

- 在实际 `S1ThreadHooker.threadSwapBuffers(int)` 回调前后只读记录 EGL/GLES context 状态，确认渲染线程的深度读取边界。

### v3.0.2-ci.43 · 内部测试

- 在 Snowmountain renderer 同一 GL 线程每 120 帧诊断一次实际 framebuffer 深度附件；读取结果仍需设备日志验证，不会自动启用未经验证的景深合成。

### v3.0.2-ci.42 · 内部测试

- 适配 HyperOS 4.0.4 AOD 玻璃时钟降级路径，覆盖 `shouldDisableGlassEffect(int, CommonConfig)` 与新版 `computeSupportedClockEffect(CommonConfig, int, Context)` 签名。
- Snowmountain EGL 深度采集仍未通过真实帧验证；CI43 设备日志显示 ShadowHook 初始化失败，ByteHook 未覆盖应用私有 renderer。

### v3.0.2-ci.36 · 内部测试

- 真实超级壁纸景深帧到达 SystemUI 时，取消此前由 `updateDeductedImageView()` 启动且仍在等待的原厂 `crop_subject` 解析协程，避免旧协程在新 GPU 帧应用后继续触发无效 gate；无真实帧阶段保持原厂路径。

### v3.0.2-ci.35 · 内部测试

- 修正 `KeyguardDepthInteractor.updateDeductedImageView()` 与观察 hook 的规则冲突；真实 GPU 景深帧存在时，将已验证 Bitmap 提交到系统景深视图并跳过仅支持 `crop_subject` 文件的 stock 分支。

### v3.0.2-ci.34 · 内部测试

- 在已验证真实超级壁纸景深帧存在时，直接保护景深设置回写和 `updateDeductedImageView()` 消费入口，避免短暂会话标志变化跳过真实景深状态。

### v3.0.2-ci.33 · 内部测试

- 在设备真实 `KeyguardDepthInteractor.updateDeductedImageView()` 消费入口前后重新写入已验证景深状态，避免异步设置流在实际合成前将 `depthEffectEnableInner` 清零。

### v3.0.2-ci.32 · 内部测试

- 为设备真实 `KeyguardPanelViewController$onViewAttachedToWindow$1` 内部类补充独立 hook profile，确保 `handleValueChanged(int,boolean)` 回写路径实际安装。

### v3.0.2-ci.31 · 内部测试

- 接入设备真实 `KeyguardPanelViewController$onViewAttachedToWindow$1.handleValueChanged(int,boolean)` 回写路径；在已验证超级壁纸景深帧存在时，保护 `depthEffectEnable` 与 `depthEffectEnableInner` 不被设置回调立即清零。

### v3.0.2-ci.30 · 内部测试

- 修复真实 `KeyguardCommonSettingObserver.getDepthEffectEnable()` 未加入方法匹配集合的问题，确保已验证景深帧到达时实际设置源 hook 能安装并维持景深开关。

### v3.0.2-ci.28 · 内部测试

- 真实超级壁纸 EGL 景深帧到达 SystemUI 后，先启用设备实际的 `depthEffectEnableInner`/景深开关，再触发锁屏景深表面重建，避免 HyperOS 收到有效帧后因开关仍为关闭而跳过合成。
- 仅对已验证的真实景深帧生效，普通壁纸和无有效帧路径保持原始行为。

### v3.0.2-ci.29 · 内部测试

- 接入设备真实 `KeyguardCommonSettingObserver.getDepthEffectEnable()` 设置源；仅在当前超级壁纸已有验证景深帧时恢复景深开关，避免异步设置流再次把锁屏合成关闭。

### v3.0.2-ci.14 · 内部测试

- 修复部分 HyperOS SystemUI 控制器不暴露 `supportSubject/cropSubject` 字段，导致真实 `crop_subject` 已存在却被误判为无景深输入的问题。
- 仅检查系统已生成的主体输入文件是否存在并保持超级壁纸身份校验，不读取、改写或伪造主体像素。

### v3.0.2-ci.13 · 内部测试

- 修复超级壁纸进入 `updateShowDepthState()` 后仍被系统清除静态主体遮罩景深状态的问题。
- 仅对已确认存在系统主体输入的超级壁纸恢复 `depthEffectEnable` 与 `depthEffectEnableInner`；普通壁纸、原始主体图、玻璃时钟和真实深度帧校验保持不变。

### v3.0.2-ci.12 · 内部测试

- 按设备实际签名接入 `MiuiKeyguardWallPaperManager.getLastDepthFrameBitmap(Function)` 异步景深回调。
- 只有已验证的真实超级壁纸深度帧才会回调给 SystemUI；缺少真实帧时继续保留 MIUI 原始请求，避免再次错误抠图。

### v3.0.2-ci.11 · 内部测试

- 对同一 nRender 分别记录进入前和返回后的 GL 上下文状态，验证景深读回的正确时机；进入前诊断默认不发布帧。
- 根据 ci.10 证据修正诊断结论：返回后已无 EGL context/depth attachment，避免把无效读回误判为平坦景深。

### v3.0.2-ci.10 · 内部测试

- 为真实 OpenGL depth readback 增加独立诊断日志，避免统一 hook 限流隐藏第 30 帧的实际结果。
- 不改变深度帧验证、普通壁纸、玻璃时钟或错误抠图保护逻辑。

### v3.0.2-ci.9 · 内部测试

- 在雪山超级壁纸的原生渲染线程尝试读取真实 OpenGL depth buffer；只有深度附件存在且数据有有效范围时才发布深度帧。
- SystemUI 仅消费已验证的雪山深度帧，未读到有效 depth buffer 时保持系统原始景深和普通壁纸路径。
- 实验 APK 继续通过时间后缀单独导出，标准 APK 不被实验导出覆盖。

### v3.0.2-ci.8 · 内部测试

- 根据当前设备 dex 的真实签名覆盖 `n2.a`、`n2.a$a` 和 `com.xiaomi.utils.JNIUtils`，记录雪山渲染生命周期与 native render 调用。
- 景深能力、渲染开关、避让矩形和深度参数恢复为系统原始结果；未确认真实深度帧前不再强制开启，避免错误抠图和旧会话残留。
- native renderer 销毁时清理深度桥接状态；本轮仍未伪造深度帧。

### v3.0.2-ci.7 · 内部测试

- 按当前壁纸元数据严格区分超级壁纸与普通图片/动态壁纸，切换到普通壁纸时立即清理景深会话和桥接状态。
- 仅在真实深度回调或原生 `subject_mask` 输入就绪时开启景深效果；`updateVideoDepthVisibility` 的景深参数会正确传递为启用。
- 移除普通渲染帧亮度/边缘伪造深度图的路径，保留原始 Bitmap 与 URI，避免错误抠图。

### v3.0.2-ci.3 · 内部测试

- 增加系统界面、息屏与锁屏编辑、超级壁纸的分别重启和全部静态作用域重启；静态作用域由模块声明管理，不再要求维护 LSPosed 推荐列表。

### v3.0.2-ci.4 · 内部测试

- 将首页重启入口明确拆分为 10 项：系统界面、Editor、MiWallpaper、六个具体超级壁纸，以及一次重启全部目标。
- 六个超级壁纸改为按独立子包执行 `am force-stop`，覆盖对应的预览进程和实际渲染进程。

### v3.0.2-ci.5 · 内部测试

- 修复超级壁纸景深链错误替换原始 Bitmap 和 `subject_mask`，恢复系统原生抠图输入。
- 修复 HyperOS 4 填充式 Glass 数字（`fontStyle=22`、`hollowStyle=0`）被识别为普通数字，并在 SystemUI 接收前后恢复 Glass effect 5。
- 普通动态壁纸景深路径保持系统原结果，不再注册或发布超级壁纸专用深度帧；接口抽象方法也不再尝试 Hook。
- 本轮未伪造超级壁纸深度帧；日志未证明真实渲染深度帧已可用，真实 Super wallpaper 深度生产链仍需设备端接口确认。

### v3.0.2-ci.6 · 内部测试

- 根据设备日志修复 `mDepthVideoReady=true` 但 `isDepthVideoEnable=false` 的判定，启用超级壁纸已有的原生 `subject_mask` 景深路径。
- 继续保持原始 Bitmap 和 `subject_mask` URI，不使用伪造渲染帧替代抠图输入，并增加实际参数诊断日志。

### v3.0.1 · 未发布

- 修复首页状态卡在启用 Monet 时仍固定使用红黄绿的问题，统一跟随动态强调色主色系；关闭 Monet 时保留原有语义色。
- 修复强制使用玻璃时钟确认弹窗动画，改为从底部滑入、回弹和自然淡入淡出。
- 参考 KernelSU 的分页弹簧和滑条交互修复预测性返回进度，新增最大预测返回进度滑条（20%–50%，默认 35%）。
- 修复最小可见距离手动输入框在 Monet 主题下被染成绿色的问题，输入区域固定为白色。
- 开发版本号更新为 `3.0.1`，版本号 `20260902`。
- 移除超级壁纸编辑器和时钟适配 Hook，改为新增可选的超级壁纸景深功能；该功能在 SystemUI 中复用动态视频壁纸的景深安全区算法，并保留普通动态壁纸和静态壁纸的系统原行为。

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

### v3.0.2-ci.94 · Internal testing
- Replay the current wallpaper session's stationary final mask when SystemUI starts late or restarts. A separate bootstrap protocol preserves capture timestamps and only permits the new receiver's first input to bypass live-frame age rejection. The in-memory provider rendezvous is direct-boot aware and does not open locked appearance preferences.
- Adopt one mask snapshot for all clock targets in each window traversal before committing the matching wallpaper transaction. Expired pairs cannot update an old mask or present obsolete buffers. Pairing remains disabled by default; cold-boot and full original-frame checks remain separate acceptance gates.

### v3.0.2-ci.93 · Internal testing
- Observe the wallpaper Engine's actual SurfaceHolder accessor and negotiate paired-frame support before sending BLAST transactions. The experiment remains off by default pending device presentation and original-frame contour checks.

### v3.0.2-ci.92 · Internal testing
- Experimentally pair each BLAST wallpaper buffer with its matching clock mask in one draw transaction. This targets temporal contour gaps that remain after faster asynchronous readback; device testing is required before enabling the path by default.

### v3.0.2-ci.91 · Internal test
- Copy the Framework EGL creation list only after exact ELF, driver/PlatformEGL vtable and current-context identity checks. The device requested ES2/no-error while GL reports 3.2/flags=0, so CI90 could not reconstruct the real sharing parameters from GL queries.

### v3.0.2-ci.90 · Internal test
- Match the host GLES version and actual no-error/debug/robust flags and reset strategy, following the device Framework EGL creation path. CI89 shared context creation was rejected with EGL_BAD_MATCH; device validation continues in this build.

### v3.0.2-ci.89 · Internal test
- Support shared contexts for Filament hosts with no EGLConfig. EGL_CONFIG_ID=0 is not a selectable config; CI88 produced no device samples and is not valid acceptance evidence.

### v3.0.2-ci.88 · Internal test
- Move GPU fence waiting and PBO mapping into a dedicated worker with a shared EGL context, allowing same-frame readback without waiting for the next renderer callback. The renderer never waits for the worker and permits only one in-flight capture.
- CI87 original frames still showed contour misalignment during motion. This build requires new frame-age, performance and original-frame checks; complete resolution is not claimed.

### v3.0.2-ci.87 · Internal test
- Move the verified sky/mountain depth-cluster split to GPU and classify original display-resolution depth. Pack four alpha pixels into each RGBA readback texel to avoid enlarged low-resolution contours and per-pixel CPU logarithms.
- Keep the native worker attached to JNI, copy alpha directly into shared memory, and adopt newly decoded clock masks before drawing. Original-frame device validation remains required; elimination of every single-frame gap is not yet claimed.

### v3.0.2-ci.86 · Internal test
- Prevent a manually copied stale native library from shadowing Gradle's current native output. Verify matching ELF build IDs between the compiled library and APK in CI and release jobs.
- Device checks found CI85 still loading the old 512-edge mask. CI85 therefore does not validate cadence or contour improvements; repeat those measurements with this build. Single-frame depth gaps are not yet declared fully resolved.

### v3.0.2-ci.85 · Internal test
- Increase the clock-mask long edge from 512 to 1024 to reduce enlarged contour pixels. Decouple CPU publishing from GPU capture and retain only the latest pending frame.
- Use a phase-preserving 120Hz cap so minor frame jitter does not halve capture cadence. Add cadence, resolution, and transport-boundary regressions. Complete AOD transitions still require frame-by-frame device validation for edge errors and brief gaps.

### v3.0.2-ci.84 · Internal test
- Keep MIUIX backdrop sampling and render the top bar with multiple full-resolution native Gaussian levels. Mask background planes independently so titles and actions stay sharp; use solid bars on platforms without runtime shaders.
- Validate block artifacts, gradient transitions, and scrolling frame times on the device. Depth edges have not passed frame-by-frame acceptance.

### v3.0.2-ci.83 · Internal test
- Use an edit icon for the home screen's AOD/lock-screen editor entry, a layers icon for MiWallpaper, and a circular restart arrow for the top-right action.
- Validate the official MIUIX progressive blur implementation. Depth edges during AOD-to-lock-screen transitions remain under frame-by-frame investigation.

### v3.0.2-ci.82 · Internal test
- Repair the Snowmountain driver's cached depth-discard dispatch after process recreation, based on device disassembly and memory evidence; validate the exact ELF and driver identity and preserve foreign hooks.
- Add executable host regressions for idempotence, driver recreation, and foreign dispatch protection, plus read-only device diagnostics. Device regression is in progress.

### v3.0.2-ci.81 · Internal test
- Keep only entries on Settings and move all appearance controls into a secondary page; remove three feature-status hints from Home and distinguish SystemUI and AOD version icons.
- Use the default MIUIX progressive-blur pipeline without extra tint; preserve the full outline logo, modernize preset checkmarks, and reset the restart dialog to the top when opened.
- Expand the edge safety distance from 320px to 2000px while preserving the 196px default and fine adjustment.

### v3.0.2-ci.80 · Internal test
- Hide navigation during restart dialogs, refine presets and the action icon, and move inactive super-wallpaper versions into More.
- Promote appearance to the third Settings tab, rename module settings to Configuration, align module information, and match KernelSU's flowing name and outline logo.
- Verify all selected packages and child processes in one ROOT session, restart SystemUI last, and distinguish inactive targets from failures without force-stopping wallpapers.
- Renew mask endpoints after provider recreation, recover GL capture after transient errors, and prevent decoder starvation while retaining the single-wallpaper mask pipeline. Device regression is in progress.

### v3.0.2-beta.2 · Prerelease
- Keep the HyperOS 4 depth, foreground occlusion, glass-clock, and AOD-transition adaptation from beta.1, and fix the GitHub Actions NDK release-build configuration.
- Refresh the MIUIX theme and main-screen layout, streamline restart actions, add page scaling, navigation gestures, a landscape rail, and live status wiring; preserve the flip animation and quarter-step adjustment.

### v3.0.2-beta.1 · Prerelease
- Complete HyperOS 4 Super-wallpaper depth, foreground occlusion, and glass-clock adaptation; improve frame smoothness and mask continuity during the lockscreen-to-AOD transition.
- Add individual and all-target restart actions for nine static-scope targets, plus device diagnostics export and safety protections.

### v3.0.2-ci.77 · Internal test
- Reconfirm the active Super wallpaper and retain the last valid clock mask before the verified OS4 lockscreen-to-AOD linkage starts; lifecycle generations prevent a delayed clear from erasing a newer frame. Repeated device transition and unlock verification is pending.

### v3.0.2-ci.73 · Internal test
- Add a foreground-parent visibility check after a successful Surface post, following HyperOS 4's verified DEX call order. Preserve the existing current-frame sequence policy and log restoration results and blocked state gates. Device verification is pending.

### v3.0.2-ci.71 · Internal test
- Reapply the verified depth foreground after HyperOS 4's stock panel visibility update, fixing the case where the parent foreground layer was hidden after its child TextureView had been restored.

### v3.0.2-ci.70 · Internal test
- Gate the verified foreground layer on the device's actual interactive state, retaining lockscreen occlusion while leaving Dozing/AOD to the stock visibility path.

### v3.0.2-ci.69 · Internal test
- Reapply the verified foreground texture after stock visibility updates, only while the paired depth/foreground frame and visible Super wallpaper lockscreen are active.

### v3.0.2-ci.58 · Internal test
- Removed loader GOT writes after CI57's SnowMountain plugin-loader crash and changed the probe to read only the verified `dlopen`, `dlsym`, and `eglGetProcAddress` slots.

### v3.0.2-ci.57 · Internal test
- Replaced `libFramework.so` `dlopen`, `dlsym`, and `eglGetProcAddress` GOT entries at the verified relocations to trace the loader; this experiment caused a SnowMountain `_DoLoadPlugin` null-pointer crash and was removed in CI58.

### v3.0.2-ci.56 · Internal test
- Tried ByteHook probes for Vulkan `vkGetInstanceProcAddr` / `vkGetDeviceProcAddr`; device status 10 with no original function confirmed that the PLT hooks were not installed, so CI57 continued from the verified device GOT evidence.

### v3.0.2-ci.55 · Internal test

- Added a read-only Vulkan `dlsym` trace before SnowMountain renderer initialization to record the device's actual Vulkan API lookups and function addresses for the next depth-attachment capture step. Original arguments, return values, and renderer output are unchanged.

### v3.0.2-ci.3 · Internal test

- Added separate restart actions for System UI, AOD/lock-screen editor, and MiWallpaper, plus a restart-all action; static targets are module-managed without an LSPosed recommendation list.

### v3.0.2-ci.4 · Internal test

- Split the Home restart controls into 10 explicit actions: System UI, Editor, MiWallpaper, six concrete Super wallpapers, and one action that restarts every target.
- Restarting a concrete Super wallpaper now uses `am force-stop` on its own package so its preview and render processes are refreshed together.

### v3.0.2-ci.5 · Internal test

- Fixed the Super-wallpaper depth chain from replacing the original Bitmap and `subject_mask`, restoring the stock segmentation input.
- Fixed filled HyperOS 4 Glass digits (`fontStyle=22`, `hollowStyle=0`) being classified as ordinary digits, and restore Glass effect 5 before and after the SystemUI receiver call.
- Ordinary dynamic wallpapers now keep the stock depth result and never register or publish through the Super-wallpaper depth bridge; abstract interface methods are skipped during hook discovery.
- This build does not fabricate a Super-wallpaper depth frame. The logs do not prove that a real renderer depth frame is available yet, so the real Super-wallpaper depth producer still needs device-side interface confirmation.

### v3.0.2-ci.6 · Internal test

- Fixed the device-reported `mDepthVideoReady=true` but `isDepthVideoEnable=false` state, enabling HyperOS's native Super-wallpaper `subject_mask` depth path.
- Continue passing through the original Bitmap and `subject_mask` URI, never substituting a fabricated render frame, and add diagnostics for the actual input arguments.

### v3.0.1 · Unreleased

- Fixed the home status card using hard-coded red, yellow, and green colors when Monet is enabled; it now uses dynamic theme container roles while preserving semantic colors when Monet is disabled.
- Reworked the forced Glass clock confirmation dialog with a bottom slide, spring overshoot, and natural fade transitions.
- Followed KernelSU's pager spring implementation to correct predictive-back progress and added a configurable maximum progress setting (20%–50%, 35% by default).
- Fixed the minimum-visible-distance editor being tinted green under Monet; its input area is now explicitly white.
- Bumped the development version to `3.0.1` with version code `20260902`.
- Removed the Super wallpaper editor and clock-adaptation hooks. Added an optional System UI path that reuses the dynamic-video wallpaper depth safe-area algorithm for Super wallpapers while preserving stock behavior for ordinary video and static wallpapers.

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
