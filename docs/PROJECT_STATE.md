# Estado do projeto — nuppacker-android
Atualizado em: 2026-11-06 01:10 por Gemini 3.8 Flash · Versão atual: 0.1.0-dev · Ramo: feat/01-android-app-chaquopy-engine

## Objetivo
Empacotar e gerenciar dumps de Switch 1/2 no formato `.nup` diretamente no Android, preservando a lógica original do NUPPacker Python.

## Situação em 5 linhas
1. Repositório criado e publicado no GitHub: `https://github.com/nepotira/nuppacker-android`.
2. Motor de empacotamento Python original preservado e testado em `nuppacker/`.
3. Aplicação Android completa estruturada com Jetpack Compose e Chaquopy.
4. UI moderna com seletor de arquivos, detecção de Title ID, barra de progresso e console de log.
5. Workflows de CI e Build de APK configurados para execução via GitHub CLI.

## O que funciona / O que não funciona / Não testado
| Item | Situação | Testado em (data/versão) | Evidência |
|---|---|---|---|
| Motor Python (`nuppacker`) | Funciona | 2026-11-06 / 0.1.0-dev | `python3 -m unittest discover tests` (4 testes OK) |
| CLI / Termux (`python3 -m nuppacker`) | Funciona | 2026-11-06 / 0.1.0-dev | Integrado com argumento parser |
| Estrutura Android Gradle & Chaquopy | Configurado | 2026-11-06 / 0.1.0-dev | Arquivos Gradle e Manifest |
| Compilação do APK via GitHub Actions | A disparar | 2026-11-06 / 0.1.0-dev | not tested no CI |

## Em andamento
- [x] Criação do repositório remoto no GitHub.
- [x] Implementação da aplicação Android (Compose + Chaquopy).
- [ ] Submissão do ramo `feat/01-android-app-chaquopy-engine`, abertura de PR e disparo do workflow de build do APK no GitHub Actions.

## Próximos passos (ordem de prioridade)
1. Commitar as alterações na branch `feat/01-android-app-chaquopy-engine`.
2. Enviar a branch para o remoto (`git push -u origin feat/01-android-app-chaquopy-engine`).
3. Criar Pull Request via `gh pr create` e mergear após validação.
4. Disparar e monitorar o build do APK via `gh workflow run build.yml`.
