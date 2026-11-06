---
trigger: model_decision
description: "Aplique ao escrever ou revisar código, HTML/CSS, SQL, scripts, nomes de arquivos, estrutura de pastas, testes e tratamento de erros."
---
# Desenvolvimento semântico: o código diz o que significa

## Nomes
- Nome revela intenção: `calcularFreteComDesconto`, não `calc2`. Booleanos como pergunta (`estaAtivo`, `temPermissao`). Sem abreviações obscuras. Um conceito = uma palavra no projeto inteiro (veja `docs/GLOSSARY.md`).
- Arquivos/pastas por função e domínio (`auth/`, `espacos/`), não por tipo genérico (`utils2/`). Estilo único por linguagem (snake_case, camelCase, kebab-case em URLs/arquivos web).
- Constantes nomeadas no lugar de números/textos mágicos. Configuração por variável de ambiente, nunca fixa no código.

## Estrutura
- Funções pequenas e com uma responsabilidade; entrada/saída claras; sem efeitos escondidos. Separar camadas (apresentação, regra de negócio, dados).
- Comentário explica o PORQUÊ (e links para ADR/issue), não o óbvio. Código morto se apaga (o Git guarda).
- Erros: nunca engolir. Mensagem útil, causa e contexto; sem vazar segredo/stack para o usuário final. Log com nível (info/warn/error).
- Dependências: mínimas, fixadas por versão, com motivo registrado. Licença compatível.

## HTML/CSS semântico e acessível
- Estrutura: `header`, `nav`, `main` (um por página), `section`, `article`, `aside`, `footer`; títulos `h1`→`h6` em ordem lógica; listas para listas; `button` para ação e `a` para navegação (nunca `div` clicável).
- Formulários: `label` ligado ao campo, `fieldset/legend`, tipos corretos (`email`, `tel`, `date`), mensagens de erro associadas. Imagens com `alt` útil (vazio se decorativa).
- ARIA só quando o HTML nativo não resolve. Contraste mínimo WCAG AA, foco visível, navegação por teclado, alvos de toque confortáveis (mobile-first).
- CSS: classes por função (`.cartao-espaco`), variáveis (design tokens) para cor/espaço/tipografia, sem `!important` de rotina.

## Banco de dados
- Migrações numeradas e imutáveis depois de aplicadas: `NNN_descricao.sql`. Nomes no singular/plural de forma consistente, chaves estrangeiras e índices explícitos, consultas parametrizadas (nunca concatenar entrada do usuário).
- Esquema documentado em `docs/ARCHITECTURE.md` (diagrama ER em Mermaid).

## Testes e verificação
- Teste conta a história: `deve_<resultado>_quando_<condição>`. Regra de negócio crítica tem teste antes do conserto de bug (reproduza o erro primeiro).
- Para UI, verifique rodando e olhando (captura/preview) e registre a evidência. Para sistema/dispositivo, use checklist com resultado esperado e observado.
- Linters e formatadores configurados e rodando no CI.

## Mudanças semânticas no Git
- A mensagem do commit e o número da versão descrevem o EFEITO da mudança (veja regras 10 e 20). Refatoração não muda comportamento; se mudar, não é refatoração.
