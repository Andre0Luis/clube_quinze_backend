package br.com.clube_quinze.api.integration.admin;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Dashboard Administrativo — Testes de Integração")
@SuppressWarnings({"rawtypes", "unchecked"})
class DashboardIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/dashboard/summary";

    @Test
    @DisplayName("GET /admin/dashboard/summary → 200 admin visualiza resumo")
    void devePermitirAdminVisualizarResumo() {
        String token = loginAsAdmin();

        ResponseEntity<Map> response = get(BASE, token, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys(
                "totalClients",
                "activePlans",
                "monthlyRevenue",
                "upcomingAppointments",
                "upcomingPayments");
    }

    @Test
    @DisplayName("GET /admin/dashboard/summary → 403 membro não acessa resumo")
    void deveNegarMembroNoDashboardResumo() {
        String memberToken = loginAsMember();

        ResponseEntity<Map> response = get(BASE, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
