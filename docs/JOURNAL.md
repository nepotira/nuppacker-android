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
