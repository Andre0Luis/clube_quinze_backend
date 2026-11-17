package br.com.clube_quinze.api.service.google;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.service.google.GoogleCalendarGateway.GoogleEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Evento do Google montado a partir do agendamento")
class GoogleEventMapperTest {

    private final GoogleEventMapper mapper = new GoogleEventMapper("Rua das Flores, 15");

    private Appointment appointment(AppointmentStatus status) {
        User client = new User();
        client.setName("João da Silva");
        Appointment a = new Appointment();
        a.setClient(client);
        a.setScheduledAt(LocalDateTime.of(2027, 3, 10, 14, 30));
        a.setDurationMinutes(45);
        a.setServiceType("Corte e barba");
        a.setNotes("Prefere máquina 2");
        a.setStatus(status);
        return a;
    }

    @Test
    @DisplayName("confirmado fica verde, com título, descrição, local e horário em Brasília")
    void confirmado() {
        GoogleEvent e = mapper.toEvent(appointment(AppointmentStatus.CONFIRMED));

        assertThat(e.colorId()).isEqualTo("10");
        assertThat(e.summary()).isEqualTo("Corte e barba — João da Silva");
        assertThat(e.description()).contains("Status: Confirmado", "Cliente: João da Silva", "Prefere máquina 2");
        assertThat(e.location()).isEqualTo("Rua das Flores, 15");
        assertThat(e.start()).isEqualTo(LocalDateTime.of(2027, 3, 10, 14, 30));
        assertThat(e.end()).isEqualTo(LocalDateTime.of(2027, 3, 10, 15, 15));
        assertThat(e.timeZone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    @DisplayName("cores por status: agendado amarelo, remarcação pendente laranja, concluído grafite")
    void coresPorStatus() {
        assertThat(mapper.toEvent(appointment(AppointmentStatus.SCHEDULED)).colorId()).isEqualTo("5");
        assertThat(mapper.toEvent(appointment(AppointmentStatus.RESCHEDULE_REQUESTED)).colorId()).isEqualTo("6");
        assertThat(mapper.toEvent(appointment(AppointmentStatus.COMPLETED)).colorId()).isEqualTo("8");
    }

    @Test
    @DisplayName("remarcação pendente avisa no título e mostra o horário proposto")
    void remarcacaoPendente() {
        Appointment a = appointment(AppointmentStatus.RESCHEDULE_REQUESTED);
        a.setProposedAt(LocalDateTime.of(2027, 3, 12, 16, 0));

        GoogleEvent e = mapper.toEvent(a);

        assertThat(e.summary()).startsWith("Remarcação pendente — ");
        assertThat(e.description()).contains("Novo horário proposto: 12/03/2027 às 16:00");
    }

    @Test
    @DisplayName("sem serviço usa título genérico; só o nome do cliente vai ao Google (nada de telefone ou e-mail)")
    void semServicoESemDadosSensiveis() {
        Appointment a = appointment(AppointmentStatus.SCHEDULED);
        a.setServiceType(null);
        a.getClient().setEmail("joao@exemplo.com");
        a.getClient().setPhone("+5511999999999");

        GoogleEvent e = mapper.toEvent(a);

        assertThat(e.summary()).isEqualTo("Atendimento — João da Silva");
        assertThat(e.summary() + e.description()).doesNotContain("joao@exemplo.com", "5511999999999");
    }
}
