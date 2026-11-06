# ADR 0002 — Stack técnica Android e reaproveitamento do motor Python original
- Status: Aceita
- Data da decisão: 2026-11-06 · Registrado em: 2026-11-06 · Decisores: nepo

## Contexto
O projeto upstream `PastaLaPate/NUPPacker` foi implementado em Python com interface desktop em PySide6. A especificação NUP requer leitura de árvores de diretórios, cálculo incremental de hashes SHA-256, deduplicação de extents, empacotamento binário de estruturas (`Superblock`, `TOC`, `NupPackage`, `NupDir`, `NupFile`) e compressão via `zstandard`.
O usuário solicitou explicitamente: "Mantenha o que já pode ser executado do código original no Android o máximo que conseguir para não mudarmos muito o que já funciona nele originalmente."

## Opções consideradas
1. **Reescrita integral em Kotlin/Java nativo**:
   - Prós: APK 100% JVM sem runtime Python.
   - Contras: Viola o requisito de manter o que já funciona no original; risco de divergência no empacotamento binário ou suporte a zstd.
2. **PySide6 / Kivy no Android**:
   - Prós: 100% Python na interface.
   - Contras: PySide6 para Android possui suporte precário, tamanho excessivo, péssima usabilidade tátil e integração deficiente com permissões de armazenamento do Android 11+.
3. **Arquitetura Híbrida: Interface Nativa em Kotlin/Jetpack Compose + Motor Python original via Chaquopy**:
   - Prós: Mantém a exata lógica de empacotamento (`build_nup`, `scan`, `discover`, `app_meta`) do `PastaLaPate/NUPPacker` sem reescrever ou divergir; interface nativa moderna em Material 3 para Android com integração total a permissões e background jobs; CLI Python utilizável também no Termux.
   - Contras: Adiciona o runtime Chaquopy no build Gradle.

## Decisão
Adotamos a **Opção 3**:
- **Interface e Sistema**: Kotlin + Jetpack Compose (Material 3), com gerenciamento de permissões de armazenamento (`MANAGE_EXTERNAL_STORAGE` para leitura rápida de pastas de jogos sem gargalo de SAF) e execução em corrotinas/serviço em segundo plano.
- **Motor de Empacotamento**: O código Python original do `nuppacker` é preservado e executado diretamente via Chaquopy no Android, além de prover um módulo executável no Termux/CLI.
- **Compressão**: Biblioteca nativa `zstandard` via pip/wheel do Chaquopy.

## Consequências
- Positivas: Máxima fidelidade ao código original que já funciona; facilidade de manutenção e sincronização com melhorias do repositório de origem; interface Android limpa e ágil.
- Negativas: Requer download do runtime Python e zstd durante a compilação do APK no GitHub Actions.
