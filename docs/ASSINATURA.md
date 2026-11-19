# Assinar e publicar o Kubixa Pics (APK release)

Este guia explica, passo a passo, como:

1. criar o teu **keystore** (`.jks`);
2. colocá-lo no GitHub **sem nunca o expor** (nem no repositório, nem nos logs);
3. publicar um APK assinado na aba **Releases** com uma tag e um clique em **Run workflow** (nenhum workflow corre sozinho).

> **Regra de ouro:** o ficheiro `.jks` e as passwords **nunca** vão para o repositório.
> O `.gitignore` já bloqueia `*.jks`, `*.keystore` e `keystore.properties`.

---

## Como funciona (visão geral)

```
 O teu PC                         GitHub (privado)                    GitHub Releases
┌──────────────┐  base64 +      ┌───────────────────────┐  git tag   ┌──────────────────┐
│ kubixapics-  │  4 secrets     │ Settings > Secrets    │  v4.1.0    │ KubixaPics-      │
│ release.jks  │ ─────────────▶ │ KEYSTORE_BASE64       │ ─────────▶ │ v4.1.0.apk       │
└──────────────┘                │ KEYSTORE_PASSWORD     │  workflow  │ SHA256SUMS.txt   │
                                │ KEY_ALIAS             │  release   └──────────────────┘
                                │ KEY_PASSWORD          │
                                └───────────────────────┘
```

- Os secrets ficam **cifrados** no GitHub; ninguém (nem tu) os consegue voltar a ler, só substituir.
- No workflow, o `.jks` é reconstruído numa pasta temporária do runner, com permissão só para o dono, e **apagado no fim**.
- As passwords chegam ao Gradle por **variáveis de ambiente** (não são escritas em ficheiro).
- O GitHub **mascara** automaticamente o valor dos secrets nos logs.
- Pull requests vindos de forks **não têm acesso** aos secrets.

---

## 1. Criar o keystore (uma única vez)

Precisas do JDK 17 (já vem com o Android Studio). Num terminal:

```bash
keytool -genkeypair -v \
  -keystore kubixapics-release.jks \
  -storetype PKCS12 \
  -alias kubixapics \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

O `keytool` pergunta:

- **Password do keystore** → escolhe uma forte (12+ caracteres). Guarda-a.
- **Nome, organização, país** → podes pôr, por exemplo, `JuLogic 404` e `AO`.
- **Password da chave** → com PKCS12 deve ser **igual** à do keystore (carrega Enter para repetir).

Já tens um `.jks`? Salta para o passo 2. Só precisas de saber o **alias** (vê com
`keytool -list -keystore kubixapics-release.jks`) e as duas passwords.

---

## 2. Guardar cópias de segurança (IMPORTANTE)

Se perderes o `.jks` ou a password, **nunca mais consegues atualizar a app** com o mesmo
`applicationId` (`ao.julogic404.kubixapics`).

- Guarda **2 cópias** do `.jks` em locais diferentes (por exemplo, um gestor de palavras-passe
  com anexos como Bitwarden/1Password **e** um pen drive offline).
- Guarda as passwords e o alias no mesmo gestor.
- **Não** envies o `.jks` por e-mail, WhatsApp ou Drive público.

---

## 3. Converter o keystore para Base64

O GitHub só guarda texto, por isso o `.jks` vai como Base64.

**Linux**
```bash
base64 -w 0 kubixapics-release.jks > keystore.b64
```

**macOS**
```bash
base64 -i kubixapics-release.jks | tr -d '\n' > keystore.b64
```

**Windows (PowerShell)**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("kubixapics-release.jks")) | Set-Content keystore.b64 -NoNewline
```

---

## 4. Adicionar os 4 secrets no GitHub

### Pelo site (mais simples)

1. Abre o repositório → **Settings** → **Secrets and variables** → **Actions**.
2. Clica em **New repository secret** e cria estes quatro:

| Nome do secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | o **conteúdo** do ficheiro `keystore.b64` (abre, copia tudo, cola) |
| `KEYSTORE_PASSWORD` | a password do keystore |
| `KEY_ALIAS` | o alias (ex.: `kubixapics`) |
| `KEY_PASSWORD` | a password da chave (igual à do keystore se usaste PKCS12) |

### Pelo terminal (GitHub CLI) — sem copiar/colar

```bash
gh secret set KEYSTORE_BASE64   < keystore.b64
gh secret set KEYSTORE_PASSWORD          # cola a password quando pedir
gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
```

### Depois de adicionar

Apaga o ficheiro temporário e limpa o rasto:

```bash
shred -u keystore.b64      # Linux  (macOS: rm -P keystore.b64 | Windows: del keystore.b64)
history -c                 # limpa o histórico do terminal, se escreveste passwords nele
```

O `.jks` original **fica só nas tuas cópias de segurança**, fora da pasta do projeto.

---

## 5. (Recomendado) Proteger a publicação com um *Environment*

O workflow usa o environment `release`. Para exigir a tua aprovação antes de cada publicação:

1. **Settings** → **Environments** → **New environment** → nome `release`.
2. Ativa **Required reviewers** e adiciona a tua conta.
3. Em **Deployment branches and tags**, escolhe *Selected branches and tags* e adiciona a branch `main`
   (o workflow é executado manualmente a partir dela).

Assim, mesmo que alguém com acesso de escrita dispare o workflow, a publicação só sai com a tua aprovação.

---

## 6. Publicar uma versão

Nenhum workflow deste projeto corre sozinho: **todos são manuais** (só `workflow_dispatch`).
Criar ou enviar uma tag **não** publica nada por si só.

**Passo 1 — criar e enviar a tag** (formato `vMAJOR.MINOR.PATCH`):

```bash
git tag -a v4.1.0 -m "Novidades desta versão"
git push origin v4.1.0
```

**Passo 2 — executar o workflow manualmente:**
**Actions → Release assinado → Run workflow** → escolhe a branch `main` e escreve a tag (`v4.1.0`).

Ao ser executado, o `release.yml` faz:

1. corre os testes unitários;
2. gera o **APK** e o **AAB** assinados;
3. verifica a assinatura (e **falha** se por engano usar a chave de debug);
4. cria a Release **Kubixa Pics v4.1.0** em *Releases* com:
   - `KubixaPics-v4.1.0.apk`
   - `SHA256SUMS.txt`
   - notas automáticas (lista de alterações desde a versão anterior);
5. atualiza o `versao.json` na `main` (aviso de atualização dentro da app, ver
   [ATUALIZACOES.md](ATUALIZACOES.md)). Dica: cria a tag com mensagem
   (`git tag -a v4.1.0 -m "Novidades..."`) para ela virar o *changelog*.

O que mais é guardado (em privado, em **Actions → o run → Artifacts**, durante 90 dias):
o `.aab` (para a Play Store) e o `mapping.txt` (para decifrar crashes do R8).

**Versões automáticas:** `versionName` vem da tag (`v4.1.0` → `4.1.0`) e `versionCode` é calculado
(`MAJOR*10000 + MINOR*100 + PATCH`, ou seja `40100`). Nunca precisas de editar o `build.gradle.kts`
para lançar uma versão; basta uma tag nova e maior.

**Pré-lançamento:** uma tag como `v4.2.0-beta1` publica a Release marcada como *Pre-release*.

**Repetir uma publicação:** volta a executar **Run workflow** com a mesma tag.

---

## 7. Gerar o APK assinado no teu PC (opcional)

Copia `keystore.properties.example` para `keystore.properties` (na raiz, ignorado pelo git) e preenche:

```properties
storeFile=C:/caminho/seguro/kubixapics-release.jks
storePassword=a_tua_password
keyAlias=kubixapics
keyPassword=a_tua_password
```

Depois:

```bash
./gradlew :app:assembleRelease -PexigirAssinaturaRelease=true
```

O `-PexigirAssinaturaRelease=true` faz o build **falhar** se faltar o keystore, em vez de cair
silenciosamente na chave de debug. O APK sai em `app/build/outputs/apk/release/`.

Verificar a assinatura:

```bash
$ANDROID_HOME/build-tools/<versão>/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## 8. Boas práticas de privacidade

- Antes de qualquer `git push`, confirma que não há chaves no histórico:
  ```bash
  git ls-files | grep -Ei '\.jks$|\.keystore$|keystore\.properties$'   # tem de ficar vazio
  git log --all --oneline -- '*.jks' 'keystore.properties'              # tem de ficar vazio
  ```
- Nunca imprimas secrets com `echo` em passos novos do workflow.
- Ao adicionar *Actions* de terceiros ao workflow, fixa-as por versão e revê o que fazem.
- **Se o `.jks` ou as passwords vazaram:** trata a chave como comprometida. Gera um keystore novo,
  atualiza os 4 secrets e, se a app estiver na Play Store, pede a **reposição da chave de upload**
  no Play Console. Se usas **Play App Signing**, a chave de assinatura real fica na Google e a de
  upload pode ser trocada — é a opção mais segura para publicar na loja.

---

## 9. Problemas comuns

| Mensagem / sintoma | Causa | Solução |
|---|---|---|
| `Falta o secret KEYSTORE_BASE64` | secret não criado ou nome diferente | Confirma os 4 nomes em **Settings → Secrets → Actions** (maiúsculas exatas) |
| `Keystore was tampered with, or password was incorrect` | `KEYSTORE_PASSWORD` errada | Volta a criar o secret com a password certa |
| `Cannot recover key` | `KEY_PASSWORD` ou `KEY_ALIAS` errados | Confirma com `keytool -list -keystore ...` |
| `Assinatura de release exigida mas o keystore nao foi encontrado` | Base64 vazio/corrompido | Recria `keystore.b64` (sem quebras de linha) e atualiza o secret |
| `O APK foi assinado com a chave de DEBUG` | credenciais não chegaram ao Gradle | Verifica os nomes dos secrets e o passo *Build release* |
| `Tag invalida` | formato errado | Usa `v1.2.3` (ou `v1.2.3-beta1`) |
| Erro 403 ao criar a Release | permissões do workflow | **Settings → Actions → General → Workflow permissions** → *Read and write* |
| O workflow não arranca ao enviar a tag | por desenho: são todos manuais | **Actions → Release assinado → Run workflow** |
| `couldn't find remote ref` no checkout | a tag não foi enviada ao GitHub | `git push origin v4.1.0` e volta a executar |
