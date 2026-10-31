package com.acougue.modules.balanca;

import com.acougue.exception.InvalidBarcodeException;
import com.acougue.modules.balanca.dto.EanParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EanBalancaParser")
class EanBalancaParserTest {

    private final EanBalancaParser parser = new EanBalancaParser();



    @Test
    @DisplayName("VALOR_TOTAL: valor e peso calculados corretamente")
    void formatoValorTotal_retornaValorEPeso() {

        EanParseResult result = parser.parse("2000100012505", new BigDecimal("25.00"));

        assertThat(result.getCodigoBalanca()).isEqualTo(100);
        assertThat(result.getValorTotal()).isEqualByComparingTo("12.50");
        assertThat(result.getPesoKg()).isEqualByComparingTo("0.500");
        assertThat(result.getFormato()).isEqualTo(EanBalancaParser.FormatoEtiqueta.VALOR_TOTAL);
    }

    @Test
    @DisplayName("VALOR_TOTAL: etiqueta real 2 000800 00968 3 (R$ 9,68 a R$ 43,99/kg = 0,220 kg)")
    void formatoValorTotal_etiquetaReal() {
        EanParseResult result = parser.parse("2000800009683", new BigDecimal("43.99"));

        assertThat(result.getCodigoBalanca()).isEqualTo(800);
        assertThat(result.getValorTotal()).isEqualByComparingTo("9.68");
        assertThat(result.getPesoKg()).isEqualByComparingTo("0.220");
    }

    @Test
    @DisplayName("VALOR_TOTAL: o peso lido é exatamente o da etiqueta, grama a grama, sem arredondar para mais ou menos")
    void formatoValorTotal_pesoExatoGramaAGrama() {
        for (String preco : new String[]{"43.99", "10.01", "12.50", "89.90", "199.99"}) {
            BigDecimal precoKg = new BigDecimal(preco);
            for (int gramas = 1; gramas <= 30_000; gramas++) {
                BigDecimal peso = BigDecimal.valueOf(gramas, 3);
                // valor que a balança imprime: peso x preço, arredondado em centavos
                BigDecimal valor = peso.multiply(precoKg).setScale(2, java.math.RoundingMode.HALF_UP);
                if (valor.compareTo(new BigDecimal("999.99")) > 0) break;   // campo de 5 dígitos
                String base = "2000800" + String.format("%05d", valor.movePointRight(2).intValueExact());
                String ean = base + dv(base);

                EanParseResult r = parser.parse(ean, precoKg);

                assertThat(r.getPesoKg()).as("preço %s, %s g", preco, gramas).isEqualByComparingTo(peso);
                assertThat(r.getValorTotal()).as("valor da etiqueta preservado").isEqualByComparingTo(valor);
            }
        }
    }

    /** Dígito verificador EAN-13 (para montar etiquetas de teste). */
    private static int dv(String doze) {
        int soma = 0;
        for (int i = 0; i < 12; i++) soma += (doze.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return (10 - soma % 10) % 10;
    }

    @Test
    @DisplayName("VALOR_TOTAL: escolhido automaticamente quando precoKg está informado")
    void formatoValorTotal_escolhidoAutomaticamente_quandoPrecoKgPresente() {
        EanParseResult result = parser.parse("2000100012505", new BigDecimal("50.00"));
        assertThat(result.getFormato()).isEqualTo(EanBalancaParser.FormatoEtiqueta.VALOR_TOTAL);
    }

    @Test
    @DisplayName("VALOR_TOTAL: lança exceção quando precoKg é nulo")
    void formatoValorTotal_lancaExcecao_quandoPrecoKgNulo() {
        assertThatThrownBy(() ->
                parser.parse("2000100012505", null, EanBalancaParser.FormatoEtiqueta.VALOR_TOTAL)
        ).isInstanceOf(InvalidBarcodeException.class)
                .hasMessageContaining("Preço/kg inválido");
    }

    @Test
    @DisplayName("VALOR_TOTAL: lança exceção quando precoKg é zero")
    void formatoValorTotal_lancaExcecao_quandoPrecoKgZero() {
        assertThatThrownBy(() ->
                parser.parse("2000100012505", BigDecimal.ZERO, EanBalancaParser.FormatoEtiqueta.VALOR_TOTAL)
        ).isInstanceOf(InvalidBarcodeException.class);
    }



    @Test
    @DisplayName("PESO_GRAMAS: peso e valor calculados corretamente")
    void formatoPesoGramas_retornaPesoEValor() {

        EanParseResult result = parser.parse("2000200015000", new BigDecimal("30.00"),
                EanBalancaParser.FormatoEtiqueta.PESO_GRAMAS);

        assertThat(result.getCodigoBalanca()).isEqualTo(200);
        assertThat(result.getPesoKg()).isEqualByComparingTo("1.500");
        assertThat(result.getValorTotal()).isEqualByComparingTo("45.00");
        assertThat(result.getFormato()).isEqualTo(EanBalancaParser.FormatoEtiqueta.PESO_GRAMAS);
    }

    @Test
    @DisplayName("PESO_GRAMAS: valorTotal é nulo quando precoKg não informado")
    void formatoPesoGramas_valorTotalNulo_semPrecoKg() {
        EanParseResult result = parser.parse("2000200015000", null,
                EanBalancaParser.FormatoEtiqueta.PESO_GRAMAS);

        assertThat(result.getPesoKg()).isEqualByComparingTo("1.500");
        assertThat(result.getValorTotal()).isNull();
    }

    @Test
    @DisplayName("PESO_GRAMAS: escolhido automaticamente quando precoKg é nulo")
    void formatoPesoGramas_escolhidoAutomaticamente_semPrecoKg() {
        EanParseResult result = parser.parse("2000200015000", null);
        assertThat(result.getFormato()).isEqualTo(EanBalancaParser.FormatoEtiqueta.PESO_GRAMAS);
    }



    @Test
    @DisplayName("Lança exceção para EAN com menos de 13 dígitos")
    void lancaExcecao_eanMenorQue13Digitos() {
        assertThatThrownBy(() -> parser.parse("200010001250", BigDecimal.TEN))
                .isInstanceOf(InvalidBarcodeException.class)
                .hasMessageContaining("13 dígitos");
    }

    @Test
    @DisplayName("Lança exceção para EAN com mais de 13 dígitos")
    void lancaExcecao_eanMaiorQue13Digitos() {
        assertThatThrownBy(() -> parser.parse("20001000125055", BigDecimal.TEN))
                .isInstanceOf(InvalidBarcodeException.class)
                .hasMessageContaining("13 dígitos");
    }

    @Test
    @DisplayName("Lança exceção para EAN com letras")
    void lancaExcecao_eanComLetras() {
        assertThatThrownBy(() -> parser.parse("2000100012AB5", BigDecimal.TEN))
                .isInstanceOf(InvalidBarcodeException.class);
    }

    @Test
    @DisplayName("Lança exceção para EAN nulo")
    void lancaExcecao_eanNulo() {
        assertThatThrownBy(() -> parser.parse(null, BigDecimal.TEN))
                .isInstanceOf(InvalidBarcodeException.class);
    }

    @Test
    @DisplayName("Lança exceção para dígito verificador incorreto")
    void lancaExcecao_digitoVerificadorIncorreto() {

        assertThatThrownBy(() -> parser.parse("2000100012509", BigDecimal.TEN))
                .isInstanceOf(InvalidBarcodeException.class)
                .hasMessageContaining("Dígito verificador");
    }

    @Test
    @DisplayName("Lança exceção para EAN que não começa com '2'")
    void lancaExcecao_eanNaoComecaCom2() {

        assertThatThrownBy(() ->
                parser.parse("1234567890128", BigDecimal.TEN, EanBalancaParser.FormatoEtiqueta.VALOR_TOTAL)
        ).isInstanceOf(InvalidBarcodeException.class)
                .hasMessageContaining("não é de balança");
    }



    @Test
    @DisplayName("Formato forçado PESO_GRAMAS prevalece sobre presença de precoKg")
    void formatoForcado_pesoGramas_prevaleceSobrePrecoKg() {

        EanParseResult result = parser.parse("2000200015000", new BigDecimal("30.00"),
                EanBalancaParser.FormatoEtiqueta.PESO_GRAMAS);
        assertThat(result.getFormato()).isEqualTo(EanBalancaParser.FormatoEtiqueta.PESO_GRAMAS);
        assertThat(result.getPesoKg()).isEqualByComparingTo("1.500");
    }
}