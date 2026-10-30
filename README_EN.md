# MiFreeformUnbounded

[中文](README.md) · [English](README_EN.md)

A freeform-window boundary and lock-screen glass-clock module for HyperOS 3/4. It is built with Kotlin, Jetpack Compose, MIUIX, and Modern LibXposed API 102. It improves freeform-window movement and forces the lock-screen glass clock in scenes where the stock editor does not support it.

> Current stable release: **v3.1.0** (versionCode: **20261095**).
> [Download v3.1.0](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v3.1.0) · [Release notes and known issues](releases/v3.1.0.md) · [Read the changelog](CHANGELOG.md)

## Features

- Configurable minimum visible edge distance for HyperOS freeform windows.
- Targeted handling for horizontal dragging, stable offsets, animation targets, and final bounds.
- Modern LibXposed API 102 with static scope support.
- Class, method, parameter, and return-type validation before every hook; unmatched rules preserve stock behavior and produce diagnostic logs.
- Hot-reload support and rate-limited diagnostics for different HyperOS builds.
- Three main Compose/MIUIX pages: Home, Configuration, and Settings. Appearance controls, module information, and About have dedicated secondary pages.
- Home shows the current Super-wallpaper version and places the others in More. The restart dialog combines nine scopes, verifies each process exit, handles SystemUI last, and reports inactive targets separately.
- Freeform boundary and forced glass-clock switches, an 8–2000px edge-distance slider, fine adjustment, and reset-to-default action; Appearance contains light/dark themes, system Monet, palette styles, and color specifications.
- Floating navigation bar, liquid-glass effects, navigation status badges, and predictive back support.
- Preserves the glass clock and valid depth mask during wallpaper changes and the lockscreen-to-AOD transition.
- Uses actual renderer depth and a single-wallpaper mask for clock occlusion, with receiver renewal and verified Snowmountain dispatch restoration after process recreation; ordinary wallpapers keep stock behavior.
- Settings remain reachable while the module is inactive; the edge-adjustment card becomes disabled and shows “模块未激活” when tapped.
- Freeform boundary protection has a dedicated secondary settings page. Its master switch controls edge distance and fine adjustment; when disabled, all subordinate controls are dimmed and non-interactive.
- Settings and secondary-page feature icons follow the MIUIX theme primary color and use wallpaper-derived Monet colors when Monet is enabled.
- Enabling the forced glass clock shows a confirmation dialog describing the power and material limitations before saving the setting.

## Compatibility

| Item | Requirement |
| --- | --- |
| System | HyperOS 3/4 (HyperOS 4 target) |
| Minimum Android | Android 12 (API 31) |
| Hook framework | A manager supporting Modern LibXposed API 102 |
| Static scope | `com.android.systemui`, `com.miui.aod`, `com.miui.miwallpaper` |
| Build toolchain | JDK 21, Android SDK 37, NDK r27b, Gradle Wrapper |

HyperOS minor releases may rename classes or change method signatures. The module intentionally performs strict matching and skips incompatible rules instead of forcing an unsafe hook.

## Installation

1. Download the APK from the [v3.1.0 stable release](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v3.1.0) and read its compatibility notes and known issues.
2. Install it with a module manager that supports Modern LibXposed API 102.
3. The module uses a fixed static scope whose targets are declared by the module; no recommended-app list needs to be maintained in LSPosed.
4. Reboot the device, or use the Home actions to restart System UI, Editor, MiWallpaper, any of the six concrete Super wallpapers, or all static scopes.
5. Open the app and verify that the status card reports Working or Pending restart.

## Usage

### Edge distance

Open **Freeform boundary protection** from Configuration to reach the secondary page. Its **Minimum visible distance** slider controls the minimum number of pixels kept visible when a freeform window reaches a display edge. The default is `196px`; the supported range is `8px`–`2000px`. Fine adjustment reduces the effective step per drag for precise tuning. When the master switch is off, the edge-distance section is dimmed and cannot be used.

When the module is inactive, the Settings page remains available, but the edge-adjustment card is disabled. It becomes available after activation or when a restart is pending.

### Feature switches

- **Freeform boundary protection**: the secondary-page master switch; when disabled, SystemUI freeform hooks call the stock implementation without changes and the edge controls are disabled.
- **Force lock-screen glass clock**: enabling it requires confirmation of the power and material limitations; it forces the lock-screen glass clock in unsupported scenes, while disabling it restores the stock lock-screen editor restrictions.
- **Super wallpaper depth**: reuses the dynamic-video wallpaper safe area and avoidance rectangle for the current Super wallpaper only; restart System UI after changing it.
- Switches and edge distance are stored in shared module/target preferences; restart the corresponding process after changing them. The Settings reset action restores `196px` and disables all features.

### Theme and navigation

- Dark mode: follow system, light, or dark.
- Monet: use wallpaper-derived dynamic colors, with optional seed color, palette style, and color specification; feature icons follow the theme primary color.
- Floating navigation bar: standard, blurred, and liquid-glass variants.
- Predictive back: enabled on Android versions that support the callback.

## Build from source

Windows PowerShell:

```powershell
$env:JAVA_HOME = "D:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease --offline --console=plain
```

Output:

```text
app/build/outputs/apk/release/app-release.apk
```

Release retains the published v3.0.2-beta.2 and CI.94 signing identity, injected through `LEGACY_RELEASE_KEYSTORE_BASE64`. The public v3.0.0 APK has a different certificate and cannot be upgraded directly to this channel; the earlier compatibility claim is corrected. The current key remains a historical Debug keystore, so a future long-lived production key needs a separate migration plan. Check your installed channel and preserve data before switching; uninstalling blindly can lose configuration.

## Personal development flow

All development and releases happen on `main`. Pushing `main` runs tests, lint, and a CI Debug build; pushing a new annotated `vX.Y.Z` tag after verification triggers the Release APK and GitHub Release. A missing established signing key fails the Release build. Published assets include a versioned APK and SHA-256, with known issues in the release notes. See [Personal Development and Release Flow](DEVELOPMENT_EN.md).

## Project layout

```text
app/src/main/java/com/freeform/unbounded/
├─ FreeformHook.kt              # LibXposed entry point and hook installation
├─ HookProfiles.kt               # Signature-checked hook rules
├─ BoundsPolicy.kt               # Edge protection and resistance calculations
├─ ModuleStatusRepository.kt     # Module, scope, and runtime state
├─ ConfigRepository.kt           # Cross-process configuration storage
└─ ui/                           # Compose + MIUIX interface
```

## Troubleshooting

- **The home page says inactive**: verify all three static scopes and reboot the device or the corresponding target process.
- **The page says pending restart**: the module is installed, but the target process has not reloaded yet.
- **Dragging is unchanged**: check whether the current HyperOS build still exposes a known method signature and inspect Xposed logs.
- **The slider is disabled**: this is expected while the module is inactive; it is restored after activation or pending restart.
- **Problems after an upgrade**: re-enable the scopes, restart SystemUI, and attach the HyperOS version and logs to an issue.

## Related projects

- [KernelSU](https://github.com/tiann/KernelSU): reference for theme, navigation, and selected UI interactions.
- [MIUIX](https://github.com/compose-miuix-ui/miuix): Compose UI components and theme system.
- [Modern LibXposed API](https://github.com/libxposed/api): module API.

## License and contributions

Released under the [GNU Affero General Public License v3.0](LICENSE). Issues, compatibility reports, and pull requests are welcome. Please include the HyperOS version, Android version, LibXposed manager version, and relevant logs when reporting a problem.

## Releases and documentation

- [v3.0.0 changelog](CHANGELOG.md)
- [GitHub Releases](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases)
- [中文 README](README.md)

[← 返回中文 README](README.md)
