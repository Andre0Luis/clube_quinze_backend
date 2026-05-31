# secrets/

Credenciais montadas no container via volume (ver `compose.yaml`). **Nada aqui é commitado** (ver `.gitignore`), exceto este README e o `.gitkeep`.

## Firebase service account

Coloque o JSON do service account do Firebase aqui com o nome exato:

```
secrets/firebase-service-account.json
```

O `compose.yaml` monta `./secrets` em `/run/secrets/firebase:ro` e aponta
`FIREBASE_SERVICE_ACCOUNT_PATH=file:/run/secrets/firebase/firebase-service-account.json`.

### Por que arquivo e não env var?
JSON multilinha (a `private_key` tem `\n`) em `.env` é frágil: `env_file` do Docker Compose
não suporta multilinha e qualquer escape errado trunca o valor. Arquivo montado é determinístico.

### Deploy no VPS
1. `scp` o service account para `~/clube_quinze/secrets/firebase-service-account.json` (ou o path do compose).
2. `chmod 600 secrets/firebase-service-account.json`.
3. `docker compose up -d`.
4. Confirmar no log: `FirebaseApp initialized successfully (projectId=clubequinze, ...)`.
5. Teste: `POST /api/v1/notifications/test/<userId>?dryRun=true` com token de admin.
