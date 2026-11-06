# NUPPacker Android

A native Android application to pack Nintendo Switch 1 and Switch 2 dumps into `.nup` container files.

Based on [PastaLaPate/NUPPacker](https://github.com/PastaLaPate/NUPPacker) and the [NUP container specification](https://github.com/a55uka/nup).

![status](https://img.shields.io/badge/status-stable-brightgreen)
![version](https://img.shields.io/badge/version-v0.1.0-blue)
![license](https://img.shields.io/badge/license-AGPL--3.0-blue)
![platform](https://img.shields.io/badge/platform-Android%208.0+-green)

---

## What is NUP?

NUP (NX Package Format) is a clean-room container format for unencrypted NX (Nintendo Switch 1/2) game dumps. It bundles base game files (`exefs`, `romfs`), updates, and DLCs into a single compressed container with deduplication and metadata support.

## Key Features

- **Native Android UI**: Built with Jetpack Compose & Material 3.
- **Preserved Core Engine**: Reuses the tested packaging logic from original `NUPPacker` via embedded Python runtime (Chaquopy).
- **Zstandard Compression**: Configurable compression level (0 = raw store up to 22).
- **Auto Title ID**: Detects Program Title ID directly from `exefs/main.npdm`.
- **Deduplication**: Automatically deduplicates identical files via SHA-256 matching.
- **Large Dumps Support**: Direct storage access (`MANAGE_EXTERNAL_STORAGE`) and background execution.
- **100% Offline & Private**: Zero telemetry, no internet permission required.

## Building the APK

Builds are automated via GitHub Actions:
```bash
gh workflow run build.yml --ref main
```

Or build locally with Gradle:
```bash
./gradlew assembleDebug
```

## Documentation

Full project documentation is available in [`docs/`](docs/):
- [Architecture](docs/ARCHITECTURE.md)
- [Requirements](docs/REQUIREMENTS.md)
- [Project State](docs/PROJECT_STATE.md)
- [Licensing](docs/LICENSING.md)
- [Architecture Decision Records (ADRs)](docs/adr/)

## License

This project is licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE).
See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for third-party acknowledgments.
