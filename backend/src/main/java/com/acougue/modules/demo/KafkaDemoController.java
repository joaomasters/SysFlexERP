package com.acougue.modules.demo;

import com.acougue.config.KafkaTopicConfig;
import com.acougue.modules.messaging.events.AlertaEstoqueEvent;
import com.acougue.modules.messaging.events.PixConfirmadoEvent;
import com.acougue.modules.messaging.events.VendaFechadaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/demo/kafka")
@RequiredArgsConstructor
// Só existe com Kafka ligado E demo.kafka.enabled=true (DEMO_KAFKA): estas rotas publicam eventos
// falsos de venda/PIX e não podem ficar abertas em produção.
@ConditionalOnProperty(name = {"kafka.enabled", "demo.kafka.enabled"}, havingValue = "true")
public class KafkaDemoController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    

    

    @PostMapping("/venda-completa")
    public ResponseEntity<Map<String, Object>> simularVendaCompleta() {

        long vendaId = System.currentTimeMillis() % 100_000;

        List<VendaFechadaEvent.ItemEvent> itens = List.of(
                
                new VendaFechadaEvent.ItemEvent(
                        1L, "Picanha",
                        new BigDecimal("2.500"),          
                        new BigDecimal("1.500"),          
                        new BigDecimal("5.000")           
                ),
                
                new VendaFechadaEvent.ItemEvent(
                        2L, "Contrafilé",
                        new BigDecimal("1.000"),
                        new BigDecimal("3.000"),          
                        new BigDecimal("4.000")
                ),
                
                new VendaFechadaEvent.ItemEvent(
                        3L, "Alcatra",
                        new BigDecimal("0.500"),
                        new BigDecimal("8.000"),          
                        new BigDecimal("3.000")
                )
        );

        VendaFechadaEvent evento = new VendaFechadaEvent(
                vendaId, 10L, new BigDecimal("287.50"), itens
        );

        log.info("[DEMO] Publicando VendaFechadaEvent vendaId={} no tópico {}", vendaId, KafkaTopicConfig.VENDAS_FECHADAS);
        kafkaTemplate.send(KafkaTopicConfig.VENDAS_FECHADAS, String.valueOf(vendaId), evento);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "publicado");
        response.put("topico", KafkaTopicConfig.VENDAS_FECHADAS);
        response.put("vendaId", vendaId);
        response.put("totalVenda", "R$ 287,50");
        response.put("itens_publicados", List.of(
                Map.of("produto", "Picanha",    "estoqueApos", "1.500 kg", "minimo", "5.000 kg", "alerta", true),
                Map.of("produto", "Contrafilé", "estoqueApos", "3.000 kg", "minimo", "4.000 kg", "alerta", true),
                Map.of("produto", "Alcatra",    "estoqueApos", "8.000 kg", "minimo", "3.000 kg", "alerta", false)
        ));
        response.put("espere", "EstoqueConsumer irá publicar AlertaEstoqueEvent para Picanha e Contrafilé");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    

    

    @PostMapping("/pix-confirmado")
    public ResponseEntity<Map<String, Object>> simularPixConfirmado(
            @RequestParam(defaultValue = "42") Long vendaId,
            @RequestParam(defaultValue = "150.00") BigDecimal valor) {

        long mpPaymentId = System.currentTimeMillis();
        PixConfirmadoEvent evento = new PixConfirmadoEvent(vendaId, mpPaymentId, valor);

        log.info("[DEMO] Publicando PixConfirmadoEvent vendaId={} mpPaymentId={} valor={}",
                vendaId, mpPaymentId, valor);
        kafkaTemplate.send(KafkaTopicConfig.PIX_CONFIRMADOS, String.valueOf(mpPaymentId), evento);

        return ResponseEntity.ok(Map.of(
                "status", "publicado",
                "topico", KafkaTopicConfig.PIX_CONFIRMADOS,
                "vendaId", vendaId,
                "mpPaymentId", mpPaymentId,
                "valor", "R$ " + valor,
                "descricao", "PixConsumer irá logar a confirmação (extensível para WebSocket)",
                "timestamp", Instant.now().toString()
        ));
    }

    

    

    @PostMapping("/alerta-estoque")
    public ResponseEntity<Map<String, Object>> simularAlertaEstoque(
            @RequestParam(defaultValue = "Picanha") String produto,
            @RequestParam(defaultValue = "0.500") BigDecimal estoqueAtual,
            @RequestParam(defaultValue = "5.000") BigDecimal estoqueMinimo) {

        long produtoId = System.currentTimeMillis() % 1000;
        BigDecimal deficit = estoqueAtual.subtract(estoqueMinimo);

        AlertaEstoqueEvent evento = new AlertaEstoqueEvent(
                produtoId, produto, estoqueAtual, estoqueMinimo, deficit
        );

        log.info("[DEMO] Publicando AlertaEstoqueEvent produto='{}' deficit={}", produto, deficit);
        kafkaTemplate.send(KafkaTopicConfig.ESTOQUE_ALERTAS, String.valueOf(produtoId), evento);

        return ResponseEntity.ok(Map.of(
                "status", "publicado",
                "topico", KafkaTopicConfig.ESTOQUE_ALERTAS,
                "produto", produto,
                "estoqueAtual", estoqueAtual + " kg",
                "estoqueMinimo", estoqueMinimo + " kg",
                "deficit", deficit + " kg",
                "descricao", "AlertaEstoqueConsumer irá logar — extensível para e-mail/push",
                "timestamp", Instant.now().toString()
        ));
    }

    

    

    @GetMapping("/topicos")
    public ResponseEntity<Map<String, Object>> listarTopicos() {
        return ResponseEntity.ok(Map.of(
                "topicos", List.of(
                        Map.of(
                                "nome", KafkaTopicConfig.VENDAS_FECHADAS,
                                "partitions", 3,
                                "replication", 2,
                                "producers", List.of("VendaEventProducer (@TransactionalEventListener AFTER_COMMIT)"),
                                "consumers", List.of("EstoqueConsumer (group: acougue-erp-estoque)")
                        ),
                        Map.of(
                                "nome", KafkaTopicConfig.PIX_CONFIRMADOS,
                                "partitions", 3,
                                "replication", 2,
                                "producers", List.of("PixEventProducer"),
                                "consumers", List.of("PixConsumer (group: acougue-erp-pix)")
                        ),
                        Map.of(
                                "nome", KafkaTopicConfig.ESTOQUE_ALERTAS,
                                "partitions", 3,
                                "replication", 2,
                                "producers", List.of("AlertaEventProducer"),
                                "consumers", List.of("AlertaEstoqueConsumer (group: acougue-erp-alertas)")
                        )
                ),
                "broker", "Upstash Kafka (SASL_SSL / SCRAM-SHA-256)",
                "ambiente", "Railway Production"
        ));
    }
}
