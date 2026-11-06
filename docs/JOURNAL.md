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
