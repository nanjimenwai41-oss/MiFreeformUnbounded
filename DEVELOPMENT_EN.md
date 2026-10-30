# Personal Development and Release Flow

This is a personal Android/Modern LibXposed module. A conversation, debugging session, or feature change is not a release by itself. A version is created only when a reproducible build is ready for users to download.

## Daily development

1. Perform all work on `main`. Check the branch and worktree first, preserve existing changes, and sync with `git pull --ff-only origin main`. Do not create development branches or worktrees on other branches unless the user explicitly changes this rule. See `AGENTS.md` for repository instructions.
2. Commit each logically complete, independently revertible change. Use Conventional Commit prefixes such as `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`, and `chore:`; for example, `fix(aod): preserve glass clock effect`.
3. Push `main` to GitHub. Every push runs CI: version consistency, unit tests, lint, and a Debug build. The CI Debug APK is a test artifact, not a release.
4. Install the CI Debug APK on the phone and verify the behavior. Continue committing on `main` when feedback requires more changes. Keep logs in the local Downloads directory or another path ignored by `.gitignore`.
5. Preserve validation results and relevant decisions in commit records. If an existing old branch needs to be integrated, back it up and preserve all commits, then remove the branch only after confirming that `main` contains its complete history.

## Versioning and releases

Use Semantic Versioning: PATCH for compatible fixes, MINOR for backward-compatible features, and MAJOR for incompatible changes. The added features accumulated during the 3.0.2 prereleases are consolidated in `3.1.0`, rather than promoted as a PATCH. Future fixes can use `3.1.1-ci.1` and `3.1.1-rc.1` before `3.1.1`. Each test build is not a separate stable release.

`versionCode` is an independent Android installation sequence, strictly increasing across all channels. Continue adding one after `20261095`; never reset it on a new date, month, or channel. It is not a date and must not exceed `2100000000`. Keep all three sources consistent. The verifier's `--previous-ref` rejects version regressions and reused installation codes.

Keep these three sources in sync and run the verifier:

- `versionName` and `versionCode` in `app/build.gradle.kts`;
- constants in `app/src/main/java/com/freeform/unbounded/BuildConfig.kt`;
- `version` and `versionCode` in `app/src/main/resources/META-INF/xposed/module.prop`.

Before a release on `main`:

```powershell
python .\tools\verify-version.py
python -B -m unittest discover -s tools/tests -p 'test_*.py'
.\gradlew.bat :app\testDebugUnitTest :app\lintRelease :app\assembleRelease --no-daemon --console=plain
python .\tools\verify-native.py --variant release
python .\tools\verify-release-apk.py
powershell -ExecutionPolicy Bypass -File .\tools\export-experiment.ps1 -Label super-depth-glass
```

The standard Gradle APK remains at `app/build/outputs/apk/release/app-release.apk`. The experiment export script only copies it to `experiments/<timestamp>-<label>/` and writes a timestamped APK, SHA-256, and metadata manifest; it does not move or delete the standard artifact.

Update both language sections of `CHANGELOG.md`, the READMEs, and curated bilingual notes at `releases/vX.Y.Z.md`. List validation coverage and unresolved issues; incomplete cold-boot or single-frame acceptance cannot be reported as fixed. Prerelease tags create a GitHub Pre-release; suffix-free tags create a stable Release. Stable publication requires an explicit user request; `3.1.0` is authorized with its documented known issues.

After verification, a clean worktree, and synchronizing `main`, create and push a new annotated tag:

```powershell
python .\tools\verify-version.py --tag v3.1.0 --previous-ref v3.0.2-beta.2
git tag -a v3.1.0 -m "Release v3.1.0"
git push origin v3.1.0
```

GitHub Actions checks that the tag belongs to `main`, verifies version progression and notes, builds the tagged Release APK, and checks its package, version, non-debuggable flag, established certificate, and native library. It publishes a versioned APK and `SHA256SUMS.txt`. Completion requires a successful Release workflow and verified public assets, not just a pushed tag. Do not manually upload Debug APKs or replace published tags/assets. Diagnose failed stages without rewriting remote tags to conceal failures.

## Signing

Never commit a signing private key. Debug uses the dedicated `.signing/mifreeform-debug.jks`, injected in CI through `DEBUG_KEYSTORE_BASE64`. Release always uses `.signing/mifreeform-release-legacy.jks`, injected through `LEGACY_RELEASE_KEYSTORE_BASE64`, and fails if it is missing without switching to the Debug test identity. Never place keystores, passwords, or device logs in the repository, issues, or CI output.

Public APK verification found certificate SHA-256 `4d0113699db6a92c72862c7724d3c95aa997c169fa87b1144c6689a190556cfe` for v3.0.0, versus `a9154c4652e0077298a58f1f04fe64ea8d8cde328802bb181b712c773522af87` for v3.0.2-beta.2, CI.94, and the current Release key. Therefore v3.0.0 cannot be upgraded in place to this channel. This release retains the beta.2/CI.94 identity; do not replace old assets to conceal the historical mismatch.

The current Release key originates from a historical machine Debug keystore. Back it up offline; a future production-key migration requires a separate plan. The dedicated Debug test key must not sign Release builds. Only the public certificate fingerprint is tracked in `tools/release-certificate.sha256`; the private key must remain untracked.

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

# Push main and trigger CI
git push origin main

# Build only the Debug test package
.\gradlew.bat :app\assembleDebug --no-daemon --console=plain

# List available Gradle tasks
.\gradlew.bat tasks --all
```

Background: this repository follows the user's requirement to work exclusively on `main`, with continuous commits and CI validation; Android recommends the Gradle Wrapper for builds; a GitHub Release points to a fixed Git tag; Keep a Changelog is a curated release history rather than a raw Git log.
