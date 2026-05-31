package br.com.clube_quinze.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseMessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseMessagingConfig.class);

    /**
     * Conteúdo JSON do service account diretamente via env var FIREBASE_SERVICE_ACCOUNT_JSON.
     * Preferido em Docker/produção onde não há como montar arquivo.
     */
    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    /**
     * Caminho para o arquivo do service account (fallback para desenvolvimento local).
     * Controlado pela env var FIREBASE_SERVICE_ACCOUNT_PATH.
     */
    @Value("${firebase.service-account:classpath:firebase-service-account.json}")
    private Resource serviceAccountResource;

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream credStream = resolveCredentialStream();
            try (InputStream is = credStream) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(is);
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();
                FirebaseApp.initializeApp(options);
                if (credentials instanceof ServiceAccountCredentials sac) {
                    log.info("FirebaseApp initialized successfully (projectId={}, clientEmail={})",
                            sac.getProjectId(), sac.getClientEmail());
                } else {
                    log.info("FirebaseApp initialized successfully (non-service-account credentials: {})",
                            credentials.getClass().getSimpleName());
                }
            }
        }
        return FirebaseMessaging.getInstance();
    }

    private InputStream resolveCredentialStream() throws IOException {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            // Logamos tamanho e "forma" (sem vazar a chave) para diagnosticar truncamento/escape
            // ao colar o JSON no .env do Docker (env_file não suporta multilinha).
            String trimmed = serviceAccountJson.trim();
            boolean looksJson = trimmed.startsWith("{") && trimmed.endsWith("}");
            log.info("Firebase: using credentials from FIREBASE_SERVICE_ACCOUNT_JSON env var "
                            + "(length={}, looksLikeJson={}, hasPrivateKey={})",
                    trimmed.length(), looksJson, trimmed.contains("private_key"));
            if (!looksJson) {
                log.warn("Firebase: FIREBASE_SERVICE_ACCOUNT_JSON não parece um JSON íntegro "
                        + "(não começa com '{' e termina com '}'). Provável truncamento no .env.");
            }
            return new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
        }
        if (!serviceAccountResource.exists()) {
            throw new IllegalStateException(
                    "Firebase: nenhuma credencial disponível. Defina FIREBASE_SERVICE_ACCOUNT_JSON "
                    + "ou FIREBASE_SERVICE_ACCOUNT_PATH para um arquivo existente. Recurso atual ausente: "
                    + serviceAccountResource.getDescription());
        }
        log.info("Firebase: using credentials from file resource: {}", serviceAccountResource.getDescription());
        return serviceAccountResource.getInputStream();
    }
}
