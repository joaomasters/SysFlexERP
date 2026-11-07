package com.acougue.security.auditoria;

import com.acougue.security.Acao;
import com.acougue.security.ExigirPermissao;
import com.acougue.entity.Modulo;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditoriaAspect")
class AuditoriaAspectTest {

    @Mock AuditoriaService auditoriaService;
    @Mock JoinPoint joinPoint;
    @Mock Signature signature;

    private AuditoriaAspect aspect;

    private ExigirPermissao permissao(Modulo modulo, Acao acao) {
        ExigirPermissao anotacao = mock(ExigirPermissao.class);
        lenient().when(anotacao.modulo()).thenReturn(modulo);
        lenient().when(anotacao.acao()).thenReturn(acao);
        return anotacao;
    }

    @Test
    @DisplayName("nunca audita ações de VER, mesmo com retorno/args válidos")
    void ignoraAcoesDeVer() {
        aspect = new AuditoriaAspect(auditoriaService);
        ExigirPermissao anotacao = permissao(Modulo.CLIENTES, Acao.VER);

        aspect.registrar(joinPoint, anotacao, ResponseEntity.ok("qualquer coisa"));

        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("EDITAR: usa o Long encontrado nos argumentos (@PathVariable id)")
    void editarUsaIdDosArgumentos() {
        aspect = new AuditoriaAspect(auditoriaService);
        ExigirPermissao anotacao = permissao(Modulo.CLIENTES, Acao.EDITAR);
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L, "dados irrelevantes"});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("atualizar");

        aspect.registrar(joinPoint, anotacao, ResponseEntity.ok().build());

        ArgumentCaptor<String> descricaoCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq("CLIENTES"), eq("EDITAR"), descricaoCaptor.capture());
        assertThat(descricaoCaptor.getValue()).isEqualTo("atualizar #42");
    }

    @Test
    @DisplayName("CRIAR: prioriza o id do recurso recém-criado (retorno), não um Long qualquer dos argumentos")
    void criarPriorizaIdDoRetorno() {
        aspect = new AuditoriaAspect(auditoriaService);
        ExigirPermissao anotacao = permissao(Modulo.FATURAMENTO, Acao.CRIAR);
        // gerarFechamento(clienteId, inicio, fim) — o clienteId (10L) NÃO é o
        // id do fechamento criado; o id de verdade só existe no retorno. Nem
        // chega a olhar os argumentos, então não precisa stubar getArgs().
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("gerarFechamento");

        FaturamentoFalso criado = new FaturamentoFalso(99L);
        aspect.registrar(joinPoint, anotacao, ResponseEntity.ok(criado));

        ArgumentCaptor<String> descricaoCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq("FATURAMENTO"), eq("CRIAR"), descricaoCaptor.capture());
        assertThat(descricaoCaptor.getValue()).isEqualTo("gerarFechamento #99");
    }

    @Test
    @DisplayName("CRIAR sem id no retorno cai pro id dos argumentos, se houver")
    void criarSemIdNoRetornoUsaArgumentoComoFallback() {
        aspect = new AuditoriaAspect(auditoriaService);
        ExigirPermissao anotacao = permissao(Modulo.PDV, Acao.CRIAR);
        when(joinPoint.getArgs()).thenReturn(new Object[]{5L});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("abrirCaixa");

        aspect.registrar(joinPoint, anotacao, ResponseEntity.ok().build()); // corpo vazio, sem getId()

        ArgumentCaptor<String> descricaoCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq("PDV"), eq("CRIAR"), descricaoCaptor.capture());
        assertThat(descricaoCaptor.getValue()).isEqualTo("abrirCaixa #5");
    }

    @Test
    @DisplayName("sem nenhum Long disponível, registra só o nome do método")
    void semIdDisponivelUsaSoNomeDoMetodo() {
        aspect = new AuditoriaAspect(auditoriaService);
        ExigirPermissao anotacao = permissao(Modulo.RELATORIOS, Acao.EXCLUIR);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"sem long aqui"});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("limparCache");

        aspect.registrar(joinPoint, anotacao, null);

        verify(auditoriaService).registrar("RELATORIOS", "EXCLUIR", "limparCache");
    }

    @Test
    @DisplayName("nunca deixa uma falha ao montar a descrição propagar e quebrar a ação real")
    void nuncaPropagaFalhaDeAuditoria() {
        aspect = new AuditoriaAspect(auditoriaService);
        ExigirPermissao anotacao = permissao(Modulo.CLIENTES, Acao.EDITAR);
        // getSignature() não é stubado de propósito — devolve null (padrão do
        // Mockito), o que faz jp.getSignature().getName() estourar NPE dentro
        // do aspecto. É exatamente esse tipo de falha inesperada que o
        // try/catch em registrar() precisa engolir sem propagar.

        assertThatCodeDoesNotThrow(() -> aspect.registrar(joinPoint, anotacao, null));

        verifyNoInteractions(auditoriaService);
    }

    private void assertThatCodeDoesNotThrow(Runnable r) {
        r.run(); // se lançar, o teste falha sozinho
    }

    /** Dublê simples só pra simular uma entidade recém-criada com getId(). */
    static class FaturamentoFalso {
        private final Long id;
        FaturamentoFalso(Long id) { this.id = id; }
        public Long getId() { return id; }
    }
}