[English](README.md) | [Português do Brasil](README.pt-BR.md)

# NUPPacker Android

A high-performance native Android application to pack Nintendo Switch 1 and Switch 2 dumps into `.nup` container files.

Based on [PastaLaPate/NUPPacker](https://github.com/PastaLaPate/NUPPacker) and the [NUP container specification](https://github.com/a55uka/nup).

![status](https://img.shields.io/badge/status-stable-brightgreen)
![version](https://img.shields.io/badge/version-v0.5.2-blue)
![license](https://img.shields.io/badge/license-AGPL--3.0-blue)
![platform](https://img.shields.io/badge/platform-Android%208.0+-green)

---

## What is NUP?

NUP (NX Package Format) is a clean-room container format for unencrypted NX (Nintendo Switch 1/2) game dumps. It bundles base game files (`exefs`, `romfs`), updates, and DLCs into a single compressed container with deduplication and metadata support.

## Key Features

- **High-Performance Rust Native Core**: Accelerated packaging via `nuppacker-core` with ARMv8 hardware SHA-256 acceleration (~2.8 GB/s), single-pass streaming, and persistent Zstandard compression context.
- **Fluid & Non-Blocking UI**: Modern Jetpack Compose Material 3 interface with asynchronous background scanning, instant storage estimation (`StatFs`), and rate-limited UI updates (60/120 Hz butter-smooth interaction).
- **ZArchiver-Style Background Service**: Full foreground service execution with interactive status notification, remaining time estimation, and guaranteed atomic cancellation (cleans up any partial `.nup.part` files immediately).
- **Configurable Zstandard Compression**: Slider smoothly adjustable from raw store (Level 0) up to ultra compression (Level 22).
- **Automatic Title ID & Metadata Detection**: Reads Program Title ID directly from `exefs/main.npdm` without user intervention.
- **Smart Deduplication**: Eliminates duplicate files across updates and base games via SHA-256 content hashing.
- **100% Offline & Private**: Zero telemetry, no network permission requested.

## Building the APK

Builds are automated via GitHub Actions:
```bash
gh workflow run build.yml --ref main
```

Or build locally with Gradle and Cargo NDK:
```bash
# Requires Android NDK and Rust toolchain with target aarch64-linux-android
./gradlew assembleRelease
```

## Documentation

Full project documentation is available in [`docs/`](docs/):
- [Architecture](docs/ARCHITECTURE.md)
- [Requirements](docs/REQUIREMENTS.md)
- [Project State](docs/PROJECT_STATE.md)
- [Performance & Optimizations Analysis](docs/ANALISE_PERFORMANCE_E_OTIMIZACOES.md)
- [Licensing](docs/LICENSING.md)
- [Architecture Decision Records (ADRs)](docs/adr/)

## Releases & Downloads

Pre-built universal APKs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) with verified SHA-256 checksums are published in [GitHub Releases](https://github.com/nepotira/nuppacker-android/releases).

## License

This project is licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE).
See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for third-party acknowledgments.
