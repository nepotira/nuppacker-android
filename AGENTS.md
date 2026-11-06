# AGENTS.md — nuppacker-android
> Manual de operação para agentes de IA. Curto de propósito: o detalhe está em `docs/` e `.agents/rules/`.

## Projeto
Empacotar e gerenciar dumps de Switch 1/2 no formato `.nup` diretamente no Android, preservando a lógica comprovada do NUPPacker Python original. · Stack: Android (Kotlin + Jetpack Compose) + Chaquopy (Python 3.11+, zstandard) · Dono: nepo · Licença: AGPL-3.0-only (SPDX)

## Perfil do projeto (o agente adapta o rigor e as ferramentas a isto)
- Tipo: app mobile
- Equipe: solo
- Risco de ações: baixo
- Distribuição: público · Dados pessoais: não
- Plataforma de trabalho atual: Linux (x86_64)
- Compilação: GitHub Actions via gh (skill compilacao-github-cli)

## Comandos (manter sempre verdadeiros)
- Instalar: `./gradlew tasks`
- Rodar: `./gradlew installDebug` (ou `adb install`)
- Testar: `./gradlew test` / `python3 -m unittest discover tests`
- Lint: `./gradlew lint`
- Build: `./gradlew assembleRelease` ou `./gradlew assembleDebug`
- CI Build: `gh workflow run build.yml --ref main`

## Regras essenciais
1. Início de sessão: ler este arquivo, `docs/PROJECT_STATE.md`, último handoff, `git status -sb`, `git log --oneline -10`.
2. Um ramo por tarefa: `tipo/NN-descricao-curta`. Nunca commitar direto na `main` (exceto commit inicial).
3. Commits: Conventional Commits `tipo(escopo)!: descrição` (até 72 caracteres, imperativo), atômicos. Rodapés: `Refs: #N`, `Assisted-by: <agente/modelo>`.
4. Versão: SemVer 2.0.0 (fix → PATCH, feat → MINOR, BREAKING → MAJOR). Fonte única: `app/build.gradle.kts`. Tag anotada `vX.Y.Z`.
5. Toda mudança atualiza `CHANGELOG.md [Unreleased]` e `docs/JOURNAL.md`; mudou o estado → `docs/PROJECT_STATE.md`.
6. Decisão arquitetural, de tecnologia ou de licença → ADR em `docs/adr/`.
7. "Pronto" = testes/build rodados e evidência registrada.
8. Segredos nunca no repositório. `.env.example` atualizado.
9. Ação destrutiva = backup + explicação + confirmação. Instalar ferramenta = perguntar antes.
10. Compilar via GitHub Actions com `gh` e monitorar até o fim; local só em último caso, com o motivo registrado.
11. Licenças: todo código novo segue AGPL-3.0; terceiros em `THIRD_PARTY_LICENSES.md`.
12. Antes de parar ou trocar de agente: `/handoff`. Ao chegar: `/retomar`.
13. Passo a passo para o usuário sempre com o COMO (caminho, clique/toque, comando completo).

## Regras locais deste projeto
- Manter o código original de empacotamento (`nuppacker`) o mais intacto possível, rodando via runtime Python embarcado no Android.
- Respeitar a especificação oficial NUP v1-draft (`a55uka/nup`).

## Mapa da documentação
`README.md` · `CONTRIBUTING.md` · `SECURITY.md` · `LICENSE` · `NOTICE` · `THIRD_PARTY_LICENSES.md` · `CHANGELOG.md` · `docs/` (PROJECT_STATE, REQUIREMENTS, ARCHITECTURE, RUNBOOK, TESTING, TROUBLESHOOTING, GLOSSARY, JOURNAL, RETROFIT, LICENSING, ENVIRONMENT) · `docs/adr/` · `docs/handoffs/`

## Nunca
- Reescrever histórico publicado, mexer em tag publicada ou apagar branch sem pedido explícito.
- Adicionar dependência sem registrar motivo e licença.
- Ampliar o escopo além da tarefa aprovada.
- Distribuir jogos, chaves de criptografia ou material proprietário.
