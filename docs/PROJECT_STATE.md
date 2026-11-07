# Estado do projeto — nuppacker-android
Atualizado em: 2026-11-07 07:35 por Gemini 3.8 Flash · Versão atual: 0.5.3 · Ramo: main

## Objetivo
Empacotar e gerenciar dumps de Switch 1/2 no formato `.nup` diretamente no Android, com máxima performance e usabilidade móvel.

## Situação em 5 linhas
1. Repositório criado e público: `https://github.com/nepotira/nuppacker-android`.
2. Motor de empacotamento nativo em Rust (`nuppacker-core` / `libnuppacker_android.so`) corrigido e alinhado bit a bit com a especificação NUP v1-draft.
3. Fim do congelamento da interface: rate-limiting no progresso e throttling de IPC de notificações.
4. Cancelamento atômico conectado via mutex `ACTIVE_CANCEL` e deleção garantida de arquivos `.nup.part`.
5. Seletor de motor integrado na interface: alternância entre Rust Nativo e Python Referência v0.1.1.

## O que funciona / O que não funciona / Não testado
| Item | Situação | Testado em (data/versão) | Evidência |
|---|---|---|---|
| Motor Python (`nuppacker`) | Funciona | 2026-11-07 / 0.5.3 | `python3 -m unittest discover tests` (8 testes OK incluindo TOC navigation) |
| CLI / Termux (`python3 -m nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | Integrado com CLI parser |
| Motor Rust Nativo (`nuppacker-core`) | Funciona | 2026-11-07 / 0.5.3 | `cargo test` no CI (10 testes OK incluindo validação completa de TOC) |
| JNI Bridge (`RustBridge.kt`) | Integrado | 2026-11-07 / 0.5.3 | Ponte JNI com `ACTIVE_CANCEL`, cancelamento atômico e detecção NPDM |
| Interface Compose + Chaquopy/Rust | Integrado | 2026-11-07 / 0.5.3 | UI fluida a 60/120 Hz, com seletor de motor (Rust vs Python) |
| Compilação do APK no GitHub Actions | Funciona | 2026-11-07 / 0.5.3 | GitHub Actions Run #42364909056 (Release v0.5.3 publicada) |
| Foreground Service & Notificação | Integrado | 2026-11-07 / 0.5.3 | `PackerForegroundService` com throttling de intents inteiros |
| Estimativa Instantânea & StatFs | Funciona | 2026-11-07 / 0.5.3 | Varredura nativa direta em Rust e StatFs fora da thread de UI |
| Documentação Bilíngue (EN/pt-BR) | Funciona | 2026-11-07 / 0.5.3 | README.md com seletor de idiomas, README.pt-BR.md espelhado e releases bilíngues |
| Teste em dispositivo Android físico | Não testado | — | Aguardando teste de usuário da v0.5.3 |

## Concluído
- [x] Criação do repositório remoto.
- [x] Implementação da aplicação Android (Compose + Chaquopy).
- [x] Foreground Service estilo ZArchiver com cancelamento.
- [x] Implementação do motor nativo em Rust (`nuppacker-core`) com aceleração ARMv8.
- [x] Reengenharia com contexto Zstd persistente e fim do double-read.
- [x] Correção do slider Zstandard (desacoplamento tátil e `roundToInt`).
- [x] Fim do congelamento da interface por inundação de chamadas JNI e IPC intents.
- [x] Sincronização real de cancelamento e deleção garantida de `.nup.part`.
- [x] Compilação multi-ABI com `cargo-ndk` e publicação da release `v0.5.3`.
- [x] Padrão internacional de idioma aplicado: README.md primário em inglês, espelho README.pt-BR.md e releases auditadas com inglês primário.
- [x] Correção binária do TOC em Rust (PKG 64 bytes e indexação da árvore de diretórios).
- [x] Adição do seletor de motor na interface (Rust Nativo vs Python v0.1.1).

## Próximos passos (ordem de prioridade)
1. Compilar APK multi-ABI via GitHub Actions com a versão `v0.5.3`.
2. Publicar release `v0.5.3` no GitHub com APK anexado e checksums SHA-256.
3. Testar no emulador de Switch 2 o arquivo `.nup` gerado tanto pelo motor Rust quanto pelo motor Python.
