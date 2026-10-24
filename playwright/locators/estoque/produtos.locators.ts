const produtosLocators = {
    btnNovoProduto: 'button:has-text("Novo Produto")',
    novoProduto: {
        nome: 'input[name="nome"]',
        unidade: 'select[name="unidadeMedida"]',
        tipo: 'select[name="tipoProduto"]',
        pluBalanca: 'input[name="codigoBalanca"]',
        precoVenda: 'input[name="precoVenda"]',
        precoCusto: 'input[name="precoCusto"]',
        estoqueMinimo: 'input[name="estoqueMinimo"]',
        fornecedor: '',
        ean13: 'input[name="ean13"]',
        cancelar: 'button[type="button"]',
        salvar: 'button[type="submit"]'
    }
}

export default produtosLocators