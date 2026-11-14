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

## Variáveis e segredos necessários
- `secrets.HOSTINGER_API_KEY` e `vars.HOSTINGER_VM_ID` (já existiam).
- Imagem no GHCR usa o `GITHUB_TOKEN` automático. O pacote `clube-quinze-backend` precisa permitir escrita a este
  repositório (*Package settings → Manage Actions access*). Os segredos `GHCR_USERNAME`/`GHCR_TOKEN` não são mais usados.
- Environment `production` com revisor obrigatório.

## Proteção de branches (configurada no GitHub)
`main` e `develop`: PR obrigatório, checks obrigatórios (Testes, Migrations, Gitleaks, Trivy, Hadolint, Revisão de
dependências), sem push forçado e sem exclusão.
