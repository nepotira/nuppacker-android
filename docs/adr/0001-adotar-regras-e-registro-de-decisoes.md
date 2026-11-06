# ADR 0001 — Adoção de regras, governança e registro de decisões
- Status: Aceita
- Data da decisão: 2026-11-06 · Registrado em: 2026-11-06 · Decisores: nepo

## Contexto
O projeto NUPPacker Android foi iniciado para fornecer uma solução móvel que permita empacotar dumps de Nintendo Switch 1/2 no formato `.nup`. Para assegurar qualidade, reprodutibilidade, governança com SemVer 2.0.0 e histórico limpo de commits, é necessário um padrão de ciclo de vida.

## Opções consideradas
1. Desenvolvimento ad-hoc sem padronização documental.
2. Adoção da Constituição de Desenvolvimento (pacote antigravity-dev-rules v1.1.0) com SemVer, Conventional Commits, Keep a Changelog e registro obrigatório em ADRs.

## Decisão
Adotamos o pacote antigravity-dev-rules v1.1.0 e as regras pessoais do usuário:
- Conventional Commits em inglês.
- SemVer 2.0.0 iniciando em 0.1.0.
- Mudanças por branches de tarefas, integradas via Pull Request.
- Documentação viva (`docs/` em português).
- Compilação do APK priorizando GitHub Actions com `gh`.

## Consequências
- Positivas: rastreabilidade completa, facilidade para alternar agentes sem perda de contexto, automação de releases.
- Negativas: pequena sobrecarga na documentação de cada etapa (mitigada por templates e disciplina).
