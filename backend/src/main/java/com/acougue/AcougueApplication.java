package com.acougue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class AcougueApplication {

    /**
     * Fuso do sistema inteiro. O servidor de produção (Railway) roda em UTC, e todo
     * LocalDateTime.now()/LocalDate.now(), os carimbos de criação e o now() do banco
     * saíam 3 horas adiantados (e "hoje" virava amanhã depois das 21h).
     */
    public static final String FUSO = "America/Sao_Paulo";

    public static void main(String[] args) {
        configurarFuso();
        SpringApplication.run(AcougueApplication.class, args);
    }

    /** Antes de qualquer conexão: o driver do Postgres manda este fuso para a sessão do banco. */
    static void configurarFuso() {
        TimeZone.setDefault(TimeZone.getTimeZone(FUSO));
    }
}
