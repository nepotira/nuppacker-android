---
trigger: model_decision
description: "Aplique ao decidir número de versão, atualizar CHANGELOG, criar tag/release, falar de compatibilidade ou quebrar API."
---
# Versionamento semântico (SemVer 2.0.0) e Changelog

## Formato
`MAJOR.MINOR.PATCH[-prerelease][+build]` — ex.: `1.4.2`, `2.0.0-rc.1`, `0.3.0`.
- MAJOR: mudança incompatível (API, CLI, formato de dados, requisito mínimo, esquema de banco sem migração automática).
- MINOR: funcionalidade nova compatível.
- PATCH: correção compatível.
- `0.y.z` = desenvolvimento inicial: tudo pode mudar; quebra incompatível sobe o MINOR. Vá para `1.0.0` quando o uso público e a API estiverem estáveis, ou quando houver gente dependendo do projeto.
- Pré-lançamentos: `-alpha.N` → `-beta.N` → `-rc.N`. Metadados de build após `+` não contam na precedência.
- Versão lançada é imutável. Erro? Lance a próxima.

## Como decidir
Pegue os commits desde a última tag (`git log vX.Y.Z..HEAD --oneline`): qualquer `BREAKING CHANGE`/`!` → MAJOR (ou MINOR em 0.y.z); senão qualquer `feat` → MINOR; senão `fix`/`perf` → PATCH; só `docs/chore/test/ci` → normalmente não lança.

## Fonte única da versão
Um único arquivo manda (`package.json`, `pyproject.toml`, `VERSION`, `Cargo.toml`, constante em código). Todo o resto lê dele. A tag `vX.Y.Z` deve bater com ele.

## CHANGELOG.md (Keep a Changelog 1.1.0)
- Para humanos, não é despejo de `git log`. Mais novo no topo. Seção `## [Unreleased]` sempre existe.
- Categorias: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`.
- Cada versão: `## [X.Y.Z] - AAAA-MM-DD` e links de comparação no rodapé: `[X.Y.Z]: https://github.com/<dono>/<repo>/compare/vANTERIOR...vX.Y.Z`.
- Quebras de compatibilidade sempre em destaque, com instrução de migração.
- Depreciar antes de remover: marque `Deprecated` em uma MINOR e remova só na MAJOR seguinte.

## Quando SemVer não é o melhor
- Artefatos de build periódicos (imagens de sistema, rootfs, ROMs, builds noturnos): pode usar CalVer `AAAA.MM.DD[.N]` ou carimbo de data/hora. Registre a escolha em ADR e mantenha SemVer para o projeto/scripts que geram o artefato.
- Projetos de documentação/guia (ex.: passo a passo de instalação de um sistema): MAJOR = método mudou, MINOR = passos/seções novas, PATCH = correção de texto/comando.

## Release (resumo; detalhes na skill `release-semver`)
Changelog fechado → versão no arquivo único → commit `chore(release): vX.Y.Z` → tag anotada → push → `gh release create` com as notas.
