package com.acougue.modules.pagamento;

import com.acougue.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP único para as APIs das adquirentes. Centraliza timeouts e o
 * tratamento de erro de rede, e é o ponto de mock nos testes dos provedores.
 */
@Component
@RequiredArgsConstructor
public class ClienteHttpPagamento {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public record Resposta(int status, String corpo, JsonNode json) {
        public boolean sucesso() {
            return status >= 200 && status < 300;
        }
    }

    public Resposta enviar(String metodo, String url, Map<String, String> headers, Object corpo) {
        try {
            HttpRequest.BodyPublisher publisher = corpo == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corpo));

            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .method(metodo, publisher);
            if (corpo != null) req.header("Content-Type", "application/json");
            headers.forEach(req::header);

            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = null;
            if (resp.body() != null && !resp.body().isBlank()) {
                try {
                    json = objectMapper.readTree(resp.body());
                } catch (Exception ignored) {
                    // corpo não-JSON (ex: página de erro de proxy) — fica só o texto
                }
            }
            return new Resposta(resp.statusCode(), resp.body(), json);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Comunicação com a adquirente interrompida.");
        } catch (Exception e) {
            throw new BusinessException("Falha de comunicação com a adquirente: " + e.getMessage());
        }
    }
}
