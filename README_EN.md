# MiFreeformUnbounded

[中文](README.md) · [English](README_EN.md)

A freeform-window boundary and lock-screen glass-clock module for HyperOS 3/4. It is built with Kotlin, Jetpack Compose, MIUIX, and Modern LibXposed API 102. It improves freeform-window movement and forces the lock-screen glass clock in scenes where the stock editor does not support it.

> Current stable version: **v3.0.0** (version code: **20260901**)
> [Download v3.0.0 Release](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v3.0.0) · [Read the changelog](CHANGELOG.md)

## Features

- Configurable minimum visible edge distance for HyperOS freeform windows.
- Targeted handling for horizontal dragging, stable offsets, animation targets, and final bounds.
- Modern LibXposed API 102 with static scope support.
- Class, method, parameter, and return-type validation before every hook; unmatched rules preserve stock behavior and produce diagnostic logs.
- Hot-reload support and rate-limited diagnostics for different HyperOS builds.
- A three-page Compose/MIUIX UI: Home, Settings, and About, with separate System UI and lock-screen editor restart actions.
- Freeform boundary and forced glass-clock switches, edge-distance slider, fine adjustment, reset-to-default action, light/dark themes, system Monet, palette styles, and color specifications.
- Floating navigation bar, liquid-glass effects, navigation status badges, and predictive back support.
- A scoped lock-screen editor hook that forces the all-in-one clock's glass-clock effect in dynamic and Super wallpaper scenes while preserving stock behavior elsewhere.
- Preserves the glass-clock effect during wallpaper switching and prevents dynamic-wallpaper filter cleanup from reverting it.
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
| Static scope | `com.android.systemui`, `com.miui.aod` |
| Build toolchain | JDK 21, Android SDK 37, Gradle Wrapper |

HyperOS minor releases may rename classes or change method signatures. The module intentionally performs strict matching and skips incompatible rules instead of forcing an unsafe hook.

## Installation

1. Download the APK from the [v3.0.0 stable release](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v3.0.0).
2. Install it with a module manager that supports Modern LibXposed API 102.
3. Enable the static scopes `com.android.systemui` and `com.miui.aod`.
4. Reboot the device, or use the Home actions to restart System UI or the lock-screen editor as needed.
5. Open the app and verify that the status card reports Working or Pending restart.

## Usage

### Edge distance

Open **Freeform boundary protection** from Settings to reach the secondary page. Its **Minimum visible distance** slider controls the minimum number of pixels kept visible when a freeform window reaches a display edge. The default is `196px`; the supported range is `8px`–`320px`. Fine adjustment reduces the effective step per drag for precise tuning. When the master switch is off, the edge-distance section is dimmed and cannot be used.

When the module is inactive, the Settings page remains available, but the edge-adjustment card is disabled. It becomes available after activation or when a restart is pending.

### Feature switches

- **Freeform boundary protection**: the secondary-page master switch; when disabled, SystemUI freeform hooks call the stock implementation without changes and the edge controls are disabled.
- **Force lock-screen glass clock**: enabling it requires confirmation of the power and material limitations; it forces the lock-screen glass clock in unsupported scenes, while disabling it restores the stock lock-screen editor restrictions.
- Switches and edge distance are stored in shared module/target preferences; restart the corresponding process after changing them. The Settings reset action restores `196px` and disables both features.

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

The repository's Release build preserves the legacy signing identity used by v3.0.0 for upgrade compatibility; GitHub Actions injects it through the `LEGACY_RELEASE_KEYSTORE_BASE64` Secret. It is still a historical Debug keystore rather than an ideal public-release key, so migrating to a new long-lived Release keystore requires a separate upgrade plan. Verify that the signing configuration, `versionName`, `versionCode`, and `module.prop` are consistent.

## Personal development flow

Daily changes, CI Debug artifacts, and stable versions are separate things: pushing a development branch runs tests, lint, and a Debug APK build; only a verified `main` branch followed by a `vX.Y.Z` tag triggers the Release APK build and GitHub Release. See [Personal Development and Release Flow](DEVELOPMENT_EN.md) for the exact steps.

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

- **The home page says inactive**: verify both static scopes and reboot the device or the corresponding target process.
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
