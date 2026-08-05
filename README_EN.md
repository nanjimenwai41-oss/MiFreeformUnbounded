# MiFreeformUnbounded

[中文](README.md) · [English](README_EN.md)

A freeform-window boundary module for HyperOS 3 / Android 16. It is built with Kotlin, Jetpack Compose, MIUIX, and Modern LibXposed API 102. Strictly validated hook rules improve edge protection and horizontal freeform-window movement without forcing incompatible methods into the system.

> Current stable release: **v2.0.0** (version code: **20260805**)  
> [Download the stable release](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v2.0.0) · [Read the changelog](CHANGELOG.md)

## Features

- Configurable minimum visible edge distance for HyperOS freeform windows.
- Targeted handling for horizontal dragging, stable offsets, animation targets, and final bounds.
- Modern LibXposed API 102 with static scope support.
- Class, method, parameter, and return-type validation before every hook; unmatched rules preserve stock behavior and produce diagnostic logs.
- Hot-reload support and rate-limited diagnostics for different HyperOS builds.
- A three-page Compose/MIUIX UI: Home, Settings, and About.
- Edge-distance slider, fine adjustment, light/dark themes, system Monet, palette styles, and color specifications.
- Floating navigation bar, liquid-glass effects, navigation status badges, and predictive back support.
- Settings remain reachable while the module is inactive; the edge-adjustment card becomes disabled and shows “模块未激活” when tapped.

## Compatibility

| Item | Requirement |
| --- | --- |
| System | HyperOS 3 / Android 16 (target environment) |
| Minimum Android | Android 12 (API 31) |
| Hook framework | A manager supporting Modern LibXposed API 102 |
| Static scope | `android`, `com.android.systemui` |
| Build toolchain | JDK 21, Android SDK 37, Gradle Wrapper |

HyperOS minor releases may rename classes or change method signatures. The module intentionally performs strict matching and skips incompatible rules instead of forcing an unsafe hook.

## Installation

1. Download the APK from the [v2.0.0 stable release](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v2.0.0).
2. Install it with a module manager that supports Modern LibXposed API 102.
3. Enable the static scopes `android` and `com.android.systemui`.
4. Reboot the device, or restart SystemUI when prompted by the app.
5. Open the app and verify that the status card reports Working or Pending restart.

## Usage

### Edge distance

The **Minimum visible distance** slider controls the minimum number of pixels kept visible when a freeform window reaches a display edge. The default is `196px`; the supported range is `8px`–`320px`. Fine adjustment reduces the effective step per drag for precise tuning.

When the module is inactive, the Settings page remains available, but the edge-adjustment card is disabled. It becomes available after activation or when a restart is pending.

### Theme and navigation

- Dark mode: follow system, light, or dark.
- Monet: use wallpaper-derived dynamic colors, with optional seed color, palette style, and color specification.
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

Before publishing, verify that the signing configuration, `versionName`, `versionCode`, and `module.prop` are consistent.

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

- **The home page says inactive**: verify both static scopes and reboot the device or SystemUI.
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

- [v2.0.0 changelog](CHANGELOG.md)
- [GitHub Releases](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases)
- [中文 README](README.md)

[← 返回中文 README](README.md)
