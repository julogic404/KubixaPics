# Workflow de compilação — Kubixa Pics

Guia completo do processo de build: debug, release assinado, R8, variantes, artefactos e CI.

---

## 1. Pré-requisitos

| Ferramenta | Versão |
|---|---|
| JDK | **17** (obrigatório — AGP 8.13 exige 17+) |
| Android Studio | Ladybug (2024.2.1) ou mais recente |
| Android SDK Platform | **36** (compileSdk) |
| Android SDK Build-Tools | 35.0.0+ |
| Gradle | 8.14.3 (via wrapper — não instale à mão) |

O `local.properties` com `sdk.dir=...` é criado automaticamente pelo Android Studio. Não o faça commit.

---

## 2. Build de debug

```bash
./gradlew :app:assembleDebug
```

**O que gera:** `app/build/outputs/apk/debug/app-debug.apk`

- `applicationId` = `ao.julogic404.kubixapics.debug` (sufixo `.debug`, para poder conviver com a versão de release no mesmo dispositivo)
- `versionName` = `4.0.0-debug`
- Sem minify, sem shrink, `debuggable=true`
- Assinado com a chave de debug do `~/.android/debug.keystore`

Instalar diretamente:

```bash
./gradlew :app:installDebug
# ou
adb install -r app/build/outputs/apk/debug/*.apk
```

---

## 3. Build de release

```bash
./gradlew :app:assembleRelease   # APK
./gradlew :app:bundleRelease     # AAB (Play Store)
./gradlew :app:assembleRelease :app:bundleRelease   # ambos
```

**O que gera:**

| Comando | Ficheiro |
|---|---|
| `assembleRelease` | `app/build/outputs/apk/release/app-release.apk` |
| `bundleRelease` | `app/build/outputs/bundle/release/app-release.aab` |

- `isMinifyEnabled = true` → R8 remove código morto e ofusca
- `isShrinkResources = true` → remove recursos não referenciados
- `debuggable = false`
- Assinado com o keystore de `keystore.properties`

### 3.1 Assinatura com `keystore.properties`

O ficheiro vive na **raiz do projeto** e está no `.gitignore`. Nunca é commitado.

**Criar o keystore (uma vez):**

```bash
keytool -genkeypair -v \
  -keystore kubixapics-release.jks \
  -alias kubixapics \
  -keyalg RSA -keysize 4096 -validity 10000
```

**Criar o `keystore.properties`:**

```bash
cp keystore.properties.example keystore.properties
# editar com os valores reais
```

```properties
storeFile=kubixapics-release.jks
storePassword=a_tua_password
keyAlias=kubixapics
keyPassword=a_tua_password
```

**Como o `app/build.gradle.kts` o lê:**

```kotlin
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null &&
        rootProject.file(keystoreProps.getProperty("storeFile")!!).exists()
```

As credenciais também podem vir de variáveis de ambiente (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), que é o que o CI usa. Se **não existirem**, o release cai na assinatura de debug (usa `-PexigirAssinaturaRelease=true` para falhar em vez disso; ver [ASSINATURA.md](ASSINATURA.md)) — o projeto compila num clone novo, mas o APK **não é publicável**. O Gradle não falha, o que evita bloquear quem só quer experimentar.

> ⚠️ **Guarde o `.jks` e as passwords num gestor de segredos.** Perder o keystore significa nunca mais poder atualizar a app na Play Store com o mesmo `applicationId`.

### 3.2 Verificar a assinatura

```bash
# Linux/macOS
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/*.apk

# Windows
%ANDROID_HOME%\build-tools\35.0.0\apksigner.bat verify --print-certs ^
  app\build\outputs\apk\release\*.apk
```

Deve imprimir `Verifies` e o SHA-256 do certificado.

---

## 4. R8 / ProGuard

### Onde estão as regras

| Ficheiro | Âmbito |
|---|---|
| `app/proguard-rules.pro` | Activities, ViewBinding, ViewModels do app |
| `core/common/consumer-rules.pro` | Modelos partilhados |
| `core/data/consumer-rules.pro` | **avif-coder (JNI)**, AndroidSVG, coroutines |
| `core/ui/consumer-rules.pro` | ViewBinding do core:ui |
| `feature/*/consumer-rules.pro` | ViewBinding de cada feature |

As `consumer-rules.pro` são aplicadas **automaticamente** a quem depende do módulo — é a grande vantagem da modularização: cada módulo declara as suas próprias regras e o app não precisa de saber nada sobre elas.

### Regras críticas

```proguard
# avif-coder usa JNI: os nomes das classes e métodos nativos não podem mudar
-keep class com.radzivon.bartoshyk.avif.coder.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# ViewModels são instanciados por reflexão pelo AndroidX
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }
```

**Se a app rebentar só no release** (e funcionar em debug), é quase sempre R8. Diagnóstico:

```bash
# 1. Ver o que foi removido
cat app/build/outputs/mapping/release/usage.txt

# 2. Ver o mapeamento de nomes ofuscados
cat app/build/outputs/mapping/release/mapping.txt

# 3. Descodificar um stack trace ofuscado
$ANDROID_HOME/cmdline-tools/latest/bin/retrace \
  app/build/outputs/mapping/release/mapping.txt crash.txt
```

**Teste rápido:** desative temporariamente o minify em `app/build.gradle.kts` (`isMinifyEnabled = false`) e recompile. Se o crash desaparecer, é R8 — adicione a regra `-keep` em falta.

---

## 5. Variantes de build

O projeto tem **duas variantes** (build types), sem flavors:

| Variante | applicationId | Minify | Assinatura | Uso |
|---|---|---|---|---|
| `debug` | `...kubixapics.debug` | ❌ | debug keystore | desenvolvimento |
| `release` | `...kubixapics` | ✅ R8 | `keystore.properties` | distribuição |

### Como alternar

**Android Studio:** painel *Build Variants* (canto inferior esquerdo) → escolher `debug` ou `release` no módulo `app`.

**Linha de comandos:**

```bash
./gradlew :app:assembleDebug      # debug
./gradlew :app:assembleRelease    # release
```

**Tarefas por módulo** (útil para compilar só uma parte):

```bash
./gradlew :core:data:assembleDebug
./gradlew :feature:crop:assembleRelease
```

### Adicionar um flavor (exemplo)

Se um dia precisar de uma variante sem o codificador AVIF (dependência nativa), o caminho é declarar o flavor **no `core:data`** e expor a funcionalidade por interface — não basta um flavor no `app`, porque a dependência vive no módulo de dados. O `app/build.gradle.kts` não declara flavors: a dependência AVIF vive em `core:data`, por isso um flavor no `app` não a removeria — seria enganador.

---

## 6. Onde ficam os artefactos

```
app/build/outputs/
├── apk/
│   ├── debug/app-debug.apk
│   └── release/app-release.apk
├── bundle/
│   └── release/app-release.aab
├── mapping/
│   └── release/
│       ├── mapping.txt      ← guarde este ficheiro de cada release!
│       ├── seeds.txt
│       └── usage.txt
└── logs/
```

> 📌 **Guarde o `mapping.txt` de cada release publicada.** Sem ele não consegue descodificar os stack traces ofuscados que chegam do Google Play Console.

---

## 7. Checklist antes de publicar

### Código
- [ ] `./gradlew clean` e depois `./gradlew :app:assembleRelease` sem warnings novos
- [ ] `./gradlew testDebugUnitTest` — todos os testes passam
- [ ] `./gradlew lintDebug` — sem erros (warnings revistos)
- [ ] Nenhum `Log.d` / `println` de debug no código de produção
- [ ] Nenhum TODO crítico pendente

### Versão
- [ ] `versionCode` incrementado em `app/build.gradle.kts` (obrigatório para a Play Store)
- [ ] `versionName` atualizado (semântico: `MAJOR.MINOR.PATCH`)
- [ ] `targetSdk` cumpre o requisito atual da Play Store

### Assinatura
- [ ] `keystore.properties` presente e correto
- [ ] `apksigner verify --print-certs` confirma o certificado esperado
- [ ] Keystore e passwords guardados no gestor de segredos
- [ ] `mapping.txt` arquivado

### Conteúdo da loja
- [ ] Ícone 512×512 e feature graphic 1024×500
- [ ] Pelo menos 2 screenshots de telefone
- [ ] Descrição curta (80 car.) e completa (4000 car.)
- [ ] Política de privacidade publicada (obrigatória — a app lê imagens)
- [ ] Classificação de conteúdo preenchida
- [ ] Declaração de segurança de dados: **a app não recolhe nem transmite dados**

### Teste final
- [ ] Instalar o **APK de release** (não o debug) num dispositivo real
- [ ] Testar as 4 ferramentas com imagens reais
- [ ] Testar em modo claro **e** escuro
- [ ] Testar em português **e** inglês
- [ ] Testar rotação de ecrã durante uma conversão
- [ ] Testar com uma imagem muito grande (>20 MP)
- [ ] Testar recusar a seleção de imagens (fluxo de cancelamento)

---

## 8. Automação (CI)

Há dois workflows em `.github/workflows/`:

| Workflow | Quando | O que faz |
|---|---|---|
| `android-ci.yml` | **manual** (*Run workflow*) | testes unitários + lint e APK debug como artefacto |
| `release.yml` | **manual** (*Run workflow*, indicando uma tag existente) | testes, APK+AAB **assinados**, verificação da assinatura e **Release no GitHub** com o APK e `SHA256SUMS.txt` |

O guia completo (criar o keystore, secrets, publicar, resolver problemas) está em
**[ASSINATURA.md](ASSINATURA.md)**.

Resumo para publicar:

Nenhum workflow corre sozinho em commits ou tags; todos são manuais (aba **Actions → Run workflow**).

```bash
git tag -a v4.1.0 -m "Novidades"
git push origin v4.1.0
# depois: Actions → Release assinado → Run workflow → tag v4.1.0
```

`versionName` e `versionCode` são calculados a partir da tag (`v4.1.0` → `4.1.0` / `40100`).

### Correr localmente o que o CI corre

```bash
./gradlew testDebugUnitTest lintDebug :app:assembleDebug
```

---

## 9. Problemas comuns

| Sintoma | Causa | Solução |
|---|---|---|
| `Unsupported class file major version` | JDK errado | Use JDK 17 (`File → Settings → Build → Gradle → Gradle JDK`) |
| `SDK location not found` | falta `local.properties` | Abra o projeto no Android Studio uma vez, ou crie `local.properties` com `sdk.dir=...` |
| `compileSdk 36 not found` | SDK Platform 36 não instalado | SDK Manager → instalar *Android 16 (API 36)* |
| Release rebenta, debug funciona | R8 removeu algo necessário | Ver secção 4 (retrace + `-keep`) |
| `Keystore was tampered with` | password errada | Confirme `storePassword` em `keystore.properties` |
| `Duplicate resources` | recurso com o mesmo nome em dois módulos | Renomeie ou mova para `core:ui` |
| `R.string.x unresolved` | recurso em módulo não dependido | Adicione a dependência do módulo, ou mova o recurso para `core:ui` |
| Build lento | sem cache | `org.gradle.caching=true` já está ativo; verifique `~/.gradle/caches` |

---

## 10. Comandos de referência rápida

```bash
./gradlew clean                          # limpar todos os módulos
./gradlew :app:assembleDebug             # APK debug
./gradlew :app:assembleRelease           # APK release
./gradlew :app:bundleRelease             # AAB release
./gradlew :app:installDebug              # instalar debug no dispositivo
./gradlew testDebugUnitTest              # testes unitários
./gradlew lintDebug                      # lint
./gradlew :app:dependencies              # árvore de dependências
./gradlew projects                       # listar módulos
./gradlew tasks --all                    # todas as tarefas
./gradlew --stop                         # parar daemons
```

---

## Segurança e otimização do release (estado)

| Item | Estado | Onde |
|---|---|---|
| R8 / minificação | Ativado | `isMinifyEnabled = true` + `proguard-android-optimize.txt` (`app/build.gradle.kts`) |
| Resource shrinking | Ativado | `isShrinkResources = true` |
| Debuggable | `false` | `isDebuggable = false`; o manifest não tem `debuggable` nem `testOnly` |
| Assinatura v2/v3 | Ativada (e verificada no CI) | `enableV2Signing`/`enableV3Signing`; `release.yml` falha se faltar v2/v3 ou se usar a chave de debug |
| Zipalign | Automático (AGP) e verificado no CI | `zipalign -c 4` em `release.yml` |
| Permissões mínimas | `INTERNET` (verificar atualizações) e `WRITE_EXTERNAL_STORAGE` só até Android 9 | `release.yml` falha se aparecer outra permissão |
| Componentes exportados | Só `AberturaAtiv` (launcher); tudo o resto `exported="false"` | `AndroidManifest.xml` |
| Remoção de logs | `Log.v/d/i/w/e` removidos pelo R8 | `app/proguard-rules.pro` (`-assumenosideeffects`) |
| Auditoria de dependências | Manual: *Auditoria de dependências* → alertas do Dependabot | `.github/workflows/auditoria-dependencias.yml` |
