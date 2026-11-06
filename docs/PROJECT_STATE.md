# Estado do projeto — nuppacker-android
Atualizado em: 2026-11-06 02:40 por Gemini 3.8 Flash · Versão atual: 0.3.0 · Ramo: feat/04-storage-estimation-and-focus-fix

## Objetivo
Empacotar e gerenciar dumps de Switch 1/2 no formato `.nup` diretamente no Android, com máxima performance e usabilidade móvel.

## Situação em 5 linhas
1. Repositório criado e público: `https://github.com/nepotira/nuppacker-android`.
2. Motor de empacotamento com streaming single-pass (sem double read) e compressão Zstandard multi-thread.
3. Operação em background via `PackerForegroundService` com notificação estilo ZArchiver, Wakelock e botão cancelar.
4. Estimativa de armazenamento em tempo real com cálculo de espaço livre e aviso preventivo de falta de espaço.
5. Usabilidade aprimorada com fechamento de teclado/deseleção ao tocar fora e ao pausar o app.

## O que funciona / O que não funciona / Não testado
| Item | Situação | Testado em (data/versão) | Evidência |
|---|---|---|---|
| Motor Python (`nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | `python3 -m unittest discover tests` (7 testes OK) |
| CLI / Termux (`python3 -m nuppacker`) | Funciona | 2026-11-06 / 0.3.0 | Integrado com CLI parser |
| Interface Compose + Chaquopy | Integrado | 2026-11-06 / 0.3.0 | Código e recursos estruturados |
| Compilação do APK no GitHub Actions | Funciona | 2026-11-06 / 0.2.0 | Run #42163935279 (sucesso, APK gerado) |
| Foreground Service & Notificação | Integrado | 2026-11-06 / 0.3.0 | `PackerForegroundService` com notificação persistente |
| Estimativa de Espaço & StatFs | Integrado | 2026-11-06 / 0.3.0 | `StatFs` e cálculo recursivo com ratios zstd |
| Deseleção de inputs / Teclado | Integrado | 2026-11-06 / 0.3.0 | `clearFocus` ao clicar fora e no `ON_PAUSE` |
| Teste em dispositivo Android físico | Não testado | — | Aguardando teste de usuário |

## Em andamento
- [x] Criação do repositório remoto.
- [x] Implementação da aplicação Android (Compose + Chaquopy).
- [x] Otimização single-pass e multi-threading Zstd.
- [x] Foreground Service estilo ZArchiver com cancelamento.
- [x] Estimativa de armazenamento e alerta preventivo de falta de espaço.
- [x] Correção de foco dos campos de texto e teclado.
- [ ] Compilação e publicação da release `v0.3.0`.

## Próximos passos (ordem de prioridade)
1. Abrir PR, mesclar na `main`, compilar APK no GitHub Actions e publicar `v0.3.0`.
2. Apresentar análise comparativa profunda de linguagens (C++ vs Rust vs Python) para evolução do motor.
