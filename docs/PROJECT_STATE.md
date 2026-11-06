# Estado do projeto — nuppacker-android
Atualizado em: 2026-11-06 01:02 por Gemini 3.8 Flash · Versão atual: 0.1.0-dev · Ramo: main

## Objetivo
Empacotar e gerenciar dumps de Switch 1/2 no formato `.nup` diretamente no Android, preservando a lógica original do NUPPacker Python.

## Situação em 5 linhas
1. Projeto inicializado via `/novo-projeto` com governança e SemVer.
2. Licenciamento estabelecido como AGPL-3.0 (paridade com o upstream).
3. ADRs 0001, 0002 e 0003 aprovados e documentados.
4. Requisitos e arquitetura híbrida (Compose + Chaquopy) definidos.
5. Preparação para o commit inicial e criação do repositório no GitHub.

## O que funciona / O que não funciona / Não testado
| Item | Situação | Testado em (data/versão) | Evidência |
|---|---|---|---|
| Especificação e governança do projeto | Conforme | 2026-11-06 | Documentos em `docs/` |
| Motor Python de empacotamento (`nuppacker`) | Importado / A integrar | 2026-11-06 | not tested no Android |
| Interface Android Compose | Em planejamento | 2026-11-06 | not tested |
| Workflow de CI GitHub Actions | Em planejamento | 2026-11-06 | not tested |

## Em andamento
- [x] Configuração de governança e documentação base.
- [ ] Criação do repositório remoto no GitHub (`gh repo create`).
- [ ] Implementação da estrutura Android com módulo Python `nuppacker` e CI.

## Próximos passos (ordem de prioridade)
1. Realizar o primeiro commit (`chore: initial commit`).
2. Criar o repositório remoto no GitHub via `gh repo create`.
3. Montar a estrutura do projeto Android (Gradle, Compose UI, módulo Chaquopy com o motor Python e workflow CI `build.yml`).
