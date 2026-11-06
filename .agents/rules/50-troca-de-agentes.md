---
trigger: model_decision
description: "Aplique ao trocar de agente/modelo, retomar trabalho de outra sessão, trabalhar em paralelo com outro agente ou economizar tokens."
---
# Troca de agentes e continuidade

Cada agente chega "sem memória". O repositório é a memória.

## Ao sair (ou quando o usuário disser que vai trocar)
1. Rode `/handoff` (skill `troca-de-agentes`). Estado vai para `docs/PROJECT_STATE.md`, diário para `docs/JOURNAL.md`, bastão para `docs/handoffs/`.
2. Nada de trabalho só no chat: commit em ramo de tarefa ou `git stash push -u -m "handoff ..."`, e `git push`.

## Ao chegar
1. Rode `/retomar`. Leia o handoff, depois VERIFIQUE: rode testes/build; não confie cegamente em "está funcionando".
2. Se o handoff e o repositório divergirem, o repositório vence; registre a divergência no JOURNAL.

## Convivência
- Um agente por ramo. Dois agentes nunca editam os mesmos arquivos ao mesmo tempo; use `git worktree` ou revezamento.
- Identifique-se nos commits: `Assisted-by: <agente/modelo>` e nas entradas do JOURNAL.
- Divisão de trabalho sugerida (ajuste ao seu caso): agente mais leve/barato para tarefas mecânicas e bem definidas (renomear, formatar, gerar docs a partir de especificação, rodar checklist); agente mais forte para arquitetura, depuração difícil, revisão e decisões. Quem delega escreve a tarefa com objetivo, arquivos, critério de aceite e comando de verificação.

## Economia de tokens
- Leia `PROJECT_STATE.md` e o handoff, não o repositório inteiro. Abra arquivos sob demanda. Mantenha `AGENTS.md` curto e as regras detalhadas em `.agents/rules/` (carregam só quando preciso).
- Prefira edições pequenas e pontuais; não reescreva arquivos inteiros sem necessidade.
- Plano curto aprovado antes de executar evita retrabalho (o maior desperdício são ciclos de tentativa e erro).
- Registre a causa raiz de cada problema resolvido em `TROUBLESHOOTING.md`: o próximo agente não paga duas vezes.
