# 个人开发与发布流程

本项目是个人维护的 Android/Modern LibXposed 模块。一次对话、调试过程或功能修改不等于一个版本；版本只在可复现构建并准备给用户下载时产生。

## 日常开发

1. 从 `main` 更新代码，为一组相关改动创建短分支，例如 `codex/ui-settings` 或 `codex/hook-aod`。很小的文档修正可以直接提交到 `main`。
2. 每完成一个逻辑完整、可独立回退的改动就提交一次。提交信息使用 `feat:`、`fix:`、`refactor:`、`test:`、`docs:`、`build:` 或 `chore:` 前缀，例如 `fix(aod): preserve glass clock effect`。
3. 把分支推送到 GitHub。每次推送都会运行 CI：版本一致性检查、单元测试、Lint 和 Debug 构建。CI 产生的 Debug APK 只是测试工件，不是正式版本。
4. 在手机上安装 CI Debug APK 验证功能；需要继续修复时，在同一个分支继续提交，CI 会自动重新构建。日志只能放在本地 Downloads 或被 `.gitignore` 忽略的目录中。
5. 验证完成后将分支合并到 `main`。个人项目不强制 Pull Request，但保留 Pull Request 可以留下测试结果和决策记录。

## 版本和发布

版本遵循 Semantic Versioning：修复使用 PATCH，兼容功能使用 MINOR，不兼容变更使用 MAJOR。版本号必须同时更新以下三个位置，并通过脚本校验：

- `app/build.gradle.kts` 的 `versionName` 和 `versionCode`；
- `app/src/main/java/com/freeform/unbounded/BuildConfig.kt`；
- `app/src/main/resources/META-INF/xposed/module.prop`。

发布前在 `main` 上完成：

```powershell
python .\tools\verify-version.py
.\gradlew.bat :app\testDebugUnitTest :app\lintRelease :app\assembleRelease --no-daemon --console=plain
```

更新中英文 `CHANGELOG.md`，确认工作区干净，然后创建并推送带注释的版本标签：

```powershell
git tag -a v3.1.0 -m "Release v3.1.0"
git push origin v3.1.0
```

推送 `vX.Y.Z` 标签后，GitHub Actions 会从该标签构建 Release APK，并创建同名 GitHub Release。不要手动上传 Debug APK，也不要修改已经发布的标签或 Release；需要修复时递增版本并创建新标签。

## 签名注意事项

签名私钥绝不提交到仓库。当前 Gradle 配置在本地存在 `.signing/mifreeform-debug.jks` 时，使用新建的独立 Debug 测试签名；CI 必须通过 `DEBUG_KEYSTORE_BASE64` Secret 使用同一把钥匙。Release 使用 `.signing/mifreeform-release-legacy.jks`，CI 必须通过 `LEGACY_RELEASE_KEYSTORE_BASE64` Secret 注入它，以保证正式版本升级链不变。不要把 keystore、密码或设备日志写进仓库、Issue 或 CI 输出。

本地 Debug 密钥只用于测试，别把它用于 Release。若更换 Debug 密钥，已安装的 Debug 包可能需要先卸载；同一测试渠道应长期保持同一把密钥。当前遗留 Release 密钥实际沿用 v3.0.0 使用的本机 Debug keystore；它应单独离线备份，未来迁移到新的正式密钥时必须单独发布迁移说明。

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

# 推送开发分支，触发 CI
git push -u origin codex/<topic>

# 只构建 Debug 测试包
.\gradlew.bat :app\assembleDebug --no-daemon --console=plain

# 查看可用 Gradle 任务
.\gradlew.bat tasks --all
```

更多背景：GitHub Flow 建议使用短分支、持续提交和合并；Android 官方建议使用 Gradle Wrapper 构建；GitHub Release 由 Git tag 指向固定源码点；Keep a Changelog 用于维护人工整理的版本变更，不能用完整 Git 日志代替。
