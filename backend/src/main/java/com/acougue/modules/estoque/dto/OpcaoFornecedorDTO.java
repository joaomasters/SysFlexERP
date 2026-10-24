package com.acougue.modules.estoque.dto;

/** Item do dropdown de fornecedores no cadastro de produto — só id e nome, sem dados cadastrais. */
public record OpcaoFornecedorDTO(Long id, String nome) {}