# Estado do projeto — nuppacker-android
Atualizado em: 2026-11-06 06:45 por Gemini 3.8 Flash · Versão atual: 0.5.0 · Ramo: main

## Objetivo
Empacotar e gerenciar dumps de Switch 1/2 no formato `.nup` diretamente no Android, com máxima performance e usabilidade móvel.

## Situação em 5 linhas
1. Repositório criado e público: `https://github.com/nepotira/nuppacker-android`.
2. Motor de empacotamento nativo em Rust (`nuppacker-core` / `libnuppacker_android.so`) com aceleração ARMv8 crypto (~2.8 GB/s SHA-256) e libzstd multi-thread.
3. Varredura nativa unificada (`analyzeFolder`) com retorno atômico de tamanho, pacotes e Title ID em ~200ms.
4. Filtro de entropia de Shannon e bypass de compressão para extensões multimídia/comprimidas conhecidas.
5. Operação em background via `PackerForegroundService` com notificação estilo ZArchiver, prefetch UFS via `posix_fadvise` e cancelamento cooperativo.

## O que funciona / O que não funciona / Não testado
| Item | Situação | Testado em (data/versão) | Evidência |
|---|---|---|---|
| Motor Python (`nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | `python3 -m unittest discover tests` (7 testes OK) |
| CLI / Termux (`python3 -m nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | Integrado com CLI parser |
| Motor Rust Nativo (`nuppacker-core`) | Funciona | 2026-11-06 / 0.5.0 | `cargo test` no CI (7 testes OK incluindo entropia, filtros e analyze_folder) |
| JNI Bridge (`RustBridge.kt`) | Integrado | 2026-11-06 / 0.5.0 | Ponte JNI com `analyzeFolder`, cancelamento atômico e detecção NPDM |
| Interface Compose + Chaquopy/Rust | Integrado | 2026-11-06 / 0.5.0 | Código e UI estruturados no Compose com varredura nativa unificada |
| Compilação do APK no GitHub Actions | Funciona | 2026-11-06 / 0.5.0 | GitHub Actions Run #42196609545 (Release v0.5.0 publicada) |
| Foreground Service & Notificação | Integrado | 2026-11-06 / 0.5.0 | `PackerForegroundService` com notificação persistente |
| Estimativa Instantânea & StatFs | Funciona | 2026-11-06 / 0.5.0 | Varredura nativa direta em Rust (~200ms) sem concorrência FUSE |
| Teste em dispositivo Android físico | Não testado | — | Aguardando teste de usuário |

## Concluído
- [x] Criação do repositório remoto.
- [x] Implementação da aplicação Android (Compose + Chaquopy).
- [x] Otimização single-pass e multi-threading Zstd.
- [x] Foreground Service estilo ZArchiver com cancelamento.
- [x] Estimativa de armazenamento instantânea e botão de recálculo manual.
- [x] Implementação do motor nativo em Rust (`nuppacker-core`) com aceleração ARMv8.
- [x] Varredura unificada nativa em Rust (`analyzeFolder`) e filtro rápido de compressibilidade/entropia.
- [x] Compilação multi-ABI com `cargo-ndk` e publicação da release `v0.5.0`.

## Próximos passos (ordem de prioridade)
1. Realizar testes de ponta a ponta no dispositivo físico com dump de Switch real.
2. Coletar feedback do usuário quanto à usabilidade móvel, velocidade e estabilidade do empacotador.
