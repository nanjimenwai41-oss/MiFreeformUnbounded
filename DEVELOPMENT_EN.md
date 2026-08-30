# Personal Development and Release Flow

This is a personal Android/Modern LibXposed module. A conversation, debugging session, or feature change is not a release by itself. A version is created only when a reproducible build is ready for users to download.

## Daily development

1. Update `main` and create a short-lived branch for one related change, such as `codex/ui-settings` or `codex/hook-aod`. A tiny documentation-only fix may be committed directly to `main`.
2. Commit each logically complete, independently revertible change. Use Conventional Commit prefixes such as `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`, and `chore:`; for example, `fix(aod): preserve glass clock effect`.
3. Push the branch to GitHub. Every push runs CI: version consistency, unit tests, lint, and a Debug build. The CI Debug APK is a test artifact, not a release.
4. Install the CI Debug APK on the phone and verify the behavior. Continue pushing commits to the same branch when feedback requires more changes. Keep logs in the local Downloads directory or another path ignored by `.gitignore`.
5. Merge the branch into `main` after verification. Pull requests are optional for a personal project, but they preserve test results and decisions when useful.

## Versioning and releases

Use Semantic Versioning: PATCH for compatible fixes, MINOR for backward-compatible features, and MAJOR for incompatible changes. Keep these three sources in sync and run the verifier:

- `versionName` and `versionCode` in `app/build.gradle.kts`;
- constants in `app/src/main/java/com/freeform/unbounded/BuildConfig.kt`;
- `version` and `versionCode` in `app/src/main/resources/META-INF/xposed/module.prop`.

Before a release on `main`:

```powershell
python .\tools\verify-version.py
.\gradlew.bat :app\testDebugUnitTest :app\lintRelease :app\assembleRelease --no-daemon --console=plain
```

Update both language sections of `CHANGELOG.md`, confirm a clean worktree, then create and push an annotated version tag:

```powershell
git tag -a v3.1.0 -m "Release v3.1.0"
git push origin v3.1.0
```

Pushing a `vX.Y.Z` tag makes GitHub Actions build the Release APK from that exact tag and create the matching GitHub Release. Do not upload a Debug APK manually, and do not mutate an already published tag or Release; increment the version and publish a new tag for fixes.

## Signing

Never commit a signing private key. When `.signing/mifreeform-debug.jks` exists locally, Gradle uses the new dedicated Debug/test certificate; CI requires the `DEBUG_KEYSTORE_BASE64` Secret so every test build uses the same key. Release uses `.signing/mifreeform-release-legacy.jks`; CI requires the `LEGACY_RELEASE_KEYSTORE_BASE64` Secret so the stable upgrade identity remains unchanged. Never use the Debug key for Release, and never place keystores, passwords, or device logs in the repository, issues, or CI output.

The local Debug key is test-only. Replacing it may require uninstalling an already installed Debug package; keep one key stable for one test channel. The legacy Release key currently reuses the machine Debug keystore used by v3.0.0; keep it backed up offline. A future migration to a new production key requires a separate migration note and release plan.

To configure the GitHub Actions Secrets for the first time, encode the files in PowerShell and paste each result into the repository's Settings -> Secrets and variables -> Actions:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('.signing\mifreeform-debug.jks')) | Set-Clipboard
[Convert]::ToBase64String([IO.File]::ReadAllBytes('.signing\mifreeform-release-legacy.jks')) | Set-Clipboard
```

Save them as `DEBUG_KEYSTORE_BASE64` and `LEGACY_RELEASE_KEYSTORE_BASE64` respectively. These commands only copy the encoded key to the clipboard and do not write it to Git.

## Common commands

```powershell
# Inspect local state
git status

# Push a development branch and trigger CI
git push -u origin codex/<topic>

# Build only the Debug test package
.\gradlew.bat :app\assembleDebug --no-daemon --console=plain

# List available Gradle tasks
.\gradlew.bat tasks --all
```

Background: GitHub Flow uses short-lived branches, continuous commits, and merges; Android recommends the Gradle Wrapper for builds; a GitHub Release points to a fixed Git tag; Keep a Changelog is a curated release history rather than a raw Git log.
