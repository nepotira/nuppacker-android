# Requisitos — NUPPacker Android

## Contexto e objetivo
Fornecer um aplicativo Android com interface tátil nativa que empacota dumps descompactados de jogos de Nintendo Switch 1 e 2 no formato de contêiner `.nup`, mantendo a lógica de empacotamento comprovada do projeto Python original `PastaLaPate/NUPPacker`.

## Partes interessadas e usuários
- **Usuários de emuladores no Android** (ex.: Eden, Ryujinx/Ryubing, forks): precisam converter e organizar dumps de jogos, atualizações e DLCs em um único contêiner limpo `.nup`.
- **Mantenedores e desenvolvedores**: precisam de código estável, rastreável e fiel ao formato NUP v1-draft.

## Requisitos funcionais (RF) — MoSCoW
| ID | Descrição | Prioridade | Critério de aceite (Dado/Quando/Então) | Teste |
|---|---|---|---|---|
| RF-001 | Selecionar diretório de entrada | Must | Dado um dump com `base/`, `updates/`, `dlc/` ou pasta com `exefs`/`romfs`, Quando o usuário seleciona a pasta, Então os pacotes contidos são descobertos e listados. | Teste de descoberta |
| RF-002 | Detecção automática de Title ID | Should | Dado um pacote contendo `exefs/main.npdm`, Quando o usuário seleciona a pasta ou clica em auto-detectar, Então o Title ID em hexadecimal é extraído e preenchido. | Teste de NPDM |
| RF-003 | Configuração de metadados do NUP | Must | Dado os campos de Nome, Versão, Versão de Update, Versão de Display, Ícone e Títulos localizados, Quando preenchidos, Então são gravados como blocos TLV válidos na tabela de conteúdos. | Validação TOC |
| RF-004 | Ajuste de compressão Zstandard | Must | Dado o seletor de nível (0 = raw até 22), Quando o usuário seleciona o nível, Então os arquivos são comprimidos via `zstandard` correspondente. | Teste de compressão |
| RF-005 | Empacotamento idêntico ao upstream | Must | Dado um conjunto de arquivos, Quando o build é iniciado, Então executa a função de packing do Python original, gerando superblock de 0x80 bytes, TOC com hash SHA-256 e blob alinhado a 16 bytes com deduplicação. | Teste de paridade NUP |
| RF-006 | Feedback em tempo real | Must | Durante o processo de build, a interface exibe barra de progresso (0-100%) e console de logs em tempo real sem travar a UI. | Teste de UI |
| RF-007 | Permissões de armazenamento no Android | Must | No Android 11+, o app solicita acesso a todos os arquivos (`MANAGE_EXTERNAL_STORAGE`) para permitir leitura direta via caminho POSIX. | Teste de permissão |

## Requisitos não funcionais (RNF)
- **RNF-001 (Compatibilidade)**: Android 8.0 (API 26) até Android 15 (API 35).
- **RNF-002 (Execução em Background)**: Utilização de serviço de primeiro plano (Foreground Service) ou Wakelock para evitar que o Android mate o processo durante a escrita de arquivos de vários gigabytes.
- **RNF-003 (Privacidade / Offline)**: 100% offline, sem permissões de rede (`android.permission.INTERNET` ausente no app final) e sem coleta de dados.
- **RNF-004 (Compilação automatizada)**: Workflow do GitHub Actions para compilação do APK via GitHub CLI (`gh`).

## Fora do escopo
- Descriptografia de NCAs ou extração de jogos a partir de cartuchos diretamente no app (o usuário já deve ter os arquivos descriptografados).
- Execução/emulação de jogos no app.

## Riscos e premissas
- **Premissa**: Dispositivos Android possuem espaço de armazenamento livre suficiente para o arquivo `.nup` resultante.
- **Risco**: Limitações do sistema operacional Android ao escrever arquivos maiores que 4 GB caso o sistema de arquivos seja FAT32 (SD cards antigos); mitigado alertando o usuário.
