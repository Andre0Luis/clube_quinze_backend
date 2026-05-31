# Notificações Push (FCM) — causa raiz, fix e diagnóstico

> Histórico: as notificações push paravam em produção — o app subia normalmente, mas
> **todo envio falhava**. Documentado aqui o que era, como foi corrigido e como diagnosticar
> de novo no futuro.

## Sintoma

- App sobe sem erro (`FirebaseApp initialized successfully`).
- Todo envio FCM falha. Em `push_deliveries`: `status='failed'`, `error_message='UNKNOWN'`.
- No log: `FcmPushServiceImpl : FCM batch: 0 sent, 1 failed`.

## Causa raiz — version skew do `google-auth-library`

`firebase-admin:9.4.2` declara `google-auth-library-credentials:1.23.0` (vence por *nearest-wins*),
enquanto `gax`/`api-common` puxam `google-auth-library-oauth2-http:1.29.0`. O oauth2-http 1.29.0
referencia `com.google.auth.CredentialTypeForMetrics`, classe **ausente** no credentials 1.23.0.

Resultado: ao renovar o access token OAuth (lazy, no primeiro envio), o SDK lança
`NoClassDefFoundError: com/google/auth/CredentialTypeForMetrics`, que sobe como
`FirebaseMessagingException` com `MessagingErrorCode = null` → logado como `UNKNOWN`.
Como o init é lazy, o app **sobe**, mas **nenhum push é entregue**.

## Fix

`pom.xml` — `dependencyManagement` alinhando os DOIS artefatos `google-auth` na mesma versão:

```xml
<properties>
  <google-auth.version>1.29.0</google-auth.version>
</properties>
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.google.auth</groupId>
      <artifactId>google-auth-library-credentials</artifactId>
      <version>${google-auth.version}</version>
    </dependency>
    <dependency>
      <groupId>com.google.auth</groupId>
      <artifactId>google-auth-library-oauth2-http</artifactId>
      <version>${google-auth.version}</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Após o fix, o dry-run sai de `UNKNOWN` para `UNREGISTERED`/`ok` (o FCM passa a autenticar).

## Credencial

`FirebaseMessagingConfig` resolve a credencial nesta ordem:
1. `FIREBASE_SERVICE_ACCOUNT_JSON` (env var, JSON inteiro em UMA linha) — usado em produção.
2. `FIREBASE_SERVICE_ACCOUNT_PATH` (arquivo) — fallback. **Recomendado** via volume Docker:
   coloque `secrets/firebase-service-account.json` e monte em `/run/secrets/firebase` (ver `secrets/README.md`).

⚠️ O JSON do classpath (`*-firebase-adminsdk-*.json`) é **gitignored** → NÃO entra na imagem do CI.
Em produção a credencial vem da env var (ou do volume), nunca do classpath.

## Como diagnosticar / testar

### Endpoint de diagnóstico (admin)
```
POST /api/v1/notifications/test/{userId}?dryRun=true   # valida credencial+token, NÃO entrega
POST /api/v1/notifications/test/{userId}               # envia de verdade
```
Retorna resultado por token (`ok`, `error` com o errorCode real do FCM, token mascarado).
Não grava em `push_deliveries`.

### Teste isolado de credencial (sem subir o app, sem DB)
```
./mvnw -o -Dfcm.live=true -Dtest=FcmCredentialDiagnosticTest test
```
Carrega o service account do classpath, faz dry-run e interpreta o erro:
- `UNKNOWN / refreshing access token` → skew de dependência (este bug).
- `INVALID_ARGUMENT / UNREGISTERED` → credencial OK, token inválido (esperado p/ token fake).

### Confirmar o skew num jar
```
unzip -l target/*.jar | grep google-auth-library   # credentials e oauth2-http devem ter a MESMA versão
```

## Gotchas do cliente (app mobile)

- **Android 13+**: exibir notificação exige permissão `POST_NOTIFICATIONS` concedida. O `getToken()`
  funciona mesmo sem ela (o token registra), mas nada aparece. Se "o token registra mas não chega",
  verifique a permissão em Ajustes → Apps → Clube Quinze → Notificações.
- **App em foreground**: no Android, mensagem de notificação não aparece sozinha na bandeja;
  é tratada em `messaging().onMessage()` e exibida via `expo-notifications`
  (requer `Notifications.setNotificationHandler` — já configurado em `app/_layout.tsx`).
- Tokens `UNREGISTERED` são auto-invalidados (`invalidated_at`) — normal; o device re-registra ao abrir o app.

## Deploy (produção)

Push na `main` builda e publica a imagem, mas o passo do Hostinger **não recria o container**.
Após o build, no VPS (`/docker/clube_quinze_backend`):
```
docker compose pull api && docker compose up -d api
```
Validar no log: `FirebaseApp initialized successfully (projectId=clubequinze, ...)`.
