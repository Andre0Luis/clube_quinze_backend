# Atualizar a rota de Política de Privacidade

Este documento resume o que precisa ser feito na VPS sempre que for necessário ajustar o HTML estático que o nginx publica em:

- **URL pública entregue pelo nginx**: `https://clubequinzeapp.cloud/privacy-policy`
- **HTML servido**: `nginx/policies/privacy.html`
- **Arquivo de configuração do nginx**: `nginx/default.conf`
- **Compose que monta o volume**: `compose.yaml` (serviço `nginx`)

## Passo a passo na VPS

1. Entre no diretório do projeto:
   ```bash
   cd /docker/clube_quinze_backend
   ```

2. Edite o HTML que será publicado. O arquivo padrão está em `nginx/policies/privacy.html`. Você pode usar `cat <<'EOF' > nginx/policies/privacy.html ... EOF` para sobrescrever o conteúdo com o novo texto.

3. Verifique se o `docker-compose.yml` monta o volume extra:
   ```yaml
   services:
     nginx:
       volumes:
         - ./nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
         - /etc/letsencrypt:/etc/letsencrypt:ro
         - ./uploads:/var/www/uploads:ro
         - ./nginx/policies:/var/www/policies:ro
   ```
   Se a última linha não estiver presente, adicione e salve o arquivo.

4. Aplique as alterações recarregando o nginx:
   ```bash
   docker compose up -d nginx
   ```
   Se já estiver rodando, basta recarregar a configuração:
   ```bash
   docker compose exec nginx nginx -s reload
   ```

5. Confirme dentro do container que o arquivo foi montado:
   ```bash
   docker compose exec nginx ls /var/www/policies
   ```
   Deve listar `privacy.html`.

6. Teste a nova página em um navegador ou pelo comando:
   ```bash
   curl https://clubequinzeapp.cloud/privacy-policy
   ```

> Se você editar `nginx/default.conf` (por exemplo para mudar a rota ou headers), precisa repetir o passo 4 para que o nginx recarregue a nova configuração.

## Quando não precisar acessar a VPS

Você também pode alterar o HTML localmente no repositório e depois enviar o commit. O fluxo é:

1. Editar `nginx/policies/privacy.html` localmente.
2. Subir as mudanças via git (`git add nginx/policies/privacy.html nginx/default.conf compose.yaml` etc.).
3. Fazer deploy normal (`docker compose build ...`, `docker compose up -d`).

Dessa forma a mesma atualização estará disponível tanto no repositório quanto no ambiente de produção.
