package com.acougue.modules.fiscal.saneamento;

import com.acougue.entity.Cliente;
import com.acougue.entity.Fornecedor;
import com.acougue.entity.Produto;
import com.acougue.modules.fiscal.emissao.ConfiguracaoFiscalService;
import com.acougue.modules.fiscal.emissao.dto.ConfiguracaoFiscalDTO;
import com.acougue.repository.ClienteRepository;
import com.acougue.repository.FornecedorRepository;
import com.acougue.repository.ProdutoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Saneamento cadastral fiscal")
class SaneamentoCadastralServiceTest {

    private final ConfiguracaoFiscalService configuracao = mock(ConfiguracaoFiscalService.class);
    private final ProdutoRepository produtoRepo = mock(ProdutoRepository.class);
    private final ClienteRepository clienteRepo = mock(ClienteRepository.class);
    private final FornecedorRepository fornecedorRepo = mock(FornecedorRepository.class);
    private final SaneamentoCadastralService service =
            new SaneamentoCadastralService(configuracao, produtoRepo, clienteRepo, fornecedorRepo);

    private void config(short regime, List<String> pendenciasNfce, List<String> pendenciasNfe) {
        ConfiguracaoFiscalDTO dto = mock(ConfiguracaoFiscalDTO.class);
        when(dto.regimeTributario()).thenReturn(regime);
        when(dto.pendenciasNfce()).thenReturn(pendenciasNfce);
        when(dto.pendenciasNfe()).thenReturn(pendenciasNfe);
        when(configuracao.buscar()).thenReturn(dto);
    }

    private static SaneamentoCadastralService.Grupo grupo(SaneamentoCadastralService.Saneamento s, String codigo) {
        return s.grupos().stream().filter(g -> g.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("conta só o que está errado de verdade, com o motivo de cada registro")
    void contaPendenciasReais() {
        config((short) 1, List.of("Informe um CNPJ válido.", "Envie o certificado A1."), List.of("Informe um CNPJ válido."));
        when(produtoRepo.ativosComDadosFiscaisIncompletos()).thenReturn(List.of(
                Produto.builder().id(1L).nome("Linguiça").cfop("5102").csosn("102").build(),
                Produto.builder().id(2L).nome("Picanha").ncm("02013000").build()));
        when(clienteRepo.findByAtivoTrue()).thenReturn(List.of(
                Cliente.builder().id(1L).nome("Ok PF").tipoPessoa("PF").cpfCnpj("529.982.247-25").build(),
                Cliente.builder().id(2L).nome("Errado").tipoPessoa("PF").cpfCnpj("529.982.247-24").build(),
                Cliente.builder().id(3L).nome("Sem doc").tipoPessoa("PF").build(),
                Cliente.builder().id(4L).nome("Alfanumérico").tipoPessoa("PJ").cpfCnpj("12.ABC.345/01DE-35").build()));
        when(fornecedorRepo.findByAtivoTrueOrderByNomeAsc()).thenReturn(List.of(
                Fornecedor.builder().id(1L).nome("Frigorífico").tipoPessoa("PJ").cpfCnpj("11.222.333/0001-81").build(),
                Fornecedor.builder().id(2L).nome("Sem CNPJ").tipoPessoa("PJ").build(),
                Fornecedor.builder().id(3L).nome("CNPJ errado").tipoPessoa("PJ").cpfCnpj("11.222.333/0001-82").build()));

        SaneamentoCadastralService.Saneamento s = service.calcular();

        assertThat(grupo(s, "EMITENTE").quantidade()).isEqualTo(2); // pendência repetida entre NFC-e e NF-e conta uma vez
        SaneamentoCadastralService.Grupo produtos = grupo(s, "PRODUTO_FISCAL");
        assertThat(produtos.quantidade()).isEqualTo(2);
        assertThat(produtos.exemplos()).extracting(SaneamentoCadastralService.Registro::detalhe)
                .containsExactly("Falta NCM", "Falta CFOP, CSOSN");
        assertThat(grupo(s, "CLIENTE_DOCUMENTO").exemplos()).extracting(SaneamentoCadastralService.Registro::nome).containsExactly("Errado");
        assertThat(grupo(s, "FORNECEDOR_DOCUMENTO").exemplos()).extracting(SaneamentoCadastralService.Registro::nome).containsExactly("CNPJ errado");
        assertThat(grupo(s, "FORNECEDOR_SEM_DOCUMENTO").exemplos()).extracting(SaneamentoCadastralService.Registro::nome).containsExactly("Sem CNPJ");
        assertThat(s.totalPendencias()).isEqualTo(2 + 2 + 1 + 1 + 1);
    }

    @Test
    @DisplayName("regime normal não cobra CSOSN; a lista de exemplos é limitada, a contagem não")
    void regimeNormalELimite() {
        config((short) 3, List.of(), List.of());
        List<Produto> muitos = new java.util.ArrayList<>();
        for (long i = 0; i < 30; i++) muitos.add(Produto.builder().id(i).nome("P" + i).cfop("5102").build());
        muitos.add(Produto.builder().id(99L).nome("Só sem CSOSN").ncm("02013000").cfop("5102").build());
        when(produtoRepo.ativosComDadosFiscaisIncompletos()).thenReturn(muitos);
        when(clienteRepo.findByAtivoTrue()).thenReturn(List.of());
        when(fornecedorRepo.findByAtivoTrueOrderByNomeAsc()).thenReturn(List.of());

        SaneamentoCadastralService.Grupo produtos = grupo(service.calcular(), "PRODUTO_FISCAL");

        assertThat(produtos.quantidade()).isEqualTo(30);
        assertThat(produtos.exemplos()).hasSize(SaneamentoCadastralService.EXEMPLOS);
        assertThat(produtos.exemplos()).allMatch(r -> r.detalhe().equals("Falta NCM"));
    }
}
