# Kubixa Pics

Estúdio de imagem completo para Android, 100% offline. Converte, redimensiona, recorta e comprime imagens sem enviar nada para a internet.

- **Package:** `ao.julogic404.kubixapics`
- **Versão:** 4.0.2 (versionCode 6)
- **minSdk:** 24 (Android 7.0) · **targetSdk:** 35 · **compileSdk:** 36
- **Kotlin** 2.3.10 · **AGP** 8.13.2 · **Gradle** 8.14.3
- **Arquitetura:** Gradle multi-módulo (3 módulos `core` + 4 módulos `feature` + `app`)

---

## Como abrir no Android Studio

1. Descompacte o zip.
2. Android Studio → **File → Open** → selecione a pasta `imagedit-studio_v4`.
3. Aguarde o **Gradle Sync** (descarrega as dependências na primeira vez).
4. **Run ▶** num emulador ou dispositivo.

> O projeto compila sem `keystore.properties`: nesse caso o build de *release* usa a assinatura de *debug*. Para assinar a sério, copie `keystore.properties.example` para `keystore.properties` e preencha os valores.
>
> 📘 **Workflow de compilação completo (debug, release, R8, variantes, CI):** [`docs/BUILD.md`](docs/BUILD.md)

---

## Funcionalidades

### 1. Conversão de formato
- **Entrada:** JPG/JPEG, PNG, WebP, GIF, BMP, TIFF, SVG, AVIF, HEIC/HEIF
- **Saída:** WebP, PNG, JPG, AVIF, BMP, TIFF
- Conversão de **várias imagens em simultâneo** (até 50 por seleção)
- Escolha do formato de saída, qualidade ajustável (10–100%)
- **Preservação de transparência** quando o formato suporta (WebP, PNG, AVIF, BMP, TIFF); JPG achata sobre branco
- **Comparação antes/depois** por imagem e resumo do lote (total poupado e %)

### 2. Redimensionamento
- Dimensões exatas, **com ou sem proporção**
- **Percentagem** (1–400%)
- **Limites máximos** de largura e altura
- **Presets:** Instagram Post 1080×1080, Instagram Story 1080×1920, Facebook Cover 851×315, YouTube Thumbnail 1280×720, WhatsApp Profile 500×500, HD 1920×1080, Full HD 1920×1080, Cover 3000×3000
- Pré-visualização em tempo real do tamanho alvo

### 3. Recorte
- **Livre**, **1:1**, **circular**, **16:9**, **4:3**
- **Rotação** em passos de 90°
- Moldura arrastável com pegas nos cantos e grelha de terços
- Pré-visualização em tempo real; o recorte circular gera PNG com transparência real

### 4. Compressão
- Compressão individual ou **em lote**
- Nível de qualidade ajustável
- **Alvo de tamanho máximo** (500 KB / 1 MB / 2 MB): reduz a qualidade e, se necessário, a resolução até caber
- Comparação original vs. comprimida com **percentagem de redução** (ex.: 4,5 MB → 850 KB → −81%)

---

## Arquitetura modular

O projeto é um **Gradle multi-módulo**: cada ferramenta é um módulo independente que só conhece os módulos de base. O `app` é uma *shell* fina (navegação, marca, manifest).

```
KubixaPics/
├── settings.gradle.kts            inclui os 8 módulos
├── build.gradle.kts               plugins (apply false)
├── gradle/libs.versions.toml      catálogo de versões centralizado
│
├── core/
│   ├── common/                    ← modelos e utilitários puros (sem Android UI)
│   │   └── ao/julogic404/kubixapics/core/common/
│   │       ├── Evento.kt                   evento consumido uma só vez
│   │       ├── model/ImagemEditada.kt        resultado (miniatura + bytes)
│   │       ├── model/ImagemFormato.kt        enum de formatos
│   │       ├── model/PreRedimensao.kt      presets de redes sociais
│   │       └── util/Formatadores.kt          tamanhos e percentagens
│   │
│   ├── data/                      ← motor de imagem (sem UI)
│   │   └── ao/julogic404/kubixapics/core/data/
│   │       ├── ImagemDecodificadorEx.kt           descodificação multi-formato + EXIF
│   │       ├── ImagemCodificador.kt             WebP/PNG/JPG/AVIF/BMP/TIFF
│   │       ├── ProcessadorImagem.kt        redimensionar
│   │       ├── ImagemCompressor.kt          comprimir + alvo de bytes
│   │       ├── BmpCodec.kt                 escritor BMP (24/32 bit)
│   │       ├── TiffCodec.kt                escritor TIFF baseline
│   │       ├── SalvarImagem.kt               MediaStore IS_PENDING + partilha
│   │       ├── Prefs.kt                    preferências e estatísticas
│   │       └── ModeloViewListaEdit.kt        ViewModel base (lista + progresso)
│   │
│   └── ui/                        ← tema, recursos partilhados, widgets
│       ├── ao/julogic404/kubixapics/core/ui/
│       │   ├── AdaptadorImagemEditada.kt      ListAdapter + DiffUtil
│       │   └── widget/CortarView.kt          vista de recorte interativa
│       └── res/                            strings (pt/en), cores, temas,
│                                           dimens, 30+ drawables, item layout
│
├── feature/
│   ├── convert/                   ← conversão de formato
│   │   ├── ConverterAtiv.kt
│   │   ├── ConverteModeloView.kt
│   │   └── res/layout/ativ_converter.xml
│   ├── resize/                    ← redimensionamento
│   ├── crop/                      ← recorte
│   └── compress/                  ← compressão
│
└── app/                           ← shell: navegação, marca, manifest
    ├── ImagemEditorApp.kt
    ├── PrincipalAtiv.kt            hub de ferramentas + estatísticas
    ├── SobreAtiv.kt
    ├── AndroidManifest.xml        declara as Activities dos módulos
    ├── proguard-rules.pro
    └── res/                       ícone/launcher, layouts do hub, xml/
```

### Grafo de dependências

```
        app
         │  (depende de todos)
   ┌─────┼─────┬─────┬─────┐
   ▼     ▼     ▼     ▼     ▼
convert resize crop compress
   └─────┴──┬──┴─────┘
            ▼
        core:ui ──► core:common
            │
        core:data ──► core:common
```

- `core:common` não depende de nada (só Kotlin + `core-ktx`).
- `core:data` depende de `core:common` e expõe-o com `api(...)`.
- `core:ui` depende de `core:common` e expõe-o com `api(...)`.
- Cada `feature:*` depende dos três `core` e **não conhece as outras features**.
- `app` depende de tudo e é o único que declara Activities no manifest.

### Porque é que isto importa

| Antes (monólito) | Agora (multi-módulo) |
|---|---|
| Um `build.gradle` de 200 linhas | 8 ficheiros pequenos e focados |
| Alterar o recorte recompilava tudo | Só `:feature:crop` recompila |
| Regras ProGuard todas no app | Cada módulo traz as suas `consumer-rules.pro` |
| Impossível testar o motor sem UI | `:core:data` testa-se com JUnit puro |
| Recursos num só `res/` | Recursos no módulo que os usa |

### Decisões de arquitetura (mantidas)

- O estado vive nos **ViewModels** → sobrevive à rotação do ecrã.
- A lista guarda apenas uma **miniatura pequena** (220 px) e os bytes do resultado; o bitmap completo é libertado logo após a codificação.
- Todo o processamento pesado corre em `Dispatchers.Default`.
- **Photo Picker** (`PickVisualMedia`) → zero permissões em Android 13+.
- Gravação com **MediaStore + IS_PENDING** em `Pictures/KubixaPics`.
- `android.nonTransitiveRClass=false` — as features referenciam `R.string.*` de `core:ui`; com R não-transitivo essas referências não compilariam.

---

## Compilação

```bash
./gradlew :app:assembleDebug      # APK debug
./gradlew :app:assembleRelease    # APK release (R8 + assinado)
./gradlew :app:bundleRelease      # AAB para a Play Store
./gradlew testDebugUnitTest       # testes
./gradlew lintDebug               # lint
```

**Artefactos:**
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

Assinatura via `keystore.properties` (fora do git). Sem esse ficheiro o release usa a chave de debug e **não é publicável**.

📘 **Guia completo** — assinatura, R8/ProGuard, variantes, checklist de publicação, CI e resolução de problemas: **[`docs/BUILD.md`](docs/BUILD.md)**

### CI (GitHub Actions)

`.github/workflows/android-ci.yml`:
- **`checks`** — testes unitários + lint em cada push/PR
- **`debug`** — APK debug como artefacto
- **`release`** — em tags `v*`: reconstrói o keystore a partir de secrets, gera APK+AAB, verifica a assinatura e anexa a uma GitHub Release

Secrets necessários: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

---

## Bugs corrigidos nesta versão

| # | Problema | Correção |
|---|---|---|
| 1 | Crash `trying to use a recycled bitmap` ao remover/terminar | A lista guarda só miniaturas; o bitmap completo é libertado após codificar |
| 2 | Imagens desfocadas por `inSampleSize` fixo | Escala real para o lado maior pedido |
| 3 | Fotos de câmara apareciam rodadas | EXIF aplicado (`ImageDecoder` em API 28+, rotação manual antes) |
| 4 | Tudo desaparecia ao rodar o ecrã | Estado movido para ViewModels |
| 5 | Itens errados após remover | `ListAdapter` + `DiffUtil` em vez de `notifyDataSetChanged` |
| 6 | Crashes silenciados por handler global | Handler `setDefaultUncaughtExceptionHandler` removido |
| 7 | Strings de formato inválidas (`%d%`, `%%` sem argumentos) | Formatos corrigidos e validados |
| 8 | Permissões de armazenamento desnecessárias | Photo Picker; `WRITE_EXTERNAL_STORAGE` limitado a `maxSdkVersion=28` |
| 9 | Ficheiros corrompidos na galeria | `MediaStore` com `IS_PENDING` |
| 10 | Password do keystore em texto simples | `keystore.properties` fora do controlo de versões |
| 11 | `compileSdk`/`targetSdk`/AGP/Gradle inconsistentes | Alinhados (36 / 35 / 8.13.2 / 8.14.3) |
| 12 | `file_paths.xml` expunha todo o armazenamento | Reduzido a `cache/shared` e `files/shared` |
| 13 | **AVIF com dimensões ímpares lançava exceção** | Recorte de 1 px para dimensões pares antes de codificar |
| 14 | **TIFF em Android 10+ falhava** (caminho legado exigia permissão) | Todos os formatos passam por MediaStore em Q+ |
| 15 | **Recorte da imagem inteira reciclava o bitmap devolvido** | `finally` já não liberta o resultado nem o original |
| 16 | **Rotação no recorte desalinhava a moldura** | A pré-visualização passa a mostrar a imagem já rodada |
| 17 | **Recorte circular era ignorado** | Máscara alfa real (`PorterDuff.SRC_IN`) → PNG transparente |
| 18 | **Compressão com alvo libertava o bitmap em uso** | Dimensões guardadas antes de reciclar; passos intermédios libertados |
| 19 | **`ImagemFormato.valueOf` podia rebentar** com preferência inválida | `runCatching` com fallback para WebP |
| 20 | Leitura dupla do ficheiro para obter o tamanho | Tamanho vem dos bytes já lidos |

---

## Correções do log de compilação (v4)

O segundo log do CI já **passou o toolchain** (125 tarefas executadas, `:core:common`, `:core:ui` e os 4 módulos de feature compilaram) e falhou em `:core:data:compileDebugKotlin` com **erros de código reais**. Duas causas, ambas minhas:

| # | Erro no log | Causa real | Correção |
|---|---|---|---|
| 1 | `Unresolved reference 'LiveData'` / `'MutableLiveData'` (10 ocorrências em `ModeloViewListaEdit.kt`) + `Cannot infer type for type parameter 'T'` + `Return type mismatch: expected 'List<ImagemEditada>', actual 'String'` | O `lifecycle-viewmodel-ktx` **não contém** `LiveData`/`MutableLiveData` — essas classes vivem no artefacto **`lifecycle-livedata-ktx`**, que nunca foi declarado. Os erros de inferência e de tipo são cascata: sem o tipo `MutableLiveData`, o compilador não consegue resolver `MutableLiveData<List<ImagemEditada>>(emptyList())` nem o getter `currentImages`. | Novo alias `androidx-lifecycle-livedata-ktx` no catálogo; dependência adicionada a `core:data`, `feature:crop`, `feature:resize`, `feature:convert` e `feature:compress`. Em `core:data` é **`api(...)`** e não `implementation(...)`, porque `ModeloViewListaEdit` expõe `LiveData` na sua API pública e os módulos de feature têm de a ver. |
| 2 | `e: ImagemDecodificadorEx.kt:131:45 Too many arguments for 'fun toString(): String'` | `bytes.take(512)` sobre um `ByteArray` devolve uma **`List<Byte>`**, não um `ByteArray`. `List<Byte>.toString()` não aceita argumentos — daí "too many arguments". O código nunca compilou; a heurística de deteção de SVG estava silenciosamente inválida. | `String(bytes, 0, minOf(512, bytes.size), Charsets.UTF_8)` — descodifica os primeiros 512 bytes diretamente, sem copiar o ficheiro inteiro para uma lista. |

**Porque é que estes erros não apareceram na v3:** o build da v3 parou antes, no `FirIncompatibleClassExpressionChecker` (incompatibilidade de metadata Kotlin). Assim que o toolchain ficou correto, o compilador chegou finalmente ao código e expôs estes dois bugs — que sempre lá estiveram.

---

## Correções do log de compilação (v3)

O CI falhou com `BUILD FAILED in 1m 40s` em `:core:data:compileDebugKotlin`. A causa **não era o `BmpCodec.kt`** — era incompatibilidade de versões do toolchain:

| # | Erro no log | Causa real | Correção |
|---|---|---|---|
| 1 | `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.3.0, expected version is 2.0.0` (avif-coder + kotlin-stdlib) | O `avif-coder:2.2.0` é compilado com **Kotlin 2.3.0** e arrasta `kotlin-stdlib:2.3.0`, mas o projeto usava KGP **2.0.21**, que só lê metadata ≤ 2.0. | Kotlin **2.3.10** |
| 2 | `e: .../BmpCodec.kt:60:5: java.lang.IllegalArgumentException: source must not be null` + `Internal compiler error` | **Efeito colateral, não causa.** É o `FirIncompatibleClassExpressionChecker` a rebentar ao tentar *reportar* a incompatibilidade de metadata do ponto 1 — o `source` nulo é o ficheiro-fonte do diagnóstico que nunca chegou a ser criado. O `BmpCodec.kt` está correto. | Resolvido pelo ponto 1 |
| 3 | `w: 'resourceConfigurations: MutableSet<String>' is deprecated` | `AGP 8.13` descontinuou a API. | Mantido (é aviso, não erro); documentado o porquê |
| 4 | (latente) `kotlinOptions {}` | Removido no Kotlin 2.3 — teria rebentado no passo seguinte. | Migrado para `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }` em todos os 8 módulos |
| 5 | (latente) AGP 8.9.2 vs. `compileSdk 36` + `avif-coder` | O `avif-coder` declara `minCompileSdk=36`; AGP 8.9.2 é anterior ao SDK 36. | AGP **8.13.2** + Gradle **8.14.3** |

**Matriz de compatibilidade agora verificada** (contra a tabela oficial Kotlin e os metadados do AAR real):

```
KGP 2.3.10  →  Gradle 7.6.3–9.0.0   ✓ 8.14.3
            →  AGP    8.2.2–9.0.0   ✓ 8.13.2
AGP 8.13.2  →  Gradle ≥ 8.13         ✓ 8.14.3
            →  JDK 17                ✓
avif-coder  →  metadata Kotlin 2.3.0 ✓ 2.3.10
            →  minCompileSdk 36      ✓ 36
```

---

## Verificação feita

Verificação **real** feita nesta versão (JDK 17 + Gradle 8.14.3 instalados no ambiente):

✅ **`./gradlew projects` → BUILD SUCCESSFUL.** Os 8 módulos são reconhecidos e todos os `build.gradle.kts` são avaliados sem erro de script — incluindo o `kotlin { compilerOptions { … } }` e a nova `api(libs.androidx.lifecycle.livedata.ktx)`.

✅ **`./gradlew :core:data:dependencies --configuration debugRuntimeClasspath` → BUILD SUCCESSFUL.** Prova direta da correção do último log: `androidx.lifecycle:lifecycle-livedata-ktx:2.8.7` aparece agora como dependência **direta** de `:core:data`, e `lifecycle-livedata:2.8.7` + `lifecycle-livedata-core:2.8.7` resolvem na árvore. É precisamente o artefacto que faltava para `LiveData`/`MutableLiveData` existirem. Também confirma que `avif-coder:2.2.0` e `androidsvg:1.4` continuam a resolver.

> ⚠️ Nota de ambiente: o sandbox tem 2 GB de RAM e o daemon do Gradle morria com `-Xmx2048m`. A verificação correu com `--no-daemon -Dorg.gradle.jvmargs="-Xmx900m -XX:MaxMetaspaceSize=400m"`. **Isto não afeta o projeto** — o `gradle.properties` do repositório mantém `-Xmx2048m`, que é adequado a uma máquina de desenvolvimento normal.

✅ **Verificação estática do código:**
- 55 XML bem-formados (layouts, values, manifest).
- **0 referências `R.*` em falta** (strings, cores, dimens, estilos, drawables, ids).
- **0 IDs de ViewBinding em falta** — cada `binding.x` existe no layout do seu módulo.
- **0 imports não utilizados** e **0 incoerências `package`↔pasta**.
- **0 `kotlinOptions`** restantes (removido no Kotlin 2.3).

✅ **API do `avif-coder` confirmada** contra o AAR e o código-fonte reais da 2.2.0: `HeifCoder.encodeAvif(bitmap, quality, …)`, `minCompileSdk=36`, `minSdk=24`, ABIs arm64-v8a/armeabi-v7a/x86/x86_64.

❌ **Não compilei o APK.** A plataforma deste ambiente bloqueia a instalação do Android SDK (política explícita: pacotes de apps de telemóvel não são construídos aqui). Sem `android.jar` da plataforma 36, `:core:data:compileDebugKotlin` para no passo de licenças do SDK, antes de chegar ao Kotlin. **O primeiro Gradle Sync no Android Studio é o teste real de compilação** — se aparecer erro, envie a mensagem.

---

## Sugestões para evoluir

**Alto valor, baixo esforço**
1. **Marca de água** — texto ou logótipo com posição, opacidade e tamanho.
2. **Rodar/espelhar** como ferramenta própria (fora do recorte).
3. **Ajustes rápidos** — brilho, contraste, saturação, nitidez.
4. **Filtros predefinidos** (P&B, sépia, vintage) com pré-visualização.
5. **Metadados** — ver e remover EXIF/GPS (privacidade antes de partilhar).
6. **Renomear em lote** com padrão (`{nome}-{largura}x{altura}`).
7. **Histórico** das últimas edições com repetir/desfazer.
8. **Comparador antes/depois** com slider arrastável.

**Médio esforço**
9. **Pipeline em cadeia** — converter + redimensionar + comprimir numa só passagem.
10. **Perfis de exportação** guardados ("Web 1200px WebP 75%").
11. **Recorte com zoom e pinça** para precisão ao pixel.
12. **Cortar/colar área** e **redimensionar por conteúdo** (seam carving simplificado).
13. **Fundo transparente automático** (remoção de fundo on-device).
14. **Colagem/mosaico** — juntar várias imagens numa grelha.
15. **PDF** — imagens → PDF e PDF → imagens.
16. **Widget** e **atalhos** (App Shortcuts) para cada ferramenta.
17. **Modo tablet** com dois painéis (lista + pré-visualização).

**Qualidade e engenharia**
18. **Testes unitários** do `ImagemRedimensionador`/`ImagemCompressor` (cálculo de dimensões e alvo de bytes) e testes instrumentados do `CortarView`.
19. **Baseline Profile** para arranque mais rápido.
20. **CI no GitHub Actions** com `assembleDebug` + lint + testes.
21. **Crashlytics/Play Console** para monitorizar falhas reais.
22. **Acessibilidade** — descrições em falta, tamanhos mínimos de toque, TalkBack no `CortarView`.
23. **Localização adicional** (es/fr/de) — a estrutura de strings já está pronta.

---

## Licenças de terceiros

AndroidX, Material Components, Kotlin Coroutines, [AndroidSVG](https://github.com/BigBadaboom/androidsvg) (Apache 2.0), [avif-coder](https://github.com/awxkee/avif-coder) (MIT).
