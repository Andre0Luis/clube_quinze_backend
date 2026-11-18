package br.com.clube_quinze.api.integration.notification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import br.com.clube_quinze.api.service.notification.PushNotificationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

@DisplayName("Notificações — vínculo com o agendamento para o app abrir a tela certa")
@SuppressWarnings({"rawtypes", "unchecked"})
class NotificationAppointmentLinkIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PushNotificationService pushNotificationService;

    @Test
    @DisplayName("GET /notifications devolve appointmentId e kind gravados a partir do payload do push")
    void listaNotificacoesComAgendamentoEAcao() {
        String adminToken = adminToken();
        Long adminId = ((Number) get("/api/v1/users/me", adminToken, Map.class).getBody().get("id")).longValue();

        pushNotificationService.sendToUser(adminId, "GENERAL", "Pedido de remarcação", "Fulano pediu outro horário",
                Map.of("appointmentId", 4242L, "kind", "admin_reschedule_request"));
        pushNotificationService.sendToUser(adminId, "NEWS", "Novidade", "Sem agendamento", Map.of());

        ResponseEntity<List> response = get("/api/v1/notifications", adminToken, List.class);
        List<Map> items = response.getBody();

        Map linked = items.stream().filter(n -> "Pedido de remarcação".equals(n.get("title"))).findFirst().orElseThrow();
        assertThat(((Number) linked.get("appointmentId")).longValue()).isEqualTo(4242L);
        assertThat(linked.get("kind")).isEqualTo("admin_reschedule_request");

        Map plain = items.stream().filter(n -> "Novidade".equals(n.get("title"))).findFirst().orElseThrow();
        assertThat(plain.get("appointmentId")).isNull();
        assertThat(plain.get("kind")).isEqualTo("NEWS");
    }
}
