# Ambiente de desenvolvimento
Atualizado em: 2026-11-06

## Plataforma
- Sistema/versão/arquitetura: Linux x86_64
- Terminal/shell: bash

## Ferramentas locais disponíveis
| Ferramenta | Versão / Caminho | Status |
|---|---|---|
| git | /usr/bin/git | Ativo |
| gh | /home/nepo/.local/bin/gh | Ativo e autenticado (`nepotira`) |
| python3 | /usr/bin/python3 | Ativo |
| adb | /usr/bin/adb | Ativo |

## Autenticações (apenas status)
- gh: Logado como `nepotira` (escopos: `repo`, `workflow`, `gist`, `read:org`)
- git user: `nepo` (`nepotira@users.noreply.github.com`)

## Pipeline de compilação
- Compilação Android via GitHub Actions (`ubuntu-latest`, JDK 17, Android SDK)
