package br.com.clube_quinze.api.diagnostic;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnóstico FCM isolado — NÃO sobe o contexto Spring nem precisa de DB.
 *
 * <p>Objetivo: provar, com o menor número de variáveis possível, se a credencial Firebase do
 * classpath é válida e se o FCM aceita a autenticação. Distingue:
 * <ul>
 *   <li><b>Credencial quebrada / projeto errado</b> → exceção GLOBAL ao enviar (THIRD_PARTY_AUTH_ERROR,
 *       SENDER_ID_MISMATCH, etc.) — falha já na chamada.</li>
 *   <li><b>Credencial OK, token inválido</b> → o dry-run com token fake retorna por-mensagem
 *       INVALID_ARGUMENT/UNREGISTERED, MAS a autenticação passou (é o resultado ESPERADO aqui).</li>
 * </ul>
 *
 * <p>Roda só sob demanda (não no CI, que tem firebase.enabled=false e não tem o JSON):
 * <pre>./mvnw -o -Dfcm.live=true -Dtest=FcmCredentialDiagnosticTest test</pre>
 */
@EnabledIfSystemProperty(named = "fcm.live", matches = "true")
class FcmCredentialDiagnosticTest {

    private static final String CLASSPATH_JSON = "clubequinze-firebase-adminsdk-fbsvc-02e1c4020e.json";
    private static final String DUMMY_TOKEN = "diagnostic-dummy-token-0000000000000000000000000000";

    @Test
    void loadsCredentialAndProbesFcmWithDryRun() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_JSON)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Service account não encontrado no classpath: " + CLASSPATH_JSON
                        + " — confirme que o arquivo está em src/main/resources (é gitignored).");
            }

            GoogleCredentials credentials = GoogleCredentials.fromStream(is);
            if (credentials instanceof ServiceAccountCredentials sac) {
                System.out.println("[FCM-DIAG] projectId=" + sac.getProjectId()
                        + " clientEmail=" + sac.getClientEmail());
            }

            String appName = "fcm-diagnostic";
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(a -> a.getName().equals(appName))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(
                            FirebaseOptions.builder().setCredentials(credentials).build(), appName));

            FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);

            Message message = Message.builder()
                    .setToken(DUMMY_TOKEN)
                    .setNotification(Notification.builder()
                            .setTitle("diag").setBody("diag").build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH).build())
                    .build();

            try {
                // dryRun=true: valida credencial + token sem entregar.
                String id = messaging.send(message, true);
                System.out.println("[FCM-DIAG] INESPERADO: dry-run aceitou token fake, id=" + id);
            } catch (FirebaseMessagingException ex) {
                String code = ex.getMessagingErrorCode() != null
                        ? ex.getMessagingErrorCode().name() : "UNKNOWN";
                int http = ex.getHttpResponse() != null ? ex.getHttpResponse().getStatusCode() : -1;
                System.out.println("[FCM-DIAG] errorCode=" + code + " http=" + http + " msg=" + ex.getMessage());
                Throwable cause = ex;
                int depth = 0;
                while (cause != null && depth++ < 8) {
                    System.out.println("[FCM-DIAG] cause[" + depth + "]: "
                            + cause.getClass().getName() + " -> " + cause.getMessage());
                    cause = cause.getCause();
                }
                System.out.println("[FCM-DIAG] INTERPRETAÇÃO: " + interpret(code));
            }
        }
    }

    private String interpret(String code) {
        return switch (code) {
            case "INVALID_ARGUMENT", "UNREGISTERED", "SENDER_ID_MISMATCH" ->
                    "CREDENCIAL OK — o FCM autenticou e só rejeitou o token fake (esperado). "
                    + "O problema NÃO é a credencial; investigar registro/entrega de tokens reais.";
            case "THIRD_PARTY_AUTH_ERROR" ->
                    "Auth de terceiros (APNs) — credencial do projeto OK, mas config APNs/iOS pode estar faltando.";
            default -> "Ver erro acima; se for falha de autenticação/permissão, a credencial/projeto está errado.";
        };
    }
}
