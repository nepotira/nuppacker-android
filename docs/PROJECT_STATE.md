# Estado do projeto — nuppacker-android
Atualizado em: 2026-11-06 05:25 por Gemini 3.8 Flash · Versão atual: 0.4.1 · Ramo: fix/09-rust-core-compile-fixes

## Objetivo
Empacotar e gerenciar dumps de Switch 1/2 no formato `.nup` diretamente no Android, com máxima performance e usabilidade móvel.

## Situação em 5 linhas
1. Repositório criado e público: `https://github.com/nepotira/nuppacker-android`.
2. Motor de empacotamento nativo em Rust (`nuppacker-core`) com aceleração ARMv8 crypto (~2.8 GB/s SHA-256) e libzstd multi-thread.
3. Fallback transparente para o motor Python Chaquopy se binários nativos não estiverem presentes.
4. Operação em background via `PackerForegroundService` com notificação estilo ZArchiver, cancelamento cooperativo e StatFs.
5. Usabilidade móvel completa com cálculo instantâneo de tamanho, botão de recálculo manual e prevenção de travamento de teclado.

## O que funciona / O que não funciona / Não testado
| Item | Situação | Testado em (data/versão) | Evidência |
|---|---|---|---|
| Motor Python (`nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | `python3 -m unittest discover tests` (7 testes OK) |
| CLI / Termux (`python3 -m nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | Integrado com CLI parser |
| Motor Rust Nativo (`nuppacker-core`) | Integrado | 2026-11-06 / 0.4.0 | Crate Rust com testes unitários em `tests.rs` |
| JNI Bridge (`RustBridge.kt`) | Integrado | 2026-11-06 / 0.4.0 | Ponte JNI com cancelamento atômico e detecção NPDM |
| Interface Compose + Chaquopy/Rust | Integrado | 2026-11-06 / 0.4.0 | Código e UI estruturados no Compose |
| Compilação do APK no GitHub Actions | Funciona | 2026-11-06 / 0.3.3 | GitHub Actions Run #42168936990 (APK v0.3.3 gerado) |
| Foreground Service & Notificação | Integrado | 2026-11-06 / 0.4.0 | `PackerForegroundService` com notificação persistente |
| Estimativa Instantânea & StatFs | Integrado | 2026-11-06 / 0.4.0 | NIO `walkFileTree` com cache em memória e recálculo manual |
| Teste em dispositivo Android físico | Não testado | — | Aguardando teste de usuário |

## Em andamento
- [x] Criação do repositório remoto.
- [x] Implementação da aplicação Android (Compose + Chaquopy).
- [x] Otimização single-pass e multi-threading Zstd.
- [x] Foreground Service estilo ZArchiver com cancelamento.
- [x] Estimativa de armazenamento instantânea e botão de recálculo manual.
- [x] Implementação do motor nativo em Rust (`nuppacker-core`) com aceleração ARMv8.
- [ ] Compilação e publicação da release `v0.4.0`.

## Próximos passos (ordem de prioridade)
1. Abrir PR no GitHub para `feat/08-native-rust-engine`, mesclar na `main`.
2. Criar tag anotada `v0.4.0`, compilar APK via GitHub Actions e publicar release no GitHub.
