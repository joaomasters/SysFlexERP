package com.acougue.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Erro de validação (400) devolve uma mensagem pronta para a tela")
class ValidacaoRespostaTest {

    private MethodArgumentNotValidException excecao(BeanPropertyBindingResult resultado) throws Exception {
        MethodParameter parametro = new MethodParameter(ValidacaoRespostaTest.class.getDeclaredMethod("alvo", String.class), 0);
        return new MethodArgumentNotValidException(parametro, resultado);
    }

    @SuppressWarnings("unused")
    private void alvo(String corpo) { }

    @Test
    @DisplayName("traz o erro por campo e um texto único em \"message\"")
    void campoEMensagem() throws Exception {
        var resultado = new BeanPropertyBindingResult(new Object(), "produto");
        resultado.addError(new FieldError("produto", "nome", "Informe a descrição do produto."));

        ResponseEntity<Map<String, String>> resposta = new GlobalExceptionHandler().handleValidation(excecao(resultado));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).containsEntry("nome", "Informe a descrição do produto.")
                .containsEntry("message", "Informe a descrição do produto.");
    }

    @Test
    @DisplayName("erro que não é de um campo não derruba o tratamento (antes virava 500)")
    void erroDeObjeto() throws Exception {
        var resultado = new BeanPropertyBindingResult(new Object(), "produto");
        resultado.addError(new ObjectError("produto", "Dados inconsistentes."));

        ResponseEntity<Map<String, String>> resposta = new GlobalExceptionHandler().handleValidation(excecao(resultado));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).containsEntry("message", "Dados inconsistentes.");
    }
}