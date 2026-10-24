import { Page, Locator } from '@playwright/test'

export interface DadosProduto {
    nome: string
    unidade?: 'KG' | 'UN' | 'CX' | 'G'
    tipo?: 'CORTE' | 'INDUSTRIALIZADO' | 'INSUMO' | 'SUBPRODUTO'
    pluBalanca?: string
    precoVenda: string
    precoCusto?: string
    estoqueMinimo?: string
    marca?: string
    validadeDias?: string
    ean13?: string
}

// Modal "Novo Produto" / "Editar Produto" (ProdutoForm do frontend)
export class ProdutoFormModal {
    readonly nome: Locator
    readonly unidade: Locator
    readonly tipo: Locator
    readonly pluBalanca: Locator
    readonly precoVenda: Locator
    readonly precoCusto: Locator
    readonly estoqueMinimo: Locator
    readonly marca: Locator
    readonly validadeDias: Locator
    readonly ean13: Locator
    readonly btnCancelar: Locator
    readonly btnSalvar: Locator

    constructor(private readonly page: Page) {
        this.nome = page.locator('input[name="nome"]')
        this.unidade = page.locator('select[name="unidadeMedida"]')
        this.tipo = page.locator('select[name="tipoProduto"]')
        this.pluBalanca = page.locator('input[name="codigoBalanca"]')
        this.precoVenda = page.locator('input[name="precoVenda"]')
        this.precoCusto = page.locator('input[name="precoCusto"]')
        this.estoqueMinimo = page.locator('input[name="estoqueMinimo"]')
        this.marca = page.locator('input[name="marca"]')
        this.validadeDias = page.locator('input[name="validadePadraoDias"]')
        this.ean13 = page.locator('input[name="ean13"]')
        this.btnCancelar = page.getByRole('button', { name: 'Cancelar', exact: true })
        this.btnSalvar = page.getByRole('button', { name: 'Salvar Produto' })
    }

    titulo(texto: 'Novo Produto' | 'Editar Produto'): Locator {
        return this.page.getByRole('heading', { name: texto })
    }

    async preencher(dados: DadosProduto) {
        // Unidade primeiro: KG/G mostram o PLU da balança, UN/CX mostram a marca
        if (dados.unidade) await this.unidade.selectOption(dados.unidade)
        if (dados.tipo) await this.tipo.selectOption(dados.tipo)
        await this.nome.fill(dados.nome)
        await this.precoVenda.fill(dados.precoVenda)
        if (dados.pluBalanca) await this.pluBalanca.fill(dados.pluBalanca)
        if (dados.precoCusto) await this.precoCusto.fill(dados.precoCusto)
        if (dados.estoqueMinimo) await this.estoqueMinimo.fill(dados.estoqueMinimo)
        if (dados.marca) await this.marca.fill(dados.marca)
        if (dados.validadeDias) await this.validadeDias.fill(dados.validadeDias)
        if (dados.ean13) await this.ean13.fill(dados.ean13)
    }

    async salvar() {
        await this.btnSalvar.click()
    }

    async cancelar() {
        await this.btnCancelar.click()
    }
}
