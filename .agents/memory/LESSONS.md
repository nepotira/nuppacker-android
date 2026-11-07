# Lessons Learned (Lições Aprendidas) — nuppacker-android

## 2026-11-07 — Padrão Mandatório de Repositórios e Releases em Inglês com Espelho em Português
- **Contexto:** Durante o ciclo de desenvolvimento de `nuppacker-android`, as notas de releases no GitHub foram geradas com títulos e corpos exclusivamente em português, e a raiz do repositório continha apenas `README.md` sem o seletor de idiomas no topo e sem o espelho `README.pt-BR.md`.
- **Correção do Usuário:** Todos os repositórios devem ser construídos em inglês como idioma principal, com um espelho em português caso a pessoa queira ler em português (`README.pt-BR.md`), com o seletor `[English](README.md) | [Português do Brasil](README.pt-BR.md)` obrigatoriamente no topo. Releases no GitHub devem ter título e corpo principal em inglês, com a versão em português como complemento opcional ao final.
- **Ações Imediatas Aplicadas:**
  1. Criação da pasta `.agents/memory/` com `LESSONS.md`, `STATE.md` e `HANDOFF.md`.
  2. Atualização do `README.md` com cabeçalho bilíngue de seleção de idioma, badges atualizados da `v0.5.2` e arquitetura nativa em Rust.
  3. Criação do arquivo espelho `README.pt-BR.md` com tradução integral e fidedigna.
  4. Auditoria e atualização retroativa de todas as releases publicadas no GitHub (`v0.5.2`, `v0.5.1`, `v0.5.0`, `v0.4.2`, etc.) via `gh release edit`, convertendo títulos e notas para inglês primário com seção em português.
