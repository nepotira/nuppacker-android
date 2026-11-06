---
trigger: model_decision
description: "Aplique ao adicionar dependência, copiar ou adaptar código/arte/fonte, usar arquivo de terceiros, fazer fork, publicar ou distribuir binários/imagens/ROMs, escolher licença ou tratar dados pessoais."
---
# Licenças e direitos (versão curta; detalhe na skill `licencas-e-direitos`)

1. Antes de adicionar dependência ou copiar algo: ache a licença (SPDX), confira compatibilidade com a licença do projeto (`LICENSE`) e registre em `THIRD_PARTY_LICENSES.md`. Sem licença = não usar.
2. Código novo leva cabeçalho SPDX e segue a licença do projeto. Não remova avisos de copyright de ninguém.
3. GPL/AGPL em projeto permissivo ou fechado: pare e pergunte ao usuário. Apache-2.0 não combina com GPL-2.0-only.
4. Não versionar nem publicar ROM, firmware, driver, instalador, fonte, ícone ou imagem proprietários; usar script de download oficial + checksum + créditos.
5. Distribuir binário/imagem com componentes GPL (kernel, rootfs): publicar o código-fonte correspondente e os avisos de licença junto da Release.
6. Fork: manter LICENSE e atribuição, remoto `upstream`, README "Fork de ...", alterações em ramos, avisar mudanças quando a licença exigir.
7. Contribuições: DCO (`git commit -s`). Combinar titularidade por escrito com colaboradores/freelancers.
8. Dados pessoais (LGPD): minimizar, informar finalidade, proteger; nunca no repositório.
9. Marcas de terceiros: não usar em nome/logo do projeto; declarar "não afiliado".
10. Dúvida jurídica relevante: dizer que não é aconselhamento jurídico e recomendar advogado.
