# 个人开发与发布流程

本项目是个人维护的 Android/Modern LibXposed 模块。一次对话、调试过程或功能修改不等于一个版本；版本只在可复现构建并准备给用户下载时产生。

## 日常开发

1. 所有操作统一在 `main` 上完成。开始前检查分支和工作区，保留已有改动后使用 `git pull --ff-only origin main` 同步代码；除非用户明确变更规则，不创建开发分支或其他分支的 worktree。仓库元指令见 `AGENTS.md`。
2. 每完成一个逻辑完整、可独立回退的改动就提交一次。提交信息使用 `feat:`、`fix:`、`refactor:`、`test:`、`docs:`、`build:` 或 `chore:` 前缀，例如 `fix(aod): preserve glass clock effect`。
3. 把 `main` 推送到 GitHub。每次推送都会运行 CI：版本一致性检查、单元测试、Lint 和 Debug 构建。CI 产生的 Debug APK 只是测试工件，不是正式版本。
4. 在手机上安装 CI Debug APK 验证功能；需要继续修复时，在 `main` 继续提交，CI 会自动重新构建。日志只能放在本地 Downloads 或被 `.gitignore` 忽略的目录中。
5. 在提交记录中保留验证结果和必要的决策说明。若需要合入已有旧分支，先备份并保留所有提交，确认已完整合入 `main` 后再清理分支。

## 版本和发布

版本遵循 Semantic Versioning：修复使用 PATCH，兼容功能使用 MINOR，不兼容变更使用 MAJOR。3.0.2 预发布阶段积累的新增功能以 `3.1.0` 汇总，不直接去掉后缀当作 PATCH。此后的纯修复可用 `3.1.1-ci.1`、`3.1.1-rc.1`，验收后发布 `3.1.1`；每次测试构建不等于一次正式发布。

`versionCode` 是独立、全渠道严格递增的 Android 安装序号，不再解释为日期。从 `20261095` 继续逐次加一；跨天、跨月和切换 CI/beta/正式渠道均不重置、不复用，且不得超过 `2100000000`。三处版本源仍须一致，检查器支持 `--previous-ref` 拒绝版本名回退与安装序号复用。

版本号必须同时更新以下三个位置，并通过脚本校验：

- `app/build.gradle.kts` 的 `versionName` 和 `versionCode`；
- `app/src/main/java/com/freeform/unbounded/BuildConfig.kt`；
- `app/src/main/resources/META-INF/xposed/module.prop`。

发布前在 `main` 上完成：

```powershell
python .\tools\verify-version.py
python -B -m unittest discover -s tools/tests -p 'test_*.py'
.\gradlew.bat :app\testDebugUnitTest :app\lintRelease :app\assembleRelease --no-daemon --console=plain
python .\tools\verify-native.py --variant release
python .\tools\verify-release-apk.py
powershell -ExecutionPolicy Bypass -File .\tools\export-experiment.ps1 -Label super-depth-glass
```

Gradle 的标准产物仍保留在 `app/build/outputs/apk/release/app-release.apk`。实验导出脚本只复制该文件，在 `experiments/<时间戳>-<标签>/` 生成带时间戳的 APK、SHA-256 和版本清单，不移动或删除标准产物。

更新中英文 `CHANGELOG.md` 和 README，并在 `releases/vX.Y.Z.md` 编写中英文发布说明，列出新增功能、验证范围与未解决问题。预发布标签生成 GitHub Pre-release，不带后缀的标签生成正式 Release。完整冷启动、单帧等未通过项不得写成已修复。正式发布需要用户明确要求；本次 `3.1.0` 已获授权，已知问题见发布说明。

确认工作区干净、`main` 已同步推送、检查已通过，再创建并推送新的带注释标签：

```powershell
python .\tools\verify-version.py --tag v3.1.0 --previous-ref v3.0.2-beta.2
git tag -a v3.1.0 -m "Release v3.1.0"
git push origin v3.1.0
```

GitHub Actions 检查标签提交属于 `main`，核对版本递增和发布说明，从该标签构建 Release APK，校验实际包名、版本、非 debuggable 状态、既有签名指纹和 native 库，再发布版本命名的 APK 与 `SHA256SUMS.txt`。以 Release 工作流成功、公开附件及其校验通过为完成，不能把 tag 推送成功称为发布完成。不要手动上传 Debug APK，也不要修改已发布标签或附件；失败时先查明阶段，不重写远程标签掩盖问题。

## 签名注意事项

签名私钥绝不提交到仓库。当前 Gradle 配置在本地存在 `.signing/mifreeform-debug.jks` 时，使用独立 Debug 测试签名；CI 通过 `DEBUG_KEYSTORE_BASE64` 注入它。Release 固定使用 `.signing/mifreeform-release-legacy.jks`，CI 通过 `LEGACY_RELEASE_KEYSTORE_BASE64` 注入，缺失时失败，禁止回退到 Debug 测试签名。不要把 keystore、密码或设备日志写进仓库、Issue 或 CI 输出。

公开 APK 审计确认：v3.0.0 的证书 SHA-256 为 `4d0113699db6a92c72862c7724d3c95aa997c169fa87b1144c6689a190556cfe`；v3.0.2-beta.2、CI.94 与现用 Release 密钥为 `a9154c4652e0077298a58f1f04fe64ea8d8cde328802bb181b712c773522af87`。两者不同，因此不能承诺从公开 v3.0.0 覆盖升级。本次延续 beta.2/CI.94 身份，未再次换钥；不要替换旧附件掩盖历史差异。

现用 Release 密钥仍来源于历史本机 Debug keystore，应离线备份，未来迁移长期生产密钥需要单独方案。当前独立 Debug 测试密钥不能用于 Release。公开证书指纹保存在 `tools/release-certificate.sha256`，仅指纹可提交，私钥仍禁止提交。

首次配置 GitHub Actions Secret 时，在 PowerShell 中将对应文件转为 Base64 后粘贴到仓库的 Settings -> Secrets and variables -> Actions：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('.signing\mifreeform-debug.jks')) | Set-Clipboard
[Convert]::ToBase64String([IO.File]::ReadAllBytes('.signing\mifreeform-release-legacy.jks')) | Set-Clipboard
```

依次保存为 `DEBUG_KEYSTORE_BASE64` 和 `LEGACY_RELEASE_KEYSTORE_BASE64`；命令只复制到剪贴板，不会写入 Git。

## 常用命令

```powershell
# 查看本地状态
git status

# 推送 main，触发 CI
git push origin main

# 只构建 Debug 测试包
.\gradlew.bat :app\assembleDebug --no-daemon --console=plain

# 查看可用 Gradle 任务
.\gradlew.bat tasks --all
```

更多背景：本仓库按用户要求统一在 `main` 开发，持续提交并通过 CI 验证；Android 官方建议使用 Gradle Wrapper 构建；GitHub Release 由 Git tag 指向固定源码点；Keep a Changelog 用于维护人工整理的版本变更，不能用完整 Git 日志代替。
