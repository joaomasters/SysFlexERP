package com.acougue.modules.fiscal.emissao.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SalvarConfiguracaoFiscalDTO {

    private boolean emitirNfce;
    private boolean emitirNfe;

    @NotNull
    @Pattern(regexp = "HOMOLOGACAO|PRODUCAO", message = "Ambiente deve ser HOMOLOGACAO ou PRODUCAO")
    private String ambiente;

    @NotBlank
    @Size(max = 30)
    private String provedor;

    @Size(max = 150) private String razaoSocial;
    @Size(max = 120) private String nomeFantasia;
    @Size(max = 20)  private String cnpj;
    @Size(max = 20)  private String inscricaoEstadual;

    @Min(1) @Max(4)
    private short regimeTributario = 1;

    @Size(max = 120) private String logradouro;
    @Size(max = 10)  private String numeroEndereco;
    @Size(max = 60)  private String complemento;
    @Size(max = 60)  private String bairro;
    @Size(max = 10)  private String codigoMunicipio;
    @Size(max = 60)  private String municipio;
    @Size(max = 10)  private String cep;
    @Size(max = 20)  private String telefone;

    @Min(0) @Max(999)       private short nfceSerie = 1;
    @Min(1) @Max(999999999) private int nfceProximoNumero = 1;
    @Min(0) @Max(999)       private short nfeSerie = 1;
    @Min(1) @Max(999999999) private int nfeProximoNumero = 1;

    /** SOB_DEMANDA: só quando o cliente pede ("CPF na nota?"); TODAS: em toda venda. */
    @Pattern(regexp = "SOB_DEMANDA|TODAS", message = "Modo da NFC-e deve ser SOB_DEMANDA ou TODAS")
    private String nfceModo = "SOB_DEMANDA";

    @Size(max = 6) private String cscId;
    /** Em branco = mantém o código já cadastrado. */
    @Size(max = 100) private String cscToken;
    private boolean limparCsc;
}
