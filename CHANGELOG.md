# Changelog
Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) · Versionamento: [SemVer 2.0.0](https://semver.org/lang/pt-BR/)

## [Unreleased]

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

[0.2.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.2.0
[0.1.1]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.1.1
[0.1.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.1.0
