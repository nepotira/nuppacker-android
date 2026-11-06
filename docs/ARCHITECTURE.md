# Arquitetura — NUPPacker Android

## 1. Contexto (C4 Nível 1)

O NUPPacker Android opera inteiramente no dispositivo do usuário, lendo pastas de dumps de jogos descompactados e gerando um contêiner `.nup` otimizado para emuladores.

```mermaid
flowchart TD
    User["Usuário"] --> UI["NUPPacker Android (App)"]
    UI --> Storage["Armazenamento Local (/sdcard/)"]
    Storage --> NupFile["Arquivo .nup gerado"]
```

## 2. Contêineres e Componentes (C4 Níveis 2 e 3)

```mermaid
flowchart TD
    subgraph AndroidApp["Aplicativo Android (APK)"]
        subgraph UILayer["Camada de Interface (Jetpack Compose)"]
            MainWindow["MainWindow UI"]
            FolderPicker["Seletores de Diretório"]
            LogConsole["Console de Logs e Progresso"]
        end

        subgraph ServiceLayer["Camada de Serviço e Controle"]
            PackerService["PackerService (Foreground Service / Coroutines)"]
            Bridge["Chaquopy Python Bridge"]
        end

        subgraph PythonCore["Motor Python Preservado (nuppacker)"]
            Discover["discover() / guess_title_id()"]
            Plan["plan_packages()"]
            Builder["build_nup()"]
            Zstd["libzstandard (C-extension)"]
        end
    end

    UILayer --> ServiceLayer
    ServiceLayer --> Bridge
    Bridge --> PythonCore
    PythonCore --> Storage["Filesystem (/storage/emulated/0)"]
```

## 3. Modelo do Formato NUP (Binário)

```mermaid
erDiagram
    SUPERBLOCK ||--|| TOC : aponta
    TOC ||--|{ PACKAGE_RECORD : lista
    TOC ||--|{ DIR_RECORD : lista
    TOC ||--|{ FILE_RECORD : lista
    FILE_RECORD ||--|| EXTENT : "referencia no BLOB"
    SUPERBLOCK {
        byte4 magic "NUP\\x01"
        uint16 header_size "0x80"
        uint16 header_version "1"
        uint64 toc_offset "0x80"
        uint64 toc_size "bytes"
        byte32 toc_sha256 "SHA-256 da TOC"
        uint64 blob_offset "alinhado a 16 bytes"
        uint64 file_size "tamanho total"
        uint32 package_count "quantidade de pacotes"
    }
```

## 4. Fluxo de Empacotamento (Sequência)

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant UI as Interface Compose
    participant Svc as PackerService
    participant Py as Python nuppacker
    participant FS as Sistema de Arquivos

    U->>UI: Seleciona pasta do dump e clica "Build NUP"
    UI->>Svc: Dispara empacotamento com parâmetros (cfg)
    Svc->>Py: Invoca build_nup(cfg, log_cb, progress_cb)
    Py->>FS: Varre diretórios (base, updates, dlc)
    Py-->>UI: Callback log ("X dirs, Y files")
    loop Para cada arquivo
        Py->>FS: Lê arquivo, calcula SHA-256 e comprime (zstd)
        Py->>FS: Grava no arquivo .part no BLOB
        Py-->>UI: Callback progress(percentual)
    end
    Py->>FS: Grava Superblock e TOC no início do arquivo
    Py->>FS: Renomeia .part para .nup
    Py-->>Svc: Concluído com sucesso
    Svc-->>UI: Notifica conclusão
    UI-->>U: Exibe "Done"
```

## 5. Decisões Arquiteturais
- [ADR 0001: Adoção de regras e governança](file:///home/nepo/Downloads/NUP/docs/adr/0001-adotar-regras-e-registro-de-decisoes.md)
- [ADR 0002: Stack Android e reaproveitamento do motor Python original](file:///home/nepo/Downloads/NUP/docs/adr/0002-stack-android-e-reaproveitamento-python.md)
- [ADR 0003: Licenciamento sob GNU AGPL v3](file:///home/nepo/Downloads/NUP/docs/adr/0003-licenciamento-agpl-3.md)

## 6. Pipeline de Construção (CI)
A compilação do APK ocorre primariamente no **GitHub Actions** via GitHub CLI (`gh`), utilizando o runner `ubuntu-latest`, JDK 17, Android SDK com CMake/NDK e Gradle Wrapper.
