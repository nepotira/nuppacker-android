# Diário do projeto
Cronológico, mais novo embaixo. Use o modelo da regra `30-documentacao`. Registros retroativos levam o selo abaixo.

> Selo retroativo: `(registro retroativo: fato ocorrido em AAAA-MM-DD HH:MM; reconstruído em AAAA-MM-DD; confiança: alta|média|baixa; evidência: <git/arquivo/relato>)`

---

## 2026-11-06 01:00 — Inicialização do Projeto NUPPacker Android
- **Contexto**: Solicitação do usuário para criar versão Android do NUPPacker (https://github.com/PastaLaPate/NUPPacker), com o requisito explícito de manter ao máximo o código original executável no Android.
- **Ações**:
  - Classificação como NOVO e execução do `/novo-projeto`.
  - Cópia dos templates via `bootstrap-projeto.sh`.
  - Configuração da licença AGPL-3.0 em paridade com o upstream.
  - Definição dos ADRs 0001 (Governança), 0002 (Stack Android + Chaquopy) e 0003 (Licença AGPL-3.0).
  - Elaboração de `REQUIREMENTS.md`, `ARCHITECTURE.md` e preenchimento de `AGENTS.md`.
- **Decisão técnica**: Utilizar Jetpack Compose para a interface e Chaquopy para executar o código Python original do empacotador com biblioteca nativa Zstandard.

## 2026-11-06 01:10 — Implementação do app Android e motor Python Chaquopy
- **Ramo**: `feat/01-android-app-chaquopy-engine`
- **Ações**:
  - Extração e preservação do motor Python original (`nuppacker/__init__.py`) com ponte Android (`run_packer_bridge`, `auto_detect_title_id`, `discover_summary`).
  - Criação de testes unitários automatizados em `tests/test_nuppacker.py` (executados com sucesso via `python3 -m unittest`).
  - Estruturação do projeto Android Gradle com Jetpack Compose (Material 3), Chaquopy 17.2.0 com `zstandard`, e permissão `MANAGE_EXTERNAL_STORAGE`.
  - Implementação da UI tátil em `MainActivity.kt` e `PackerViewModel.kt` com barra de progresso, console de logs em tempo real e detecção de Title ID.
  - Configuração dos workflows do GitHub Actions (`build.yml` e `ci.yml`) para compilação automatizada do APK via GitHub CLI.
- **Evidência**: Testes unitários do motor Python rodaram com sucesso (`Ran 4 tests in 0.002s, OK`).
