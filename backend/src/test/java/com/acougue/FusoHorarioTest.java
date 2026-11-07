package com.acougue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fuso horário do sistema")
class FusoHorarioTest {

    private final TimeZone original = TimeZone.getDefault();

    @AfterEach
    void restaurar() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("servidor em UTC passa a marcar a hora de Brasília")
    void servidorEmUtcUsaHorarioDeBrasilia() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        AcougueApplication.configurarFuso();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Sao_Paulo");
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime brasilia = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        assertThat(ChronoUnit.SECONDS.between(agora, brasilia)).isBetween(-5L, 5L);
    }
}
