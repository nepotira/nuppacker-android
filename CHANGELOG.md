# Changelog
Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) · Versionamento: [SemVer 2.0.0](https://semver.org/lang/pt-BR/)

## [Unreleased]

## [0.3.1] - 2026-11-06

### Fixed
- Fixed Kotlin compiler error `Platform declaration clash (setIgnoreStorageWarning(Z)V)` by renaming method to `updateIgnoreStorageWarning`.

## [0.3.0] - 2026-11-06

### Added
- **Storage & Space Estimation Card**: Real-time calculation of original dump size, estimated final `.NUP` size based on selected Zstd level, and available free storage on the destination drive using `StatFs`.
- **Low Disk Space Warning & Safety Lock**: Automatic detection when device storage is insufficient for the build, disabling the start button with a clear warning banner and providing an override checkbox ("Ignore warning and build anyway") to prevent crashes and file corruption.

### Fixed
- **Input Focus & Virtual Keyboard Dismissal**: Implemented tap-to-clear-focus on all cards, empty spaces, and background areas, preventing text inputs from staying persistently focused.
- **Auto Keyboard Dismiss on App Pause**: Automatically clears focus when the app is minimized or backgrounded (`ON_PAUSE`), preventing the virtual keyboard from automatically reopening upon returning to the app.
- Configured `ImeAction.Done` and `keyboardActions` on all single-line input fields for immediate keyboard dismissal on keyboard action.

## [0.2.0] - 2026-11-06

### Added
- **High-Performance Single-Pass Streaming Engine**: Overhauled file processing to simultaneously hash SHA-256 and compress Zstandard in a single read pass, cutting disk I/O time in half and eliminating redundant double-reads.
- **Multi-threaded Zstandard**: Added multi-core thread scaling (`threads = min(cores, 8)`) for high-speed compression on modern Android multi-core CPUs.
- **Foreground Service & ZArchiver-style Notification**: Persistent background operation (`PackerForegroundService`) with Wakelock, real-time progress bar, status, and cancel action right in the notification shade.
- **Battery Optimization & OEM Management**: Added battery optimization whitelist integration with custom detection and guidance for HyperOS / MIUI, Samsung One UI, Motorola Moto UI, and pure AOSP.
- **Cancellation Button**: Added "Cancel Build" button both on the UI and inside the ongoing notification, with immediate cleanup of partial files (`.part`).
- **Output Directory Picker**: Added dedicated folder picker button for the destination `.nup` file, maintaining the file name while allowing easy directory selection.

## [0.1.1] - 2026-11-06

### Fixed
- Fixed `'LinkedHashMap' object is not subscriptable` error when passing Kotlin parameters to Python in Chaquopy by serializing configs to JSON and adding recursive proxy normalization.
- Fixed destination directory creation by automatically creating parent directories if they do not exist.
- Fixed Title ID parsing to safely strip `0x` prefixes, lowercase characters, and whitespace.

### Changed
- Set default Zstandard compression level to **3** (the official recommended standard for NUP containers in emulators for fast streaming decompression).

## [0.1.0] - 2026-11-06

### Added
- Native Android app with Jetpack Compose (Material 3) and direct storage access.
- Embedded Chaquopy Python runtime preserving original `nuppacker` packaging logic.
- Real-time progress bar, live terminal log console, and Title ID auto-detection.
- Standalone CLI interface (`python3 -m nuppacker`) compatible with Termux.
- Automated unit test suite verifying TOC alignment, TLV blocks, and Superblock.
- GitHub Actions workflows for continuous integration and automated APK compilation.
- Initial project structure with documentation and governance under AGPL-3.0.

[0.3.1]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.3.1
[0.3.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.3.0
[0.2.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.2.0
[0.1.1]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.1.1
[0.1.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.1.0
