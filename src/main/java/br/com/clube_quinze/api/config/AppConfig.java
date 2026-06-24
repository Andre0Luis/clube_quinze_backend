package br.com.clube_quinze.api.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Fuso oficial da aplicação: horário de Brasília (UTC-3). Os horários de agendamento
     * (scheduledAt) são gravados como wall-clock de Brasília, então todo "agora" comparado a
     * eles precisa estar no mesmo fuso — caso contrário os lembretes disparam com defasagem
     * (era o bug: lembrete de "30 min" chegando ~3h antes, ao meio-dia para atendimento às 15h).
     */
    public static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock applicationClock() {
        return Clock.system(APP_ZONE);
    }
}
