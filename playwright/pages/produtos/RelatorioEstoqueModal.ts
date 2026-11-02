import { Page, Locator } from '@playwright/test'
import type { TipoProduto } from './ProdutoCadastroPage'

// Modal "Relatório de Estoque" aberto pela tela de Produtos (RelatorioEstoqueModal do frontend)
export class RelatorioEstoqueModal {
    readonly titulo: Locator
    readonly area: Locator
    readonly busca: Locator
    readonly tipo: Locator
    readonly ordenarPor: Locator
    readonly apenasAbaixoMinimo: Locator
    readonly btnImprimir: Locator
    readonly btnFechar: Locator
    readonly nenhumEncontrado: Locator

    constructor(private readonly page: Page) {
        this.titulo = page.getByRole('heading', { name: 'Relatório de Estoque' })
        // Sem papel nem test id: o id da área impressa é o único gancho estável
        this.area = page.locator('#relatorio-estoque-print')
        this.busca = page.getByPlaceholder('Buscar por nome, marca, fornecedor ou código de barras...')
        // Os rótulos "Tipo" e "Ordenar por" não são associados aos selects; identifica pelas opções
        this.tipo = page.getByRole('combobox').filter({ has: page.getByRole('option', { name: 'Corte de Carne' }) })
        this.ordenarPor = page.getByRole('combobox').filter({ has: page.getByRole('option', { name: 'Nome do produto' }) })
        this.apenasAbaixoMinimo = page.getByRole('checkbox', { name: 'Só abaixo do mínimo' })
        this.btnImprimir = page.getByRole('button', { name: 'Imprimir' })
        this.btnFechar = page.getByRole('button', { name: 'Fechar' })
        this.nenhumEncontrado = this.area.getByText('Nenhum produto encontrado.', { exact: true })
    }

    linha(nome: string): Locator {
        return this.area.getByRole('row').filter({ hasText: nome })
    }

    async buscar(termo: string) {
        await this.busca.fill(termo)
    }

    // '' volta para "Todos"
    async filtrarPorTipo(tipo: TipoProduto | '') {
        await this.tipo.selectOption(tipo)
    }

    async ordenar(campo: 'nome' | 'marca' | 'fornecedor') {
        await this.ordenarPor.selectOption(campo)
    }

    async fechar() {
        await this.btnFechar.click()
    }
}
