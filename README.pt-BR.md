[English](README.md) | [Português do Brasil](README.pt-BR.md)

# NUPPacker Android

Aplicativo Android nativo de alta performance para empacotar dumps de Nintendo Switch 1 e Switch 2 no formato de contêiner `.nup`.

Baseado em [PastaLaPate/NUPPacker](https://github.com/PastaLaPate/NUPPacker) e na [especificação de contêiner NUP](https://github.com/a55uka/nup).

![status](https://img.shields.io/badge/status-stable-brightgreen)
![version](https://img.shields.io/badge/version-v0.5.3-blue)
![license](https://img.shields.io/badge/license-AGPL--3.0-blue)
![platform](https://img.shields.io/badge/platform-Android%208.0+-green)

---

## O que é o NUP?

O NUP (NX Package Format) é um formato de contêiner *clean-room* para dumps não criptografados de jogos de NX (Nintendo Switch 1 e 2). Ele agrupa arquivos do jogo base (`exefs`, `romfs`), atualizações e DLCs em um único contêiner comprimido com suporte a desduplicação e metadados.

## Principais Funcionalidades

- **Motor Nativo de Alta Performance em Rust**: Empacotamento acelerado via `nuppacker-core` com aceleração criptográfica por hardware ARMv8 SHA-256 (~2.8 GB/s), streaming em passagem única e contexto persistente de compressão Zstandard.
- **Interface Fluida e Não-Bloqueante**: Interface moderna em Jetpack Compose Material 3 com varredura assíncrona em segundo plano, estimativa de armazenamento instantânea (`StatFs`) e atualizações de taxa limitada para a UI (60/120 Hz perfeitamente responsivos).
- **Serviço em Segundo Plano Estilo ZArchiver**: Execução contínua em Foreground Service com notificação de status interativa, previsão de tempo restante e cancelamento atômico garantido (exclusão imediata de resquícios de arquivos parciais `.nup.part`).
- **Compressão Zstandard Configurável**: Controle suave via slider variando de armazenamento puro sem compressão (Nível 0) até compressão máxima (Nível 22).
- **Detecção Automática de Title ID e Metadados**: Extração direta do Program Title ID a partir de `exefs/main.npdm` sem necessidade de digitação do usuário.
- **Desduplicação Inteligente**: Elimina arquivos idênticos entre jogo base e atualizações através do hash de conteúdo SHA-256.
- **100% Offline e Privado**: Sem telemetria e sem necessidade de permissão de acesso à internet.

## Como Compilar o APK

As compilações são automatizadas via GitHub Actions:
```bash
gh workflow run build.yml --ref main
```

Ou compile localmente utilizando o Gradle e o Cargo NDK:
```bash
# Requer Android NDK e toolchain Rust configurada para o target aarch64-linux-android
./gradlew assembleRelease
```

## Documentação

A documentação completa do projeto está disponível em [`docs/`](docs/):
- [Arquitetura](docs/ARCHITECTURE.md)
- [Requisitos](docs/REQUIREMENTS.md)
- [Estado do Projeto](docs/PROJECT_STATE.md)
- [Análise de Performance e Otimizações](docs/ANALISE_PERFORMANCE_E_OTIMIZACOES.md)
- [Licenciamento](docs/LICENSING.md)
- [Registros de Decisão de Arquitetura (ADRs)](docs/adr/)

## Releases e Downloads

APKs universais pré-compilados (`arm64-v8a`, `armeabi-v7a`, `x86_64`) com checksums SHA-256 verificados são publicados na seção de [Releases do GitHub](https://github.com/nepotira/nuppacker-android/releases).

## Licença

Este projeto é licenciado sob a [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE).
Consulte [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) para atribuições e licenças de terceiros.
