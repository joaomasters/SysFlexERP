package com.acougue.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ex.getMessage(), 403));
    }

    // Fiado acima do limite: 409 com os valores, para o PDV pedir a autorização de um administrador.
    // Declarado mais específico que BusinessException, então o Spring escolhe este handler.
    @ExceptionHandler(LimiteCreditoExcedidoException.class)
    public ResponseEntity<LimiteExcedidoResponse> handleLimiteExcedido(LimiteCreditoExcedidoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new LimiteExcedidoResponse(
                ex.getMessage(), 409, LimiteCreditoExcedidoException.CODIGO,
                ex.getClienteNome(), ex.getLimite(), ex.getSaldoAtual(), ex.getValorFiado(), ex.getExcedente(),
                LocalDateTime.now()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(ex.getMessage(), 422));
    }

    @ExceptionHandler(InvalidBarcodeException.class)
    public ResponseEntity<ErrorResponse> handleBarcode(InvalidBarcodeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage(), 400));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage(), 400));
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage(), 404));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            // erro de objeto (não de um campo) não é FieldError: usa o nome do objeto como chave
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });
        // "message" resume tudo em um texto só — é o que as telas mostram no aviso.
        errors.put("message", String.join(" ", new LinkedHashSet<>(errors.values())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /*
     Erro inesperado: o detalhe técnico (SQL, nome de tabela/constraint, stack)
     vai SÓ para o log do servidor. Antes ia na resposta — e entregava a
     estrutura do banco para quem estivesse sondando a API. O usuário recebe
     um código para o suporte localizar o erro no log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        String codigo = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.error("Erro interno [{}]", codigo, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Erro interno inesperado. Se persistir, informe ao suporte o código " + codigo + ".", 500));
    }

    // Rota inexistente: 404 (antes virava 500 "No static resource ...")
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleRotaInexistente(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Recurso não encontrado.", 404));
    }

    // Corpo da requisição ilegível (JSON malformado, tipo errado): 400 sem expor detalhes do parser
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleCorpoInvalido(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Dados enviados em formato inválido.", 400));
    }

    /*
     Restrições únicas que o usuário consegue resolver sozinho: a mensagem diz o que corrigir.
     O serviço já confere antes de gravar, mas dois cadastros ao mesmo tempo passam os dois
     pela conferência e só o banco barra o segundo (issue #92). O nome da restrição não vai
     para a resposta, só a mensagem.
     */
    private static final Map<String, String> MENSAGENS_RESTRICAO = Map.of(
            "produtos_codigo_interno_key",
            "Este código interno já está cadastrado em outro produto. Informe outro código "
                    + "ou deixe o campo em branco para gerar automaticamente.",
            "uk_tenants_codigo",
            "Este código de empresa já está em uso. Tente salvar novamente.",
            // Módulo industrial: códigos únicos dos cadastros (V39)
            "uk_ind_unidade_codigo", "Já existe uma unidade com este código.",
            "uk_ind_setor_codigo", "Já existe um setor com este código nesta unidade.",
            "uk_ind_centro_codigo", "Já existe um centro de trabalho com este código.",
            "uk_ind_turno_codigo", "Já existe um turno com este código.",
            "uk_ind_tipo_maquina_codigo", "Já existe um tipo de máquina com este código.",
            "uk_ind_maquina_codigo", "Já existe uma máquina com este código.",
            "uk_ind_ferramenta_codigo_rev", "Já existe uma ferramenta com este código e revisão."
    );

    // Violação de regra do banco (valor fora da lista, duplicado...): 422 com mensagem
    // específica quando a restrição é conhecida, senão uma mensagem neutra
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegridade(org.springframework.dao.DataIntegrityViolationException ex) {
        String detalhe = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
        log.warn("Violação de integridade: {}", detalhe);
        String mensagem = mensagemDaRestricao(ex, detalhe);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(mensagem != null ? mensagem
                        : "Os dados informados não são válidos ou já existem no cadastro.", 422));
    }

    /** Mensagem amigável da restrição violada, ou null se ela não está mapeada. */
    static String mensagemDaRestricao(Throwable ex, String detalhe) {
        // Hibernate informa o nome da restrição; sem ele, procura o nome no texto do Postgres
        for (Throwable t = ex; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve && cve.getConstraintName() != null) {
                String msg = MENSAGENS_RESTRICAO.get(cve.getConstraintName().toLowerCase());
                if (msg != null) return msg;
            }
        }
        if (detalhe == null) return null;
        String texto = detalhe.toLowerCase();
        return MENSAGENS_RESTRICAO.entrySet().stream()
                .filter(e -> texto.contains("\"" + e.getKey() + "\""))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    public record LimiteExcedidoResponse(String message, int status, String codigo, String clienteNome,
                                         BigDecimal limite, BigDecimal saldoAtual, BigDecimal valorFiado,
                                         BigDecimal excedente, LocalDateTime timestamp) {}

    public record ErrorResponse(String message, int status, LocalDateTime timestamp) {
        public ErrorResponse(String message, int status) {
            this(message, status, LocalDateTime.now());
        }
    }
}