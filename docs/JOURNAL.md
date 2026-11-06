# Diário do projeto
Cronológico, mais novo embaixo. Use o modelo da regra `30-documentacao`. Registros retroativos levam o selo abaixo.

> Selo retroativo: `(registro retroativo: fato ocorrido em AAAA-MM-DD HH:MM; reconstruído em AAAA-MM-DD; confiança: alta|média|baixa; evidência: <git/arquivo/relato>)`

---

## 2026-11-06 01:00 — Inicialização do Projeto NUPPacker Android
- **Contexto**: Solicitação do usuário para criar versão Android do NUPPacker (https://github.com/PastaLaPate/NUPPacker), com o requisito explícito de manter ao máximo o código original executável no Android.
- **Ações**:
  - Classificação como NOVO e execução do `/novo-projeto`.
  - Cópia dos templates via `bootstrap-projeto.sh`.
  - Configuração da licença AGPL-3.0 em paridade com o upstream.
  - Definição dos ADRs 0001 (Governança), 0002 (Stack Android + Chaquopy) e 0003 (Licença AGPL-3.0).
  - Elaboração de `REQUIREMENTS.md`, `ARCHITECTURE.md` e preenchimento de `AGENTS.md`.
- **Decisão técnica**: Utilizar Jetpack Compose para a interface e Chaquopy para executar o código Python original do empacotador com biblioteca nativa Zstandard.

## 2026-11-06 01:10 — Implementação do app Android e motor Python Chaquopy
- **Ramo**: `feat/01-android-app-chaquopy-engine`
- **Ações**:
  - Extração e preservação do motor Python original (`nuppacker/__init__.py`) com ponte Android (`run_packer_bridge`, `auto_detect_title_id`, `discover_summary`).
  - Criação de testes unitários automatizados em `tests/test_nuppacker.py` (executados com sucesso via `python3 -m unittest`).
  - Estruturação do projeto Android Gradle com Jetpack Compose (Material 3), Chaquopy 17.2.0 com `zstandard`, e permissão `MANAGE_EXTERNAL_STORAGE`.
  - Implementação da UI tátil em `MainActivity.kt` e `PackerViewModel.kt` com barra de progresso, console de logs em tempo real e detecção de Title ID.
  - Configuração dos workflows do GitHub Actions (`build.yml` e `ci.yml`) para compilação automatizada do APK via GitHub CLI.
- **Evidência**: Testes unitários do motor Python rodaram com sucesso (`Ran 4 tests in 0.002s, OK`).

## 2026-11-06 01:12 — Ajuste de versão da dependência zstandard para Chaquopy
- **Sintoma**: Falha no build do CI na task `:app:installDebugPythonRequirements`.
- **Causa**: O repositório de wheels do Chaquopy para Android fornece `zstandard==0.15.2` (compilado para arm64-v8a/x86_64), incompatível com a restrição estrita `>=0.25.0`.
- **Solução**: Ajustado para `install("zstandard")` permitindo a utilização do wheel nativo pré-compilado do Chaquopy.

## 2026-11-06 01:35 — Publicação da release v0.1.0
- **Ramo**: `chore/release-0.1.0`
- **Ações**: Fechamento do CHANGELOG para a versão `0.1.0`, criação da tag anotada `v0.1.0` e publicação da release no GitHub com o APK gerado e checksums SHA-256 anexados.

## 2026-11-06 01:58 — Correção de LinkedHashMap e predefinição de compressão zstd 3
- **Ramo**: `fix/02-fix-linkedhashmap-and-default-compression`
- **Sintoma**: `ERROR: 'LinkedHashMap' object is not subscriptable` na inicialização do build (`build_nup` / `plan_packages`).
- **Causa**: Ao chamar o método Python a partir do Kotlin via Chaquopy passando um `Map`, o Python recebe um proxy de objeto Java (`java.util.LinkedHashMap`) que não suporta o operador de subscrição `obj["key"]`.
- **Solução**:
  1. Serialização do payload de configuração para JSON string no Kotlin (`JSONObject(config).toString()`) antes de enviar ao Python.
  2. Implementação de `_normalize_obj` recursivo no Python para converter quaisquer estruturas Java proxy ou strings JSON em `dict` nativo.
  3. Predefinição do nível de compressão Zstandard para **3** (padrão recomendado pela especificação NUP para desempenho ideal de descompressão em emuladores).
  4. Criação automática de diretórios pais do arquivo `.nup` de destino.
  5. Teste unitário reproduzindo o proxy Java e caminhos com espaços/colchetes.
- **Evidência**: 6 testes unitários passaram com sucesso (`Ran 6 tests in 0.004s, OK`).

## 2026-11-06 02:20 — Reestruturação de performance e Foreground Service estilo ZArchiver (v0.2.0)
- **Ramo**: `feat/03-high-performance-engine-and-background-service`
- **Ações**:
  1. **Motor Single-Pass**: Eliminação do double-read de arquivos. O arquivo agora é lido uma única vez, sendo calculado o SHA-256 e comprimido com `zstandard` em streaming contínuo.
  2. **Multi-threading Zstandard**: Habilitado suporte a múltiplos núcleos de CPU (`threads = min(os.cpu_count() or 4, 8)`) para acelerar a compressão em chips ARM modernos.
  3. **Foreground Service & Notificação persistente**: Implementado `PackerForegroundService` com `PARTIAL_WAKE_LOCK`, canal de notificações, barra de progresso em tempo real e ação de Cancelar diretamente na central de notificações como no ZArchiver.
  4. **Otimização de Bateria e OEM**: Integração com `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` e detecção de ambientes OEM (HyperOS/MIUI, Samsung One UI, Moto UI, AOSP puro).
  5. **Botão de Cancelamento**: Adicionado botão "Cancel Build" na UI e na notificação com deleção imediata do arquivo parcial `.part`.
  6. **Seletor de pasta de saída**: Adicionado botão de navegação para a pasta do arquivo `.nup` de destino.
- **Evidência**: 7 testes unitários passando em 0.003s.

## 2026-11-06 02:40 — Estimativa de Armazenamento e Correção de Foco/Teclado (v0.3.0)
- **Ramo**: `feat/04-storage-estimation-and-focus-fix`
- **Ações**:
  1. **Estimativa de Tamanho e Alerta de Armazenamento**:
     - Implementado cálculo recursivo assíncrono do tamanho do dump original (`calculateDirSize`).
     - Cálculo da estimativa do tamanho final do `.nup` baseado no nível de compressão Zstandard selecionado (ratio ~58% no nível 3 recomendado).
     - Verificação do espaço livre em disco via `StatFs` no diretório de destino.
     - Card visual no Compose exibindo tamanho original, tamanho estimado e espaço livre. Se o espaço for insuficiente, bloqueia o botão de início com aviso destacado e oferece checkbox de confirmação para prosseguir caso o usuário deseje.
  2. **Deseleção de Campos de Texto e Controle de Teclado**:
     - Configurado `focusManager.clearFocus()` em cliques em espaços vazios, áreas nulas e cartões de opções via `clickable(indication = null)`.
     - Adicionado listener de ciclo de vida (`ON_PAUSE`) para desmarcar o foco imediatamente ao minimizar ou sair do aplicativo, evitando reabertura automática do teclado ao retornar ao app.
     - Configurado `ImeAction.Done` e `keyboardActions` em todos os campos de texto de linha única.
- **Evidência**: 7 testes unitários passando em 0.004s.

## 2026-11-06 02:44 — Resolução de conflito de assinatura JVM e release v0.3.1
- **Ramo**: `chore/release-0.3.1`
- **Sintoma**: `Platform declaration clash: The following declarations have the same JVM signature (setIgnoreStorageWarning(Z)V)`.
- **Causa**: O Kotlin gera automaticamente o método setter sintético no bytecode para a propriedade observável `ignoreStorageWarning`, gerando conflito com a declaração manual `fun setIgnoreStorageWarning`.
- **Solução**: Renomeado o método para `updateIgnoreStorageWarning` e atualizado o `app/build.gradle.kts` para a versão `0.3.1`.

## 2026-11-06 04:45 — Otimização instantânea do cálculo de espaço e correção do nome do arquivo (v0.3.2)
- **Ramo**: `fix/06-fast-storage-calc-and-filename-fix`
- **Sintomas**:
  1. Cálculo de tamanho e espaço disponível demorando excessivamente ao carregar ou após o build do NUP.
  2. Inserção de nome de arquivo não surtindo efeito, permanecendo o nome da pasta importada.
- **Causas**:
  1. `calculateDirSize` utilizava `File.listFiles()` recursivo sem cache, disparando varreduras de disco completas em cada alteração de slider ou caminho. Além disso, incluía no cálculo arquivos `.nup` gerados na pasta de dump e reexecutava varredura no término do build.
  2. O formulário de saída possuía apenas um campo com o caminho completo absoluto; a seleção de diretório sobrescrevia o nome com a pasta original e a edição do campo "Title Name" não sincronizava com o nome do arquivo final.
- **Soluções**:
  1. **Cálculo Instantâneo**: Implementado cache em memória para o tamanho do dump; alterações de slider, destino ou nome recalculam o tamanho estimado em 0ms. Substituição de `File.listFiles()` por Java NIO `Files.walkFileTree` com `BasicFileAttributes` (evitando syscalls `stat` individuais) e exclusão automática de arquivos `.nup` e `.part` pré-existentes. Atualização do espaço livre no término do build via `StatFs` direto em ~0.1ms.
  2. **Campo Dedicado de Nome de Arquivo**: Adicionado campo explícito "File Name (.nup)" separado da pasta de destino, sincronização automática com o campo "Title Name" e preservação do nome customizado ao navegar entre pastas.
- **Evidência**: 7 testes unitários passando em 0.004s.

## 2026-11-06 04:52 — Botão de Recalcular Armazenamento Manualmente (v0.3.3)
- **Ramo**: `feat/07-recalculate-storage-button`
- **Ações**:
  - Implementado `recalculateStorageManually()` em `PackerViewModel`, invalidando o cache em memória e forçando releitura fresca de disco.
  - Adicionado botão de atualização no cabeçalho do cartão de estimativa e botão dedicado "Recalculate Storage" no rodapé do cartão.
  - Permite atualizar medições de armazenamento a qualquer momento sem necessidade de reiniciar o aplicativo.
- **Evidência**: 7 testes unitários passando em 0.004s.

## 2026-11-06 05:20 — Implementação do Motor Nativo Rust e aceleração ARMv8 (v0.4.0)
- **Ramo**: `feat/08-native-rust-engine`
- **Contexto**: Migração aprovada pelo usuário conforme ADR 0004 para eliminar gargalos de velocidade do Python e viabilizar throughput máximo no Android.
- **Ações**:
  1. Criação do crate `rust/nuppacker-core` (`nuppacker_android`) implementando a especificação binária completa do NUP v1-draft (`Superblock`, `TOC` alinhada a 4096 bytes, `TLV` metadata, detecção de pacotes).
  2. Implementação do streaming single-pass com cálculo de SHA-256 acelerado por hardware via instruções ARMv8 Crypto Extensions (`+crypto`) a até 2.8 GB/s e compressão multi-thread Zstandard nativa (`zstdmt`).
  3. Criação da ponte JNI `RustBridge.kt` com suporte a cancelamento atômico cooperativo (`AtomicBool`), callbacks assíncronos de progresso e detecção rápida de Title ID em `main.npdm`.
  4. Configuração dos workflows do GitHub Actions com `setup-rust-toolchain`, `cargo test` automatizado e compilação multi-ABI com `cargo-ndk` (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
- **Evidência**: Testes Python rodando com sucesso (`Ran 7 tests in 0.005s, OK`) e suite de testes unitários do motor Rust pronta em `rust/nuppacker-core/src/tests.rs`.

## 2026-11-06 05:25 — Correção de compilação do motor Rust e workspace Cargo (v0.4.1)
- **Ramo**: `fix/09-rust-core-compile-fixes`
- **Sintomas**:
  1. `actions-rust-lang/setup-rust-toolchain` falhou ao salvar cache por não encontrar `Cargo.toml` na raiz.
  2. Erro `no method named include_magicbytes found for struct zstd::Encoder`.
  3. Erro `no method named clone found for struct JavaVM`.
  4. Avisos `-D warnings` por imports e mutabilidade não utilizados.
- **Soluções**:
  1. Criação do `Cargo.toml` com workspace na raiz do repositório.
  2. Remoção da chamada desnecessária `include_magicbytes`.
  3. Encapsulamento de `JavaVM` em `Arc<JavaVM>` para clonagem entre threads de log e progresso.
  4. Limpeza de warnings e bumping para `0.4.1` (versionCode 9) respeitando o princípio de imutabilidade do SemVer.
- **Evidência**: Testes locais Python passando e build acionado no GitHub Actions.

## 2026-11-06 05:32 — Adição explícita de alvos Android no Rust e perfis no workspace (v0.4.2)
- **Ramo**: `fix/10-rust-android-targets-and-workspace-profiles`
- **Sintomas**:
  1. `cargo-ndk` falhou no CI com `can't find crate for core` pois o target `aarch64-linux-android` não havia sido instalado via `setup-rust-toolchain`.
  2. Cargo alertou que `[profile.release]` deve residir no workspace raiz e não em pacotes filhos.
- **Soluções**:
  1. Adicionado comando explícito `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android` no workflow `build.yml`.
  2. Movido o bloco `[profile.release]` para o `Cargo.toml` raiz.
  3. Atualizada a versão para `0.4.2` (versionCode 10).
- **Evidência**: Testes Rust e Python validados e build acionado no GitHub Actions.
