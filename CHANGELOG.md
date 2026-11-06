# Changelog
Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) · Versionamento: [SemVer 2.0.0](https://semver.org/lang/pt-BR/)

## [Unreleased]

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

[0.1.1]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.1.1
[0.1.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.1.0
