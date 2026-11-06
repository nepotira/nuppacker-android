---
trigger: model_decision
description: "Aplique ao criar ou alterar código, configuração, decisões, instalações, correções ou quando precisar registrar o que foi feito; também ao escrever README, ADR, diário ou docs."
---
# Documentação profissional contínua ("documentar ao fazer")

Documentar não é etapa final: acontece no mesmo ciclo da mudança. Nada fica só na conversa do chat; o chat some, o repositório fica.

## Conjunto mínimo (criar se faltar, manter vivo)
| Arquivo | Para quê | Quando atualizar |
|---|---|---|
| `README.md` | O que é, para quem, como instalar/usar em 5 minutos, status, licença | Mudou instalação/uso/status |
| `CHANGELOG.md` | Histórico para humanos por versão | Toda mudança visível |
| `docs/PROJECT_STATE.md` | Painel vivo: objetivo, estado atual, o que funciona/não funciona, próximos passos, bloqueios | Fim de toda sessão |
| `docs/JOURNAL.md` | Diário cronológico completo do processo (tentativas, erros, causas, soluções) | A cada ação relevante |
| `docs/REQUIREMENTS.md` | Requisitos e critérios de aceite | Escopo mudou |
| `docs/ARCHITECTURE.md` | Visão C4, dados, integrações (diagramas Mermaid) | Estrutura mudou |
| `docs/adr/NNNN-titulo.md` | Decisões e alternativas descartadas | Decisão relevante |
| `docs/RUNBOOK.md` | Instalar, rodar, atualizar, reverter, restaurar backup | Procedimento mudou |
| `docs/TESTING.md` | Estratégia, comandos, matriz do que foi testado | Testes mudaram |
| `docs/TROUBLESHOOTING.md` | Sintoma → causa → solução | Todo problema resolvido |
| `docs/GLOSSARY.md` | Termos e siglas | Termo novo |
| `docs/handoffs/` | Passagem de bastão entre agentes | Troca de agente/pausa longa |
| `CONTRIBUTING.md`, `SECURITY.md`, `LICENSE` | Colaboração, segurança, direitos | Raramente |

## Entrada do JOURNAL (modelo)
```
### AAAA-MM-DD HH:MM (fuso) — <agente/modelo> — <título>
- Objetivo: ...
- O que foi feito: ... (comandos/arquivos)
- Resultado e evidência: ... (saída, log, captura)
- Problemas e causa raiz: ...
- Decisões: ... (link do ADR)
- Não verificado: ...
- Próximo passo: ...
```
Hora real: `date -Iseconds`. Se a IA não souber a hora, escreva "hora desconhecida", nunca invente.

## Regras de escrita
- Português do Brasil claro; frases curtas; defina siglas na primeira vez.
- Comandos completos e copiáveis em blocos de código, com o resultado esperado.
- Diagramas em Mermaid (renderiza no GitHub). Links relativos entre documentos.
- Problema resolvido = entrada em TROUBLESHOOTING no mesmo dia (sintoma exato da mensagem de erro, causa, solução, como evitar).
- Decisão = ADR (contexto, opções, escolha, consequências, status: Proposta/Aceita/Substituída). Nunca editar ADR aceito: crie outro que o substitua.
- Projetos que não são só código (ROM, SO, hardware): mantenha `docs/COMPATIBILIDADE.md` com matriz "funciona / não funciona / não testado" e a data/versão testada, e registre backups e o caminho de volta (rollback).
- Registros retroativos: veja a regra 8 da Constituição (data real + "reconstruído em" + nível de confiança + evidência).
