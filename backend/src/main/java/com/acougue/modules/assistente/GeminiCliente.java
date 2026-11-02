package com.acougue.modules.assistente;

import com.acougue.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/*
 Chamada à Interactions API do Gemini (POST /interactions) em modo sem estado
 (store: false): o Google não guarda a conversa, e o histórico vai inteiro em
 cada chamada. A chave vai no cabeçalho, nunca na URL nem no log.
 */
@Slf4j
@Component
public class GeminiCliente {

    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(40);

    private final ObjectMapper json;
    private final HttpClient http;
    private final String chave;
    private final String url;

    public GeminiCliente(ObjectMapper json,
                         @Value("${assistente.gemini.chave:}") String chave,
                         @Value("${assistente.gemini.url:https://generativelanguage.googleapis.com/v1beta}") String url) {
        this.json = json;
        this.chave = chave.trim();
        this.url = url.replaceAll("/+$", "") + "/interactions";
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean configurado() {
        return !chave.isEmpty();
    }

    public JsonNode criarInteracao(ObjectNode corpo) {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                .timeout(TEMPO_LIMITE)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", chave)
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString()))
                .build();
        HttpResponse<String> resposta;
        try {
            resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("Assistente: Gemini não respondeu: {}", e.getMessage());
            throw new BusinessException("O assistente não conseguiu responder agora. Tente de novo em instantes.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("O assistente não conseguiu responder agora. Tente de novo em instantes.");
        }

        if (resposta.statusCode() == 429) {
            log.warn("Assistente: limite do Gemini (429): {}", resumo(resposta.body()));
            // A camada gratuita tem limite por minuto e por dia (20 chamadas/dia no 3.6 Flash em nov/2026)
            throw new BusinessException(resposta.body().contains("per day")
                    ? "O limite gratuito diário do assistente acabou. Ele volta a funcionar amanhã."
                    : "O limite gratuito do assistente foi atingido. Tente de novo em alguns minutos.");
        }
        // Na camada gratuita o 403 aparece quando chegam chamadas demais seguidas (visto em nov/2026);
        // chave inválida também dá 403, e aí o log abaixo mostra
        if (resposta.statusCode() == 403) {
            log.warn("Assistente: Gemini recusou (403): {}", resumo(resposta.body()));
            throw new BusinessException("O serviço de IA recusou a pergunta agora. Tente de novo em um minuto.");
        }
        if (resposta.statusCode() == 503) {
            throw new BusinessException("O serviço de IA está sobrecarregado agora. Tente de novo em alguns minutos.");
        }
        if (resposta.statusCode() / 100 != 2) {
            log.warn("Assistente: Gemini respondeu {}: {}", resposta.statusCode(), resumo(resposta.body()));
            throw new BusinessException("O assistente não conseguiu responder agora. Tente de novo em instantes.");
        }
        try {
            return json.readTree(resposta.body());
        } catch (IOException e) {
            throw new BusinessException("O assistente devolveu uma resposta inválida. Tente de novo.");
        }
    }

    private static String resumo(String corpo) {
        return corpo.length() > 500 ? corpo.substring(0, 500) : corpo;
    }
}
