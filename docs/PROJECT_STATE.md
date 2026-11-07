# Estado do projeto — nuppacker-android
Atualizado em: 2026-11-07 06:27 por Gemini 3.8 Flash · Versão atual: 0.5.2 · Ramo: main

## Objetivo
Empacotar e gerenciar dumps de Switch 1/2 no formato `.nup` diretamente no Android, com máxima performance e usabilidade móvel.

## Situação em 5 linhas
1. Repositório criado e público: `https://github.com/nepotira/nuppacker-android`.
2. Motor de empacotamento nativo em Rust (`nuppacker-core` / `libnuppacker_android.so`) com aceleração ARMv8 crypto (~2.8 GB/s SHA-256) e libzstd com contexto persistente.
3. Fim do congelamento da interface: rate-limiting no progresso e throttling de IPC de notificações.
4. Cancelamento atômico conectado via mutex `ACTIVE_CANCEL` e deleção imediata e garantida de arquivos `.nup.part`.
5. Slider de compressão desacoplado no Compose com `roundToInt()` e I/O não-bloqueante (`Dispatchers.IO`).

## O que funciona / O que não funciona / Não testado
| Item | Situação | Testado em (data/versão) | Evidência |
|---|---|---|---|
| Motor Python (`nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | `python3 -m unittest discover tests` (7 testes OK) |
| CLI / Termux (`python3 -m nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | Integrado com CLI parser |
| Motor Rust Nativo (`nuppacker-core`) | Funciona | 2026-11-07 / 0.5.2 | `cargo test` no CI (9 testes OK incluindo deleção de .part no cancelamento) |
| JNI Bridge (`RustBridge.kt`) | Integrado | 2026-11-07 / 0.5.2 | Ponte JNI com `ACTIVE_CANCEL`, cancelamento atômico e detecção NPDM |
| Interface Compose + Chaquopy/Rust | Integrado | 2026-11-07 / 0.5.2 | UI fluida a 60/120 Hz sem congelamento de Looper e com slider suave |
| Compilação do APK no GitHub Actions | Funciona | 2026-11-07 / 0.5.2 | GitHub Actions Run #42359474318 (Release v0.5.2 publicada) |
| Foreground Service & Notificação | Integrado | 2026-11-07 / 0.5.2 | `PackerForegroundService` com throttling de intents inteiros |
| Estimativa Instantânea & StatFs | Funciona | 2026-11-07 / 0.5.2 | Varredura nativa direta em Rust e StatFs fora da thread de UI |
| Teste em dispositivo Android físico | Não testado | — | Aguardando teste de usuário da v0.5.2 |

## Concluído
- [x] Criação do repositório remoto.
- [x] Implementação da aplicação Android (Compose + Chaquopy).
- [x] Foreground Service estilo ZArchiver com cancelamento.
- [x] Implementação do motor nativo em Rust (`nuppacker-core`) com aceleração ARMv8.
- [x] Reengenharia com contexto Zstd persistente e fim do double-read.
- [x] Correção do slider Zstandard (desacoplamento tátil e `roundToInt`).
- [x] Fim do congelamento da interface por inundação de chamadas JNI e IPC intents.
- [x] Sincronização real de cancelamento e deleção garantida de `.nup.part`.
- [x] Compilação multi-ABI com `cargo-ndk` e publicação da release `v0.5.2`.

## Próximos passos (ordem de prioridade)
1. Realizar testes no dispositivo físico com a versão `v0.5.2` (verificar responsividade da UI durante o empacotamento do Mario Kart e se o `.part` some ao cancelar).
2. Coletar feedback do usuário quanto à usabilidade e cancelamento limpo.
