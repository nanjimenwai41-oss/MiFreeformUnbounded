# MiFreeformUnbounded

[中文](README.md) · [English](README_EN.md)

A freeform-window, lock-screen glass-clock, and Super-wallpaper depth module for HyperOS 3/4.

[Stable v3.1.0](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v3.1.0) · [Prerelease v3.1.1-rc.1](https://github.com/nanjimenwai41-oss/MiFreeformUnbounded/releases/tag/v3.1.1-rc.1) · [Changelog](CHANGELOG.md)

## Features

1. **Freeform-window boundary protection**: set the minimum window area kept visible at screen edges, from 8 to 2000px. The default is 196px.
2. **Lock-screen glass clock**: enable the glass clock in supported scenes where the system normally restricts it. It may increase power use, and some digit materials are unsupported.
3. **Super-wallpaper depth**: place wallpaper foreground objects in front of the lock-screen clock. Testing has focused on Snowmountain; results vary with wallpaper and system version.

## Requirements

- HyperOS 3/4 on Android 12 or later.
- An installed and enabled framework supporting Modern LibXposed API 102.
- Compatibility depends on the system build; not every HyperOS device is supported.

## Installation and use

1. Install the APK and enable the module in LSPosed.
2. Open Configuration and enable the features you need.
3. Restart the relevant scopes from Home to apply the module and settings.

The static scope covers System UI, the AOD and lock-screen editor, MiWallpaper, and the Moon, Snowmountain, Geometry, Saturn, Earth, and Mars Super wallpapers. Missing wallpapers do not need to be installed.

## Upgrading

Builds signed like v3.1.0, v3.0.2-beta.2, and CI.94 support in-place upgrades. Public v3.0.0 and some Debug builds use a different certificate and cannot be upgraded directly. Preserve your configuration before switching channels; do not uninstall or clear data without a backup.

Prereleases are for testing. Stable users can stay on v3.1.0. Each release page includes the APK, checksums, and known issues.

## Known issues

- Brief foreground/clock-mask misalignment can occur during AOD-to-lock-screen transitions.
- Depth recovery after a full cold boot and before the first unlock has not completed device testing.

See the [release notes](releases/v3.1.1-rc.1.md) for details.

## Reporting problems

Include the device model, HyperOS and Android versions, module and framework versions, and steps to reproduce. Include a transition recording for depth-related problems.

## License

[GNU Affero General Public License v3.0](LICENSE)
