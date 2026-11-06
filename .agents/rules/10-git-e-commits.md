---
trigger: model_decision
description: "Aplique ao criar ramos, fazer commits, abrir PRs, mesclar, marcar tags, configurar Git/GitHub ou mexer em histórico."
---
# Git: ramos, commits e PRs

## Modelo de ramos (GitHub Flow, com ramos curtos)
- `main`: sempre funcional e publicável. Protegida (PR obrigatório, sem force push).
- Ramos de tarefa, vida curta (horas a poucos dias): `tipo/NN-descricao-curta`, em minúsculas e com hífens. `NN` = número da issue quando existir. Ex.: `feat/12-login-oauth`, `fix/engine-boot-tela-preta`, `docs/readme-instalacao`.
- Tipos: `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `hotfix`.
- `release/X.Y.Z` só se precisar estabilizar uma versão enquanto o desenvolvimento continua. `hotfix/...` sai da tag em produção.
- Agentes em paralelo: um ramo por agente e, de preferência, uma pasta própria: `git worktree add ../<projeto>-<ramo> <ramo>`.

## Commits (Conventional Commits 1.0.0)
```
tipo(escopo)!: descrição curta no imperativo (até 72 caracteres)

Corpo (opcional): explique o PORQUÊ e o contexto, não repita o diff.

Refs: #12
BREAKING CHANGE: o que quebra e como migrar
Assisted-by: <agente/modelo>
```
- Idioma: tipo, escopo, descrição e corpo do commit em inglês (a documentação do projeto continua em português), salvo ADR-0001 do projeto em contrário.
- Tipos e efeito na versão: `feat` → MINOR · `fix` → PATCH · `!` ou `BREAKING CHANGE` → MAJOR · `docs, style, refactor, perf, test, build, ci, chore` → nenhum bump por si só (`perf` pode ser PATCH).
- Escopo = módulo/área (`auth`, `db`, `ui`, `rootfs`, `kernel`). Use `!` ao lado do tipo para sinalizar quebra.
- Atômico: um commit = uma mudança lógica que compila e passa nos testes. Não misture formatação com lógica.
- Proibido: mensagens como "ajustes", "update", "fix bug", "wip" na `main`. Em ramo de tarefa, `wip` é aceitável e deve ser limpo antes do PR (`git commit --fixup <sha>` e `git rebase -i --autosquash origin/main`, só em ramo ainda não publicado ou com `--force-with-lease` e aviso).
- Não commitar: segredos, `.env`, binários grandes, builds, `node_modules`, dumps de banco, imagens de disco.

## Pull Requests
- Pequeno (ideal < 400 linhas), uma intenção, título no formato Conventional Commits, descrição com: o quê, por quê, como testar, evidências, riscos, issue (`Closes #N`).
- Merge: **squash**, para a `main` ficar linear e cada PR virar um commit Conventional. O título do PR vira a mensagem.
- Apague o ramo depois do merge.

## Tags e releases
- Tag anotada, só na `main`: `git tag -a vX.Y.Z -m "vX.Y.Z"`; publicada com `git push origin vX.Y.Z`. Nunca mover nem apagar tag publicada: lance uma versão nova.

## Configuração mínima
```
git config user.name "Seu Nome"
git config user.email "ID+usuario@users.noreply.github.com"
git config pull.rebase true
git config init.defaultBranch main
git config core.hooksPath scripts/hooks
git config commit.template .gitmessage
```

## Histórico e segurança
- Nunca `push --force`; se for inevitável, `--force-with-lease`, com backup (`git bundle create ../backup.bundle --all`) e confirmação do usuário.
- Segredo commitado: considere vazado. Revogue/rotacione a chave PRIMEIRO; depois limpe o histórico (skill `repositorio-github`).
- Passo a passo para o usuário: sempre o comando completo e o que deve aparecer.
