package br.com.clube_quinze.api.config;

import com.google.auth.oauth2.GoogleCredentials;
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
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(is))
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("FirebaseApp initialized successfully");
            }
        }
        return FirebaseMessaging.getInstance();
    }

    private InputStream resolveCredentialStream() throws IOException {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            log.info("Firebase: using credentials from FIREBASE_SERVICE_ACCOUNT_JSON env var");
            return new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
        }
        log.info("Firebase: using credentials from file resource: {}", serviceAccountResource);
        return serviceAccountResource.getInputStream();
    }
}
