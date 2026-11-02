import { Page, Locator } from '@playwright/test'

// Espelha TIPOS_PRODUTO de frontend/src/types/produto.ts (mesma ordem do formulário)
export const TIPOS_PRODUTO = [
    { valor: 'CORTE', nome: 'Corte de Carne' },
    { valor: 'BEBIDA', nome: 'Bebida' },
    { valor: 'ALIMENTO', nome: 'Alimento' },
    { valor: 'MERCEARIA', nome: 'Mercearia' },
    { valor: 'HORTIFRUTI', nome: 'Hortifruti' },
    { valor: 'FRIOS_LATICINIOS', nome: 'Frios e Laticínios' },
    { valor: 'PADARIA', nome: 'Padaria' },
    { valor: 'CONGELADOS', nome: 'Congelados' },
    { valor: 'LIMPEZA', nome: 'Limpeza' },
    { valor: 'HIGIENE', nome: 'Higiene' },
    { valor: 'INDUSTRIALIZADO', nome: 'Industrializado' },
    { valor: 'INSUMO', nome: 'Insumo' },
    { valor: 'SUBPRODUTO', nome: 'Subproduto (sebo, osso...)' },
    { valor: 'OUTROS', nome: 'Outros' },
] as const

export type TipoProduto = (typeof TIPOS_PRODUTO)[number]['valor']

export interface DadosProduto {
    nome: string
    precoVenda: string
    // Obrigatório no cadastro novo; sem ele a tela mostra "Informe o código de barras."
    ean13?: string
    codigoInterno?: string
    unidade?: 'KG' | 'UN' | 'CX' | 'G'
    tipo?: TipoProduto
    pluBalanca?: string
    precoCusto?: string
    estoqueMinimo?: string
    estoqueAtual?: string
    marca?: string
    validadeDias?: string
}

// Tela "Novo Produto" / "Editar Produto" (ProdutoCadastroPage do frontend, rotas /produtos/novo e /produtos/:id/editar)
export class ProdutoCadastroPage {
    readonly abaBasico: Locator
    readonly abaFiscal: Locator
    readonly codigoInterno: Locator
    readonly ean13: Locator
    readonly nome: Locator
    readonly unidade: Locator
    readonly tipo: Locator
    readonly opcoesTipo: Locator
    readonly pluBalanca: Locator
    readonly precoVenda: Locator
    readonly precoCusto: Locator
    readonly estoqueMinimo: Locator
    readonly estoqueAtual: Locator
    readonly marca: Locator
    readonly validadeDias: Locator
    readonly btnCancelar: Locator
    readonly btnSalvar: Locator

    constructor(private readonly page: Page) {
        this.abaBasico = page.getByRole('tab', { name: 'Informações Básicas' })
        this.abaFiscal = page.getByRole('tab', { name: 'Informações Fiscais' })
        this.codigoInterno = page.locator('input[name="codigoInterno"]')
        this.ean13 = page.locator('input[name="ean13"]')
        this.nome = page.locator('input[name="nome"]')
        this.unidade = page.locator('select[name="unidadeMedida"]')
        this.tipo = page.locator('select[name="tipoProduto"]')
        this.opcoesTipo = this.tipo.getByRole('option')
        this.pluBalanca = page.locator('input[name="codigoBalanca"]')
        this.precoVenda = page.locator('input[name="precoVenda"]')
        this.precoCusto = page.locator('input[name="precoCusto"]')
        this.estoqueMinimo = page.locator('input[name="estoqueMinimo"]')
        this.estoqueAtual = page.locator('input[name="estoqueAtual"]')
        this.marca = page.locator('input[name="marca"]')
        this.validadeDias = page.locator('input[name="validadePadraoDias"]')
        this.btnCancelar = page.getByRole('button', { name: 'Cancelar', exact: true })
        this.btnSalvar = page.getByRole('button', { name: 'Salvar Produto' })
    }

    async abrir() {
        await this.page.goto('/produtos/novo')
    }

    titulo(texto: 'Novo Produto' | 'Editar Produto'): Locator {
        return this.page.getByRole('heading', { name: texto, level: 1 })
    }

    erro(texto: string): Locator {
        return this.page.getByText(texto, { exact: true })
    }

    async preencher(dados: DadosProduto) {
        // Unidade primeiro: KG/G habilitam o PLU da balança, UN/CX habilitam a marca
        if (dados.unidade) await this.unidade.selectOption(dados.unidade)
        if (dados.tipo) await this.tipo.selectOption(dados.tipo)
        if (dados.codigoInterno) await this.codigoInterno.fill(dados.codigoInterno)
        if (dados.ean13) await this.ean13.fill(dados.ean13)
        await this.nome.fill(dados.nome)
        // CurrencyInput formata a partir dos centavos: "1000" vira R$ 10,00
        await this.precoVenda.fill(dados.precoVenda)
        if (dados.pluBalanca) await this.pluBalanca.fill(dados.pluBalanca)
        if (dados.precoCusto) await this.precoCusto.fill(dados.precoCusto)
        if (dados.estoqueMinimo) await this.estoqueMinimo.fill(dados.estoqueMinimo)
        if (dados.estoqueAtual) await this.estoqueAtual.fill(dados.estoqueAtual)
        if (dados.marca) await this.marca.fill(dados.marca)
        if (dados.validadeDias) await this.validadeDias.fill(dados.validadeDias)
    }

    async salvar() {
        await this.btnSalvar.click()
    }

    // Com o formulário alterado, abre o ConfirmDialog "Descartar cadastro?"
    async cancelar() {
        await this.btnCancelar.click()
    }
}
