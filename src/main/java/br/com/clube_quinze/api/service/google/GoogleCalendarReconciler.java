package br.com.clube_quinze.api.service.google;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rede de segurança: a cada 2 minutos sincroniza o que ficou para trás (fila fora do ar, falha do Google, carga inicial). */
@Component
public class GoogleCalendarReconciler {

    static final int BATCH = 25;
    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarReconciler.class);

    private final GoogleCalendarService service;

    public GoogleCalendarReconciler(GoogleCalendarService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 120_000L, initialDelay = 60_000L)
    public void run() {
        int done = reconcileNow();
        if (done > 0) {
            log.info("Google Agenda: {} agendamento(s) sincronizado(s) pelo reconciliador", done);
        }
    }

    public int reconcileNow() {
        return service.reconcile(BATCH);
    }
}
