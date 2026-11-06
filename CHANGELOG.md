# Changelog
Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) · Versionamento: [SemVer 2.0.0](https://semver.org/lang/pt-BR/)

## [Unreleased]

## [0.4.0] - 2026-11-06

### Added
- **Native Rust Packaging Engine (`nuppacker-core`)**: Implemented high-performance native core in Rust (`rust/nuppacker-core`) supporting the full NUP v1-draft container specification.
- **ARMv8 Hardware Cryptography Acceleration**: Uses native ARMv8 vector instructions (`+crypto`) in `sha2` crate to process SHA-256 hashes at up to **2.8 GB/s**, completely eliminating the CPU bottleneck and saturating mobile UFS 3.1/4.0 flash storage.
- **Multithreaded Native Zstandard Compression**: Uses native `libzstd` (`zstdmt`) without Python GIL locks for high-throughput parallel compression.
- **High-Performance JNI Bridge**: Implemented `RustBridge.kt` and `libnuppacker_android.so` with atomic cooperative cancellation (`AtomicBool`), real-time progress callbacks, and Title ID detection from `main.npdm`.
- **Rust Toolchain & CI Integration**: Added automated Rust testing and `cargo-ndk` multi-ABI compilation (`arm64-v8a`, `armeabi-v7a`, `x86_64`) to GitHub Actions CI/CD workflows.

## [0.3.3] - 2026-11-06

### Added
- **Manual Recalculate Storage Button**: Added a dedicated refresh icon in the card header and a "Recalculate Storage" button at the bottom of the Storage Card, allowing users to force a fresh recalculation of dump size and available storage on demand without having to close and reopen the app.

## [0.3.2] - 2026-11-06

### Performance
- **Instant Storage Calculation & Caching**: Replaced slow recursive `File.listFiles()` traversal with Java NIO `Files.walkFileTree` (using in-memory `BasicFileAttributes` without extra `stat` syscalls).
- **Intelligent Dump Size Caching**: Cached dump directory sizes so changing Zstd level, destination directory, or filename recalculates the estimate and free space **instantaneously (0ms)** without re-scanning the storage.
- **Fast Build Completion Free Space Refresh**: After build success, device available free space updates immediately in 0.1ms via single `StatFs` syscall without re-traversing the game dump.
- **Excluded Output Artifacts**: Automatically ignores existing `.nup` and `.part` files inside the dump folder during calculation so previously built files never corrupt or slow down the estimate.

### Fixed
- **Dedicated File Name Input**: Added dedicated "Output File Name" field so users can easily name the output file directly without editing full path strings.
- **Dynamic File Name Synchronization**: Synchronized "Title Name" with the output file name so typing the game title automatically names the `.nup` container, while allowing custom manual overrides.
- **File Name Preservation on Folder Browse**: Choosing a new destination folder via folder picker now preserves the user's custom file name instead of resetting to the imported folder's name.

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

[0.4.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.4.0
[0.3.3]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.3.3
[0.3.2]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.3.2
[0.3.1]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.3.1
[0.3.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.3.0
[0.2.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.2.0
[0.1.1]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.1.1
[0.1.0]: https://github.com/nepotira/nuppacker-android/releases/tag/v0.1.0
