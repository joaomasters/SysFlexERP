package com.acougue.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Violação de integridade (422) explica o que corrigir quando a restrição é conhecida")
class IntegridadeRespostaTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static DataIntegrityViolationException doHibernate(String restricao, String sqlMsg) {
        var cve = new org.hibernate.exception.ConstraintViolationException(
                "could not execute statement", new SQLException(sqlMsg, "23505"), restricao);
        return new DataIntegrityViolationException("could not execute statement", cve);
    }

    @Test
    @DisplayName("código interno duplicado (cadastros simultâneos, issue #92) diz para trocar o código")
    void codigoInternoDuplicado() {
        var ex = doHibernate("produtos_codigo_interno_key",
                "ERROR: duplicate key value violates unique constraint \"produtos_codigo_interno_key\"");

        var resposta = handler.handleIntegridade(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody().message()).contains("código interno já está cadastrado");
    }

    @Test
    @DisplayName("sem o nome da restrição no Hibernate, reconhece pelo texto do Postgres")
    void reconhecePeloTexto() {
        var ex = new DataIntegrityViolationException("falha",
                new SQLException("ERROR: duplicate key value violates unique constraint \"produtos_codigo_interno_key\""));

        assertThat(handler.handleIntegridade(ex).getBody().message()).contains("código interno já está cadastrado");
    }

    @Test
    @DisplayName("restrição não mapeada mantém a mensagem neutra, sem expor o nome da restrição")
    void restricaoDesconhecida() {
        var ex = doHibernate("alguma_outra_key",
                "ERROR: duplicate key value violates unique constraint \"alguma_outra_key\"");

        var mensagem = handler.handleIntegridade(ex).getBody().message();

        assertThat(mensagem).isEqualTo("Os dados informados não são válidos ou já existem no cadastro.");
        assertThat(mensagem).doesNotContain("alguma_outra_key");
    }
}
