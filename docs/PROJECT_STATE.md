# Estado do projeto — nuppacker-android
Atualizado em: 2026-11-06 05:40 por Gemini 3.8 Flash · Versão atual: 0.4.2 · Ramo: main

## Objetivo
Empacotar e gerenciar dumps de Switch 1/2 no formato `.nup` diretamente no Android, com máxima performance e usabilidade móvel.

## Situação em 5 linhas
1. Repositório criado e público: `https://github.com/nepotira/nuppacker-android`.
2. Motor de empacotamento nativo em Rust (`nuppacker-core` / `libnuppacker_android.so`) com aceleração ARMv8 crypto (~2.8 GB/s SHA-256) e libzstd multi-thread.
3. Fallback transparente para o motor Python Chaquopy se binários nativos não estiverem presentes.
4. Operação em background via `PackerForegroundService` com notificação estilo ZArchiver, cancelamento cooperativo e StatFs.
5. Usabilidade móvel completa com cálculo instantâneo de tamanho, botão de recálculo manual e prevenção de travamento de teclado.

## O que funciona / O que não funciona / Não testado
| Item | Situação | Testado em (data/versão) | Evidência |
|---|---|---|---|
| Motor Python (`nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | `python3 -m unittest discover tests` (7 testes OK) |
| CLI / Termux (`python3 -m nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | Integrado com CLI parser |
| Motor Rust Nativo (`nuppacker-core`) | Funciona | 2026-11-06 / 0.4.2 | `cargo test` no CI (4 testes OK: align16, tlv, parse_ver, synthetic_build) |
| JNI Bridge (`RustBridge.kt`) | Integrado | 2026-11-06 / 0.4.2 | `libnuppacker_android.so` incluído no APK para arm64-v8a, armeabi-v7a, x86_64 |
| Interface Compose + Chaquopy/Rust | Integrado | 2026-11-06 / 0.4.2 | Código e UI estruturados no Compose |
| Compilação do APK no GitHub Actions | Funciona | 2026-11-06 / 0.4.2 | GitHub Actions Run #42188669550 (APK v0.4.2 gerado e publicado) |
| Foreground Service & Notificação | Integrado | 2026-11-06 / 0.4.2 | `PackerForegroundService` com notificação persistente |
| Estimativa Instantânea & StatFs | Integrado | 2026-11-06 / 0.4.2 | NIO `walkFileTree` com cache em memória e recálculo manual |
| Teste em dispositivo Android físico | Não testado | — | Aguardando teste de usuário |

## Concluído
- [x] Criação do repositório remoto.
- [x] Implementação da aplicação Android (Compose + Chaquopy).
- [x] Otimização single-pass e multi-threading Zstd.
- [x] Foreground Service estilo ZArchiver com cancelamento.
- [x] Estimativa de armazenamento instantânea e botão de recálculo manual.
- [x] Implementação do motor nativo em Rust (`nuppacker-core`) com aceleração ARMv8.
- [x] Compilação multi-ABI com `cargo-ndk` e publicação da release `v0.4.2`.

## Próximos passos (ordem de prioridade)
1. Aguardar feedback do usuário sobre a performance do empacotamento no hardware físico do Android.
2. Suporte opcional a hashing Blake3 se a especificação NUP vier a adotá-lo.
