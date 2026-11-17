# CI/CD — Gitflow, qualidade e segurança

Tudo roda no GitHub Actions (grátis em repositório público). Nada sobe em produção sem passar por aqui.

## Branches (Gitflow)

| Branch | Para quê | O que acontece |
|---|---|---|
| `feature/*` | trabalho novo | PR para `develop` roda todas as verificações |
| `develop` | integração | verificações + imagem Docker `develop-<sha>` |
| `release/X.Y.Z` | candidato à produção | verificações + imagem `X.Y.Z-rc` (é a que será promovida) |
| `main` | produção | recebe a release; **não** faz deploy sozinha |
| `hotfix/X.Y.Z` | correção urgente | sai da `main`, gera `X.Y.Z-rc`, volta para `main` **e** `develop` |

## Verificações (workflows `CI` e `Segurança`)

| Check | O que faz | Bloqueia o merge? |
|---|---|---|
| Testes + cobertura | suíte completa (H2) + JaCoCo | sim |
| Migrations (MariaDB real) | Flyway V1..Vn num MariaDB (Testcontainers) + `ddl-auto=validate` | sim |
| Qualidade (SpotBugs) | relatório de possíveis bugs | não (relatório) |
| CodeQL | análise estática de segurança | alertas em *Security → Code scanning* |
| Gitleaks | segredos nos commits **novos** | sim |
| Revisão de dependências | dependência nova com vulnerabilidade HIGH+ | sim |
| Trivy | vulnerabilidades HIGH/CRITICAL (com correção) e segredos | sim |
| Hadolint | boas práticas do Dockerfile | sim (só erros) |

Dependabot abre PRs semanais (Maven, Actions e Docker) apontando para `develop`.
As ações do GitHub estão fixadas por SHA; o Dependabot atualiza.

## Como publicar uma versão

1. `git checkout develop && git checkout -b release/X.Y.Z`
2. Ajuste a `<version>` do `pom.xml` para `X.Y.Z` e faça o push. O CI gera a imagem `X.Y.Z-rc`.
3. Valide a release (homologação, quando existir) e corrija na própria branch, se preciso.
4. Abra PR `release/X.Y.Z` → `main` e faça o merge.
5. Na `main`, crie a tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
6. O workflow **Deploy produção** pede **aprovação manual** (Environment `production`), confere se a tag está na
   `main`, se o `pom.xml` bate e se o código é idêntico ao da imagem validada, promove `X.Y.Z-rc` → `X.Y.Z`
   (mesma imagem, sem reconstruir), publica na Hostinger e confere `/actuator/info` e `/api/v1/plans`.
7. Se a verificação falhar, volta sozinho para a versão anterior. Depois do sucesso, `:latest` aponta para a versão no ar.
8. Faça o merge da `main` de volta na `develop`.

### Rollback manual
*Actions → Deploy produção → Run workflow* com `action = deploy` e a versão anterior (ex.: `1.17.0`).
O primeiro rollback precisa de uma imagem com versão: rode uma vez `action = baseline` com a versão que está no ar hoje.

## Atenção: a ação da Hostinger REESCREVE o `.env` a cada deploy
`hostinger/deploy-on-vps` grava o `.env` do projeto **sempre**: com as variáveis de `environment-variables` ou, se nenhuma for
informada, com o `.env.example` do repositório (valores em branco). Foi isso que derrubou a produção no release 1.18.0
(JWT, banco e e-mail vazios). Regras:
- **Homologação:** o workflow passa todas as variáveis (segredos do Environment `staging`).
- **Produção:** **todas** as variáveis vêm do segredo `PROD_ENV` do Environment `production` (linhas `CHAVE=valor`), mais
  `IMAGE_TAG` definido pelo workflow. O workflow **barra o deploy** se `PROD_ENV` estiver vazio ou sem as chaves essenciais.
- Cada versão sobe com **tag própria** de imagem (`IMAGE_TAG=1.18.1`): a VPS é obrigada a baixá-la. Mover `:latest` não adianta,
  porque a VPS reaproveita a imagem em cache com esse nome.
- Para atualizar uma variável de produção, atualize o segredo `PROD_ENV` (não edite o `.env` da VPS: o próximo deploy o reescreve).

Criar/atualizar `PROD_ENV` a partir do `.env` atual da VPS (os valores não aparecem na tela):
```bash
ssh root@72.60.242.216 'grep -v "^#" /docker/clube_quinze_backend/.env | grep "="' | gh secret set PROD_ENV --env production --repo Andre0Luis/clube_quinze_backend
```

## Saúde (`/health`)
`GET /health` é público e informa: `status` (UP, DEGRADED se a fila caiu, DOWN/503 se o banco caiu), `app`, `version`, `build`
(sha curto gravado na imagem), `environment`, `uptimeSeconds` e `checks` (banco e fila de verdade; push, e-mail e Google só
informam se estão configurados). Os deploys de homologação e produção esperam a **build certa** com banco `UP` antes de seguir.

## Tags e Releases
A tag `vX.Y.Z` é a versão do backend (`pom.xml`). A cada deploy de produção **bem-sucedido** o workflow cria a **Release** do GitHub
com as mudanças desde a versão anterior (notas geradas dos PRs). Deploy que falha e volta para a versão anterior **não** gera Release.

## Variáveis e segredos necessários
- `secrets.HOSTINGER_API_KEY` e `vars.HOSTINGER_VM_ID` (já existiam).
- Imagem no GHCR usa o `GITHUB_TOKEN` automático. O pacote `clube-quinze-backend` precisa permitir escrita a este
  repositório (*Package settings → Manage Actions access*). Os segredos `GHCR_USERNAME`/`GHCR_TOKEN` não são mais usados.
- Environment `production` com revisor obrigatório.

## Proteção de branches (configurada no GitHub)
`main` e `develop`: PR obrigatório, checks obrigatórios (Testes, Migrations, Gitleaks, Trivy, Hadolint, Revisão de
dependências), sem push forçado e sem exclusão.
