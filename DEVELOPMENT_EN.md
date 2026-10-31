# Development and release

## Branches and commits

Work on `main`. Check the worktree, preserve existing changes, and sync with `git pull --ff-only origin main`. Do not create feature branches or worktrees on other branches. Preserve divergent history; do not force-push or reset it away.

Keep each commit limited to one related change and use Conventional Commit prefixes. A request to commit only does not authorize pushing. Never commit keys, device logs, reverse-engineering files, APKs, or local build caches.

## Local checks

Choose checks based on the change:

| Change | Before committing |
| --- | --- |
| User and developer documentation | Check links, content, and `git diff --check` |
| Python tools, version or CI policy | Python tests and affected commands |
| Kotlin, Android resources or manifest | Relevant Kotlin tests; add builds and lint when resources, dependencies or packaging change |
| Native code | Host policy tests, the affected Gradle build, and `verify-native.py` |
| Signing, version generation or release packaging | Full release checks |

```powershell
python -B -m unittest discover -s tools/tests -p 'test_*.py'
python tools/verify-version.py
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

CI always checks version metadata and Python tests. Documentation-only changes skip Android setup. Android changes run unit tests, Debug lint and a Debug build; native changes add host policy tests. Tags run the release workflow without also starting normal CI.

Record device acceptance separately from code checks. A successful build does not establish cold-boot recovery or frame-by-frame depth correctness. Device installation and restarts require task authorization; never reboot the whole device without an explicit request.

## Toolchain

Use JDK 21, Android SDK 37, NDK `27.1.12297006`, and the Gradle Wrapper. Configure SDK paths locally. Do not put personal paths into shared instructions. Use `--offline` only with a complete dependency cache.

APKs use Gradle `externalNativeBuild` outputs. `tools/verify-native.py` compares their ELF build IDs with the APK. Do not manually copy libraries into `jniLibs`. The standalone native script is diagnostic only, not a mandatory step for every commit.

## Version source

Root `version.properties` is the only version source:

```properties
versionName=3.1.1-rc.1
versionCode=20261096
releaseChannel=prerelease
```

Gradle sets Android version metadata and generates `BuildConfig` and the packaged Xposed `module.prop`. The Xposed template is `app/src/main/xposed/module.prop`; do not maintain another version copy.

Use PATCH for fixes, MINOR for compatible features, and MAJOR for incompatible changes. Prereleases use `-rc.N` or `-beta.N`; stable versions have no suffix. `releaseChannel` must be `prerelease` or `stable` and agree with the suffix. Prereleases must not become Latest.

Increment `versionCode` for every distributed build across all channels. It is an installation sequence, not a date. Never reuse it or exceed `2100000000`. Ordinary source commits do not require a version bump.

## Publishing

Publish only on an explicit user request. Update the version source, both changelog sections, and `releases/v<version>.md`. README updates are limited to user-facing version links and compatibility; omit build gates, repository procedures, local paths and UI inventories.

The tag workflow runs the full release gates:

```powershell
python -B -m unittest discover -s tools/tests -p 'test_*.py'
python tools/verify-version.py --tag v3.1.1-rc.1 --previous-ref v3.1.0
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease --no-daemon --console=plain
python tools/verify-native.py --variant release
python tools/verify-release-apk.py
```

Run the full set locally when changing version generation, signing or packaging. Other changes need only relevant local checks before CI. Experiment export is optional and copies, rather than moves, the standard APK.

Select the baseline from published GitHub release records, excluding drafts, the current tag and tags outside the current commit ancestry. Sort eligible releases by Semantic Versioning. The checker supports historical Gradle-based versions.

After checks pass, commit a clean worktree on `main`, push it, and create an annotated tag:

```powershell
git push origin main
git tag -a v3.1.1-rc.1 -m "Pre-release v3.1.1-rc.1"
git push origin v3.1.1-rc.1
```

The workflow checks ancestry and version progression, runs tests, lint and the signed Release build, validates the APK, and publishes the versioned APK and `SHA256SUMS.txt`. Verify workflow success, release type and public assets before calling the release complete. Do not move published tags or replace existing assets.

## Signing

Debug uses `.signing/mifreeform-debug.jks` through `DEBUG_KEYSTORE_BASE64`; Release uses `.signing/mifreeform-release-legacy.jks` through `LEGACY_RELEASE_KEYSTORE_BASE64`. Missing Release credentials must fail, never fall back to Debug. Keep all keys, passwords and Secrets out of commits and logs.

The public certificate fingerprint is in `tools/release-certificate.sha256`. Public v3.0.0 uses a different identity from v3.0.2-beta.2, CI.94 and 3.1.x. Do not promise cross-channel in-place upgrades. Keep the current Release identity; any production-key migration needs a separate plan.

## Documentation and code

Keep shared rules in tracked `AGENTS.md` and `DEVELOPMENT.md`. `CLAUDE.md` is an entry point. Task-specific device records can remain in `.agentdocs/`.

Use concrete descriptions of behavior, reasons and limitations. Avoid promotional claims, process narration and unsupported compatibility promises. README introduces only freeform-window boundary protection, the glass clock and Super-wallpaper depth.
