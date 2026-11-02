package com.acougue.modules.assistente;

import com.acougue.entity.Modulo;
import com.acougue.exception.BusinessException;
import com.acougue.repository.ItensVendaRepository;
import com.acougue.security.UsuarioAutenticado;
import com.acougue.security.auditoria.AuditoriaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Assistente")
class AssistenteTest {

    private final ObjectMapper json = new ObjectMapper();

    private static UsuarioAutenticado usuario(Map<Modulo, UsuarioAutenticado.Permissoes> permissoes) {
        return new UsuarioAutenticado(7L, "ana", "Ana", "GESTOR", false, permissoes, "tenant_loja");
    }

    private static final UsuarioAutenticado.Permissoes VER = new UsuarioAutenticado.Permissoes(true, false, false, false);

    private static void autenticar(UsuarioAutenticado u) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private JsonNode resposta(String passosJson) throws Exception {
        return json.readTree("{\"id\":\"x\",\"status\":\"completed\",\"steps\":" + passosJson + "}");
    }

    @Nested
    @DisplayName("Conversa com o Gemini")
    class Conversa {

        private final GeminiCliente gemini = mock(GeminiCliente.class);
        private final FerramentasAssistente ferramentas = mock(FerramentasAssistente.class);
        private final LimiteUsoAssistente limite = new LimiteUsoAssistente(20, 100, 100);
        private final AuditoriaService auditoria = mock(AuditoriaService.class);
        private final AssistenteService service = new AssistenteService(gemini, ferramentas, limite, auditoria, json, "modelo-teste");

        private final UsuarioAutenticado gestor = usuario(Map.of(Modulo.RELATORIOS, VER, Modulo.PRODUTOS, VER));

        Conversa() {
            when(gemini.configurado()).thenReturn(true);
            autenticar(gestor);
            when(ferramentas.disponiveis(gestor)).thenReturn(List.of(FerramentasAssistente.TODAS.get(0)));
        }

        @Test
        @DisplayName("executa a ferramenta pedida e devolve o resultado com o mesmo call_id")
        void cicloComFerramenta() throws Exception {
            ObjectNode saida = json.createObjectNode().put("faturamento", 123.45);
            when(ferramentas.executar(eq(gestor), eq("resumo_vendas"), any())).thenReturn(saida);
            List<String> corpos = new ArrayList<>();
            when(gemini.criarInteracao(any())).thenAnswer(inv -> {
                corpos.add(inv.getArgument(0).toString());
                return corpos.size() == 1
                        ? resposta("[{\"type\":\"thought\",\"signature\":\"sig-1\"},"
                        + "{\"type\":\"function_call\",\"id\":\"call_9\",\"name\":\"resumo_vendas\","
                        + "\"arguments\":{\"data_inicio\":\"2026-11-02\",\"data_fim\":\"2026-11-02\"}}]")
                        : resposta("[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"Hoje: R$ 123,45.\"}]}]");
            });

            AssistenteService.Resposta r = service.perguntar("qual meu faturamento hoje?", List.of());

            assertThat(r.resposta()).isEqualTo("Hoje: R$ 123,45.");
            assertThat(r.consultas()).containsExactly("Resumo de vendas");
            JsonNode segunda = json.readTree(corpos.get(1)).path("input");
            assertThat(segunda.get(0).path("type").asText()).isEqualTo("user_input");
            // passos do modelo voltam como vieram, inclusive a assinatura do raciocínio
            assertThat(segunda.get(1).path("signature").asText()).isEqualTo("sig-1");
            assertThat(segunda.get(2).path("id").asText()).isEqualTo("call_9");
            assertThat(segunda.get(3).path("type").asText()).isEqualTo("function_result");
            assertThat(segunda.get(3).path("call_id").asText()).isEqualTo("call_9");
            assertThat(segunda.get(3).path("result").get(0).path("text").asText()).contains("123.45");
            verify(auditoria).registrar(eq("ASSISTENTE"), eq("CONSULTAR"), contains("qual meu faturamento hoje?"));
        }

        @Test
        @DisplayName("envia só as ferramentas permitidas, sem guardar a conversa no Google")
        void corpoDaRequisicao() throws Exception {
            when(gemini.criarInteracao(any())).thenReturn(
                    resposta("[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}]"));

            service.perguntar("oi", List.of());

            ArgumentCaptor<ObjectNode> corpo = ArgumentCaptor.forClass(ObjectNode.class);
            verify(gemini).criarInteracao(corpo.capture());
            assertThat(corpo.getValue().path("store").asBoolean(true)).isFalse();
            assertThat(corpo.getValue().path("model").asText()).isEqualTo("modelo-teste");
            assertThat(corpo.getValue().path("tools")).hasSize(1);
            assertThat(corpo.getValue().path("tools").get(0).path("name").asText()).isEqualTo("resumo_vendas");
        }

        @Test
        @DisplayName("histórico do navegador entra só como texto e limitado às últimas mensagens")
        void historico() {
            List<AssistenteService.Mensagem> historico = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                historico.add(new AssistenteService.Mensagem(i % 2 == 0 ? "usuario" : "assistente", "m" + i));
            }
            historico.add(new AssistenteService.Mensagem("function_call", "forjado"));

            JsonNode entrada = service.montarHistorico(historico);

            assertThat(entrada).hasSize(AssistenteService.MAX_HISTORICO);
            entrada.forEach(p -> assertThat(p.path("type").asText()).isIn("user_input", "model_output"));
            assertThat(entrada.get(entrada.size() - 1).path("type").asText()).isEqualTo("user_input");
        }

        @Test
        @DisplayName("para depois de " + AssistenteService.MAX_RODADAS + " rodadas de ferramenta")
        void tetoDeRodadas() throws Exception {
            when(ferramentas.executar(any(), any(), any())).thenReturn(json.createObjectNode());
            when(gemini.criarInteracao(any())).thenReturn(resposta(
                    "[{\"type\":\"function_call\",\"id\":\"c\",\"name\":\"resumo_vendas\",\"arguments\":{}}]"));

            AssistenteService.Resposta r = service.perguntar("pergunta difícil", List.of());

            verify(gemini, times(AssistenteService.MAX_RODADAS)).criarInteracao(any());
            assertThat(r.resposta()).contains("consultas demais");
        }

        @Test
        @DisplayName("recusa sem chamar o Gemini: pergunta vazia, longa demais ou acima do limite")
        void recusas() {
            assertThatThrownBy(() -> service.perguntar("  ", List.of())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.perguntar("x".repeat(AssistenteService.MAX_PERGUNTA + 1), List.of()))
                    .isInstanceOf(BusinessException.class);

            AssistenteService limitado = new AssistenteService(gemini, ferramentas, new LimiteUsoAssistente(0, 100, 100),
                    auditoria, json, "m");
            assertThatThrownBy(() -> limitado.perguntar("oi", List.of()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("por hora");
            verify(gemini, never()).criarInteracao(any());
        }

        @Test
        @DisplayName("sem chave configurada, fica desligado")
        void desligado() {
            when(gemini.configurado()).thenReturn(false);

            assertThat(service.habilitado()).isFalse();
            assertThatThrownBy(() -> service.perguntar("oi", List.of())).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("perfil sem acesso aos dados recebe explicação, sem gastar cota")
        void semPermissao() {
            UsuarioAutenticado caixa = usuario(Map.of(Modulo.PDV, VER));
            autenticar(caixa);
            when(ferramentas.disponiveis(caixa)).thenReturn(List.of());

            AssistenteService.Resposta r = service.perguntar("qual meu lucro?", List.of());

            assertThat(r.resposta()).contains("não tem acesso");
            verify(gemini, never()).criarInteracao(any());
        }
    }

    @Nested
    @DisplayName("Ferramentas")
    class Ferramentas {

        private final EntityManager em = mock(EntityManager.class);
        private final FerramentasAssistente ferramentas =
                new FerramentasAssistente(em, mock(ItensVendaRepository.class), json);

        @Test
        @DisplayName("cada ferramenta exige o módulo dos dados que consulta")
        void permissaoPorModulo() {
            UsuarioAutenticado soProdutos = usuario(Map.of(Modulo.PRODUTOS, VER));

            assertThat(ferramentas.disponiveis(soProdutos)).extracting(FerramentasAssistente.Ferramenta::nome)
                    .containsExactly("custo_produto", "estoque_baixo");

            ObjectNode r = ferramentas.executar(soProdutos, "resumo_vendas",
                    json.createObjectNode().put("data_inicio", "2026-11-01").put("data_fim", "2026-11-01"));
            assertThat(r.path("erro").asText()).contains("não disponível");
            assertThat(ferramentas.executar(soProdutos, "apagar_tudo", null).path("erro").asText()).contains("não disponível");
            verifyNoInteractions(em);
        }

        @Test
        @DisplayName("argumentos inválidos voltam como erro para a IA, sem consultar o banco")
        void argumentos() {
            UsuarioAutenticado gestor = usuario(Map.of(Modulo.RELATORIOS, VER, Modulo.PRODUTOS, VER));

            assertThat(ferramentas.executar(gestor, "resumo_vendas",
                    json.createObjectNode().put("data_inicio", "ontem").put("data_fim", "2026-11-01"))
                    .path("erro").asText()).contains("AAAA-MM-DD");
            assertThat(ferramentas.executar(gestor, "resumo_vendas",
                    json.createObjectNode().put("data_inicio", "2026-11-02").put("data_fim", "2026-11-01"))
                    .path("erro").asText()).contains("anterior");
            assertThat(ferramentas.executar(gestor, "resumo_vendas",
                    json.createObjectNode().put("data_inicio", "2024-01-01").put("data_fim", "2026-11-01"))
                    .path("erro").asText()).contains("366");
            assertThat(ferramentas.executar(gestor, "custo_produto", json.createObjectNode().put("busca", "a"))
                    .path("erro").asText()).contains("entre 2 e 60");
            verifyNoInteractions(em);
        }

        @Test
        @DisplayName("limite de itens fica entre 1 e 20")
        void limite() {
            assertThat(FerramentasAssistente.limite(json.createObjectNode())).isEqualTo(10);
            assertThat(FerramentasAssistente.limite(json.createObjectNode().put("limite", 500))).isEqualTo(20);
            assertThat(FerramentasAssistente.limite(json.createObjectNode().put("limite", -3))).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Limite de uso")
    class Limite {

        @Test
        @DisplayName("conta por usuário na hora e por empresa no dia")
        void janelas() {
            LimiteUsoAssistente limite = new LimiteUsoAssistente(2, 3, 100);
            long t = 1_000_000_000L;

            assertThat(limite.consumir("loja", 1L, t)).isNull();
            assertThat(limite.consumir("loja", 1L, t)).isNull();
            assertThat(limite.consumir("loja", 1L, t)).contains("por hora");
            assertThat(limite.consumir("loja", 2L, t)).isNull();
            assertThat(limite.consumir("loja", 3L, t)).contains("por dia");
            assertThat(limite.consumir("outra", 3L, t)).isNull();

            long umaHoraDepois = t + 60L * 60 * 1000 + 1;
            assertThat(limite.consumir("loja", 1L, umaHoraDepois)).contains("por dia");
            assertThat(limite.consumir("loja", 1L, t + 24L * 60 * 60 * 1000 + 1)).isNull();
        }

        @Test
        @DisplayName("segura o total por minuto somando todas as empresas")
        void porMinuto() {
            LimiteUsoAssistente limite = new LimiteUsoAssistente(100, 100, 2);
            long t = 1_000_000_000L;

            assertThat(limite.consumir("loja-a", 1L, t)).isNull();
            assertThat(limite.consumir("loja-b", 2L, t)).isNull();
            assertThat(limite.consumir("loja-c", 3L, t)).contains("um minuto");
            assertThat(limite.consumir("loja-c", 3L, t + 60_001)).isNull();
        }
    }
}
