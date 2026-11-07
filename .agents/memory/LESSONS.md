# Lessons Learned (Lições Aprendidas) — nuppacker-android

## 2026-11-07 — Padrão Mandatório de Repositórios e Releases em Inglês com Espelho em Português
- **Contexto:** Durante o ciclo de desenvolvimento de `nuppacker-android`, as notas de releases no GitHub foram geradas com títulos e corpos exclusivamente em português, e a raiz do repositório continha apenas `README.md` sem o seletor de idiomas no topo e sem o espelho `README.pt-BR.md`.
- **Correção do Usuário:** Todos os repositórios devem ser construídos em inglês como idioma principal, com um espelho em português caso a pessoa queira ler em português (`README.pt-BR.md`), com o seletor `[English](README.md) | [Português do Brasil](README.pt-BR.md)` obrigatoriamente no topo. Releases no GitHub devem ter título e corpo principal em inglês, com a versão em português como complemento opcional ao final.
- **Ações Imediatas Aplicadas:**
  1. Criação da pasta `.agents/memory/` com `LESSONS.md`, `STATE.md` e `HANDOFF.md`.
  2. Atualização do `README.md` com cabeçalho bilíngue de seleção de idioma, badges atualizados da `v0.5.2` e arquitetura nativa em Rust.
  3. Criação do arquivo espelho `README.pt-BR.md` com tradução integral e fidedigna.
  4. Auditoria e atualização retroativa de todas as releases publicadas no GitHub (`v0.5.2`, `v0.5.1`, `v0.5.0`, `v0.4.2`, etc.) via `gh release edit`, convertendo títulos e notas para inglês primário com seção em português.

## 2026-11-07 — Incompatibilidade de NUP Gerado pelo Motor Rust em Emuladores de Switch 2
- **Problema de Raiz Identificado:** O contêiner `.nup` gerado pelo motor nativo Rust a partir da versão v0.4.0 não era reconhecido pelos emuladores de Switch 2 (diferente da versão v0.1.1 original em Python que funcionava).
- **Causas Técnicas Comprovadas no Código:**
  1. No `packer.rs` (linha 429), o campo reservado `res2` do registro PKG no TOC foi serializado como `0u64` (8 bytes) em vez de `0u32` (4 bytes). Isso fez cada registro de pacote ter 68 bytes em vez dos 64 bytes (`0x40`) da especificação NUP, desfasando em 4 bytes todos os registros de diretórios, arquivos e string pool subsequentes no TOC.
  2. No `tree.rs`, a função `collect()` clonava o diretório para a lista plana antes de indexar os subdiretórios e arquivos. Isso fez todos os ponteiros de subdiretórios e arquivos no array `children` apontarem para o índice 0 (gerando recursão circular e arquivos apontando para o arquivo 0).
  3. Desvio heurístico em `v0.5.0` (`is_incompressible_ext` / `fast_entropy_probe`) marcando arquivos como RAW sem tentar compressão Zstd, diferindo da política de referência da especificação NUP.
  4. Testes unitários Rust anteriores apenas checavam os primeiros 24 bytes do Superblock, sem inspecionar nem validar a integridade estrutural do TOC.
- **Ações Mandatórias:**
  1. Corrigir a serialização do PKG record em `packer.rs` para gravar estritamente `0u32` (64 bytes).
  2. Corrigir a indexação em `tree.rs` com algoritmo de dois passos (indexação in-place antes de clonar), garantindo navegação válida de diretórios e arquivos.
  3. Alinhar a política de compressão Zstd com a especificação de referência NUP.
  4. Implementar testes unitários completos de TOC no Rust (`test_toc_parsing_and_navigation`) que validem cada offset e a navegação `base -> exefs -> main.npdm`.
  5. Adicionar seletor de motor (Rust Nativo vs Python Original de Referência) na UI do Android.
