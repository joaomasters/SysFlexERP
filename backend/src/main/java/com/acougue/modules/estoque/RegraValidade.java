package com.acougue.modules.estoque;

import com.acougue.exception.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class RegraValidade {

    public static final int MAX_ANOS = 3;

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private RegraValidade() {}

    public static LocalDate limite(LocalDate dataEntrada) {
        return dataEntrada.plusYears(MAX_ANOS);
    }

    public static void validarLimite(LocalDate dataValidade, LocalDate dataEntrada) {
        if (dataValidade == null) {
            return;
        }
        LocalDate limite = limite(dataEntrada);
        if (dataValidade.isAfter(limite)) {
            throw new BusinessException(String.format(
                    "Data de validade %s é superior a %d anos da data de entrada. Máximo permitido: %s.",
                    dataValidade.format(BR), MAX_ANOS, limite.format(BR)));
        }
    }
}