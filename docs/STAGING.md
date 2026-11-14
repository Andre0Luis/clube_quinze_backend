# Homologação ("produção oculta")

Ambiente isolado na própria VPS, com dados falsos, para validar tudo antes de ir para produção.

## O que é
- Projeto Docker próprio (`clube-quinze-staging`): API, MariaDB 11.4, RabbitMQ, Mailpit e (opcional) nginx. **Não enxerga a produção**
  (rede, banco, fila e uploads separados).
- E-mails caem no **Mailpit** (nada sai de verdade) e o push do Firebase fica desligado.
- A API só responde com a **chave de acesso** (`X-Staging-Key`, cookie ou `?staging_key=`); sem ela, 404.
  Links de e-mail (`/appointment-action`) e o retorno do login do Google abrem sem chave.
- Consome ~1 GB de RAM (limites no `compose.staging.yaml`).

## Como acessar
- **Túnel SSH** (sem precisar de DNS/HTTPS):
  `ssh -L 8081:127.0.0.1:8081 -L 8025:127.0.0.1:8025 root@<VPS>` → API em `http://localhost:8081`, e-mails em `http://localhost:8025`.
- **HTTPS público** (depois de habilitar o TLS): `https://staging.clubequinzeapp.cloud:8443`. Para abrir o Swagger no navegador:
  `https://staging.clubequinzeapp.cloud:8443/swagger-ui.html?staging_key=<chave>` (grava um cookie).
- A chave fica no segredo `STAGING_ACCESS_KEY` do GitHub Environment `staging`.

## Fluxo de entrega
1. PR para `develop` → CI verde → **imagem `develop-<sha>`** → deploy automático na homologação + teste de fumaça.
2. `release/X.Y.Z` → imagem `X.Y.Z-rc.<sha>` → deploy na homologação → validação final.
3. Merge na `main` + tag `vX.Y.Z` → produção com **aprovação manual**, reaproveitando a imagem validada.

O deploy da homologação só roda quando a variável do repositório `STAGING_ENABLED` é `true`; o teste de fumaça só roda com
`STAGING_TLS=true`.

## Colocando no ar (uma vez)
Pré-requisito: a `main` precisa conter este `compose.yaml` (montagem de `/var/www/certbot`) — ou aplicar o passo 2, que já faz isso na VPS.

1. **DNS**: no painel do domínio, criar o registro **A** `staging` → IP da VPS. Confirmar com `dig +short staging.clubequinzeapp.cloud`.
2. **Na VPS** (root), clonar e preparar o nginx de produção para o desafio do certbot (recria só o nginx, ~2 s):
   ```bash
   git clone --depth 1 -b develop https://github.com/Andre0Luis/clube_quinze_backend.git /opt/cq-repo
   bash /opt/cq-repo/scripts/staging/enable-prod-acme.sh
   ```
3. **Certificado** do subdomínio (sem parar nada) e **correção da renovação automática da produção**:
   ```bash
   bash /opt/cq-repo/scripts/staging/request-cert.sh
   bash /opt/cq-repo/scripts/staging/fix-prod-cert-renewal.sh
   ```
4. **No GitHub**: variáveis do repositório `STAGING_ENABLED=true` e `STAGING_TLS=true`.
5. Rodar o workflow **Deploy homologação** (Actions → Run workflow) com a tag atual de `develop` (ex.: `develop-abc1234`) ou
   dar push na `develop`.
6. Conferir: `scripts/staging/smoke.sh https://staging.clubequinzeapp.cloud:8443 "$(cat ~/.config/clube-quinze/staging-access-key)"`.

## Segredos (GitHub Environment `staging`)
`STAGING_DB_ROOT_PASSWORD`, `STAGING_DB_PASSWORD`, `STAGING_RABBIT_PASSWORD`, `STAGING_JWT_SECRET`, `STAGING_ACCESS_KEY`.
Variáveis do repositório: `STAGING_ENABLED`, `STAGING_TLS`, `STAGING_URL`.

## Resetar a homologação
Apaga banco e uploads (só dados falsos): na VPS,
`docker compose -p clube-quinze-staging down -v` e rodar o deploy de novo.

## Observações
- O admin de homologação vem do seed da aplicação (`DatabaseSeeder`); a chave de acesso é o que protege o ambiente.
- O nginx de produção na VPS tem configuração própria (`/docker/clube_quinze_backend/nginx/default.conf`), que **não** é
  atualizada pelo deploy: mudanças nele são feitas por script (como o `enable-prod-acme.sh`).
