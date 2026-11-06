# ADR 0003 — Licenciamento sob GNU AGPL v3
- Status: Aceita
- Data da decisão: 2026-11-06 · Registrado em: 2026-11-06 · Decisores: nepo

## Contexto
O software base `PastaLaPate/NUPPacker` é distribuído sob os termos da licença GNU Affero General Public License v3 (AGPL-3.0). O projeto NUPPacker Android reutiliza diretamente a base de código do empacotador.

## Opções consideradas
1. Licença permissiva (MIT ou Apache-2.0): incompatível com a natureza copyleft forte da AGPL-3.0 do código derivado.
2. GPL-3.0: compatível em muitos casos, mas diverge do texto exato do upstream.
3. AGPL-3.0: licença idêntica à do repositório upstream, garantindo total conformidade legal, preservação dos direitos autorais de terceiros e reciprocidade aberta da comunidade.

## Decisão
Adotamos formalmente a licença **GNU AGPL-3.0-only** para o projeto NUPPacker Android.

## Consequências
- Positivas: Plena conformidade legal com o projeto original; proteção contra apropriação fechada de código derivado.
- Negativas: Exige que todo binário público distribuído tenha seu código-fonte correspondente aberto e disponível.
