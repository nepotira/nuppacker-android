# Como contribuir
1. Abra uma issue descrevendo o problema/ideia.
2. Crie um ramo: `git switch -c tipo/NN-descricao-curta` (em fork: `gh repo fork --clone` primeiro).
3. Commits no padrão Conventional Commits e **com assinatura de origem (DCO)**: `git commit -s -m "feat(ui): adiciona filtro por esporte"`.
4. Rode os testes (veja AGENTS.md > Comandos).
5. Abra o PR com `gh pr create --fill` e preencha o modelo.

## Direitos e licença
Ao contribuir, você declara que tem o direito de enviar o código (Developer Certificate of Origin, https://developercertificate.org) e concorda que sua contribuição seja licenciada sob a licença do projeto (veja `LICENSE`). Não envie código copiado de fontes sem licença compatível. Detalhes: `docs/LICENSING.md`.
Regras completas: `.agents/rules/10-git-e-commits.md`.
