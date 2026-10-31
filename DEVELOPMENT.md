# 开发与发布

## 分支与提交

- 在 `main` 开发。开始前检查 `git status`，保留已有改动，再执行 `git pull --ff-only origin main`。
- 不创建功能分支或其他分支的 worktree。历史分叉时保留双方提交，不强推或重置远程历史。
- 每个提交只包含一个相关改动，使用 Conventional Commit 前缀。用户要求只提交时不推送；其他情况按任务要求推送。
- 密钥、设备日志、反编译材料、APK 和本地构建缓存不得提交。

## 本地验证

按改动选择检查，不要求每个提交都重建签名 APK。

| 改动 | 提交前检查 |
| --- | --- |
| README、发布说明、开发文档 | 本地链接、内容和 `git diff --check` |
| Python 工具、版本或 CI 策略 | Python 单元测试与相关命令 |
| Kotlin、资源、Android 清单 | 相关 Kotlin 单元测试；涉及资源、依赖或打包时补充对应构建和 lint |
| Native 代码 | native 策略测试、对应 Gradle 构建及 `verify-native.py` |
| 签名、版本生成、发布打包 | 完整发布门禁 |

```powershell
python -B -m unittest discover -s tools/tests -p 'test_*.py'
python tools/verify-version.py
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

CI 始终检查版本和 Python 工具。纯文档改动不启动 Android 工具链；Android 改动运行单元测试、Debug lint 和 Debug 构建；native 改动增加宿主策略测试。标签只触发发布工作流，不再额外触发日常 CI。

代码检查与设备验收分开记录。编译成功不能证明景深恢复、开机恢复或单帧遮挡通过。安装、进程重启和整机重启按用户授权执行；未经明确要求不整机重启。

## 工具链

JDK 21、Android SDK 37、NDK `27.1.12297006`，通过仓库 Gradle Wrapper 构建。SDK 在本机 `local.properties` 或 Android 环境变量中配置，不把个人路径写进共享文档。首次构建需要联网；依赖完整时才使用 `--offline`。

APK 使用 Gradle `externalNativeBuild` 的产物。`tools/verify-native.py` 比较 APK 内与当前编译库的 ELF build ID；不手动复制 `.so` 到 `jniLibs`。`tools/build-native-depth.ps1` 仅用于独立 native 诊断，不是每次提交的前置步骤。

## 版本

唯一版本源是根目录 `version.properties`：

```properties
versionName=3.1.1-rc.1
versionCode=20261096
releaseChannel=prerelease
```

- Gradle 从该文件设置 Android 版本，并生成 `BuildConfig` 和 APK 内的 Xposed `module.prop`。模板位于 `app/src/main/xposed/module.prop`，不手工维护第二份版本号。
- 修复递增 PATCH，兼容功能递增 MINOR，不兼容变更递增 MAJOR。预发布使用 `-rc.N` 或 `-beta.N`，正式版不带后缀。
- `releaseChannel` 为 `prerelease` 或 `stable`，必须与版本后缀一致。发布工作流使用校验后的通道，不把预发布设为 Latest。
- `versionCode` 是 Android 安装序号，每个分发构建递增，不按日期、月份或通道重置。不能复用已分发的序号，且不得超过 `2100000000`。
- 普通源码提交不必生成新版本。准备分发时才更新版本。

## 发布

发布需要用户明确要求。更新版本源、中英文更新日志和 `releases/v<版本>.md`。README 只更新用户需要的版本链接与兼容信息；不写测试门禁、仓库流程、构建路径或 UI 改动清单。

先运行本次改动所需的本地验证。完整发布门禁由标签工作流执行：

```powershell
python -B -m unittest discover -s tools/tests -p 'test_*.py'
python tools/verify-version.py --tag v3.1.1-rc.1 --previous-ref v3.1.0
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease --no-daemon --console=plain
python tools/verify-native.py --variant release
python tools/verify-release-apk.py
```

版本生成、签名和打包发生变化时，本地也执行这组完整门禁。其他改动无需在本地与 CI 重复全部检查。实验导出是可选操作，`tools/export-experiment.ps1` 只复制标准 APK，不移动或删除原文件。

发布基准来自 GitHub 的已公开发布记录：排除草稿、当前标签和不属于当前提交历史的标签，再按语义版本选择前一个发布。版本检查兼容历史标签中原有的 Gradle 版本定义，不按标签字符串排序猜测基准。

工作区干净且检查通过后，在 `main` 提交并推送，再创建带注释标签：

```powershell
git push origin main
git tag -a v3.1.1-rc.1 -m "Pre-release v3.1.1-rc.1"
git push origin v3.1.1-rc.1
```

工作流检查标签属于 `main`，核对发布基准和版本，运行单元测试、lint、Release 构建及 APK 校验，再发布版本命名的 APK 和 `SHA256SUMS.txt`。工作流成功、发布类型正确且公开附件校验通过才算发布完成。失败时修正原因并重跑；不移动已公开标签或替换既有附件。

## 签名

- Debug 使用 `.signing/mifreeform-debug.jks`，CI 从 `DEBUG_KEYSTORE_BASE64` 注入。不要用 Debug 包覆盖正式签名包。
- Release 固定使用 `.signing/mifreeform-release-legacy.jks`，CI 从 `LEGACY_RELEASE_KEYSTORE_BASE64` 注入；缺失时失败，不回退到 Debug 密钥。
- `tools/release-certificate.sha256` 保存公开证书指纹。密钥、密码和 Secret 不得出现在提交、日志或 Issue 中。
- 公开 v3.0.0 与 v3.0.2-beta.2/CI.94/3.1.x 的签名不同，不能承诺跨渠道覆盖升级。现用 Release 身份保留不变；生产密钥迁移须另定方案并保留历史附件。

## 文档与代码

共享规则写入已提交的 `AGENTS.md` 和本文件；`CLAUDE.md` 仅保留入口。`.agentdocs/` 保存具体任务和设备验收记录，不作为另一套通用发布规范。

文档、注释和提交信息直接说明行为、原因及限制。不写夸张评价、工作汇报式段落或无依据的兼容承诺。README 只向普通用户介绍三项核心功能：自由窗口边界保护、锁屏玻璃时钟、超级壁纸景深。
