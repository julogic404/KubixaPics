# Verificador de atualizações

Ao abrir, a app faz **um único pedido GET** a:

```
https://raw.githubusercontent.com/julogic404/KubixaPics/refs/heads/main/versao.json
```

Não é enviado nenhum dado do utilizador nem nenhuma imagem. Sem rede (ou com qualquer erro), a
verificação automática falha em silêncio. Também há **Menu (☰) → Procurar atualizações**.

## Formato do `versao.json` (na raiz da branch `main`)

```json
{
  "version": "4.1.0",
  "changelog": "O que mudou nesta versão.",
  "force": false,
  "min_supported": "4.0.0",
  "apk_url": "https://github.com/julogic404/KubixaPics/releases/download/v4.1.0/KubixaPics-v4.1.0.apk"
}
```

| Campo | Significado |
|---|---|
| `version` | Última versão publicada (`x.y.z`). Se for maior que a instalada, aparece o aviso. |
| `changelog` | Texto mostrado no aviso. |
| `force` | `true` = atualização **obrigatória** para todos (o aviso não se fecha). |
| `min_supported` | Quem tem versão **inferior** a esta fica também com atualização obrigatória. |
| `apk_url` | Link `https://` do APK (ex.: o anexo da Release). Se estiver vazio ou inválido, abre a página *Releases*. |

## Comportamento

- **Opcional** (`force: false` e versão ≥ `min_supported`): janela com **Atualizar** e **Mais tarde**.
  "Mais tarde" não volta a incomodar para essa versão; a verificação manual mostra sempre.
- **Obrigatória** (`force: true` ou versão < `min_supported`): janela sem botão de fechar nem *voltar*,
  só **Atualizar** (abre o download no navegador).
- Valores inválidos (por exemplo o modelo `"#"`) são ignorados: a app considera-se atualizada.
- As versões comparam-se **numericamente** (`4.10.0` > `4.9.9`); prefixo `v` e sufixos como `-debug` são ignorados.

## Publicar uma atualização

Quando executas o workflow `release.yml` (manual), ele atualiza o `versao.json` **sozinho** depois de publicar a Release:

```bash
git tag -a v4.1.0 -m "Novidades desta versão (isto vira o changelog)"
git push origin v4.1.0
```

Depois: **Actions → Release assinado → Run workflow** com a tag `v4.1.0`.

Passados ~10 minutos: Release criada **e** `versao.json` atualizado na `main` com um commit
`chore(release): atualizar versao.json para v4.1.0 [skip ci]`.

| Campo | Como é preenchido |
|---|---|
| `version` | da tag (`v4.1.0` → `4.1.0`) |
| `apk_url` | `https://github.com/<repo>/releases/download/<tag>/KubixaPics-<tag>.apk` |
| `changelog` | 1) texto do *Run workflow*; 2) mensagem da **tag anotada** (`git tag -a ... -m`); 3) assuntos dos commits desde a tag anterior (máx. 8). Máx. 800 caracteres |
| `force` | `false` em cada Release; só fica `true` se o pedires (ver abaixo) |
| `min_supported` | mantém o valor que já estava no ficheiro (ou `0.0.0` na primeira vez) |

- **Pré-lançamentos** (`v4.2.0-beta1`) publicam a Release mas **não** mexem no `versao.json`.
- **Forçar a atualização** ou subir o mínimo: **Actions → Release assinado → Run workflow**, indica a tag
  e preenche `forcar` e/ou `versao_minima` (e, se quiseres, o `changelog`). Também podes editar o
  `versao.json` à mão.
- O `versao.json` só é atualizado **depois** de a Release (com o APK) existir, para a app nunca apontar
  para um download inexistente.

### Se a `main` tiver proteção de branch

Se o push direto for recusado, o workflow abre um **Pull Request** `atualizar-versao-<tag>` (precisa de
**Settings → Actions → General → Workflow permissions → Allow GitHub Actions to create and approve pull
requests**). Faz *merge* e o aviso fica ativo. Alternativa: nas regras da branch, deixa o
`github-actions[bot]` contornar a proteção.
