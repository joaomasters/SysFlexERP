import { Page, Locator } from '@playwright/test'
import { ProdutoCadastroPage, type DadosProduto } from './ProdutoCadastroPage'
import { RelatorioEstoqueModal } from './RelatorioEstoqueModal'

// Listagem de produtos (ProdutosPage do frontend, rota /produtos)
export class ProdutosPage {
    readonly titulo: Locator
    readonly btnNovoProduto: Locator
    readonly btnRelatorio: Locator
    readonly busca: Locator
    readonly btnLimparBusca: Locator
    readonly tabela: Locator
    readonly cadastro: ProdutoCadastroPage
    readonly relatorio: RelatorioEstoqueModal

    constructor(private readonly page: Page) {
        this.titulo = page.getByRole('heading', { name: 'Produtos', level: 1 })
        this.btnNovoProduto = page.getByRole('button', { name: 'Novo Produto' })
        this.btnRelatorio = page.getByRole('button', { name: 'Relatório de Estoque' })
        this.busca = page.getByRole('searchbox', { name: 'Buscar produtos por nome, código ou código de barras' })
        this.btnLimparBusca = page.getByRole('button', { name: 'Limpar busca' })
        this.tabela = page.getByRole('table')
        this.cadastro = new ProdutoCadastroPage(page)
        this.relatorio = new RelatorioEstoqueModal(page)
    }

    async abrir() {
        await this.page.goto('/produtos')
    }

    linha(nome: string): Locator {
        return this.page.getByRole('row').filter({ hasText: nome })
    }

    nenhumEncontrado(termo: string): Locator {
        return this.page.getByText(`Nenhum produto encontrado para "${termo}".`, { exact: true })
    }

    async buscar(termo: string) {
        await this.busca.fill(termo)
    }

    // A listagem é paginada (10 por página): busca antes para o produto estar visível
    async editar(nome: string) {
        await this.buscar(nome)
        await this.linha(nome).getByRole('button', { name: 'Editar produto' }).click()
    }

    async inativar(nome: string) {
        await this.buscar(nome)
        await this.linha(nome).getByRole('button', { name: 'Inativar produto' }).click()
    }

    // Leitor USB: digita muito rápido e termina com Enter
    async bipar(codigo: string) {
        const focoEmCampo = await this.page.evaluate(() =>
            ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName ?? ''))
        if (focoEmCampo) {
            // Com o foco num campo os dígitos entram direto nele; o tempo entre teclas não importa
            await this.page.keyboard.type(codigo)
            await this.page.keyboard.press('Enter')
            return
        }
        // Foco fora: o useBarcodeScan descarta a leitura se passar de 80 ms entre teclas, e o
        // keyboard.type pode atrasar com vários workers. Dispara todas as teclas de uma vez no navegador
        await this.page.evaluate(c => {
            for (const key of [...c, 'Enter']) {
                document.body.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }))
            }
        }, codigo)
    }

    async abrirRelatorio() {
        await this.btnRelatorio.click()
    }

    async cadastrar(dados: DadosProduto) {
        await this.btnNovoProduto.click()
        await this.cadastro.preencher(dados)
        await this.cadastro.salvar()
    }
}
