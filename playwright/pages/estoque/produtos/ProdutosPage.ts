import { Page, Locator } from '@playwright/test'
import { ProdutoFormModal, type DadosProduto } from './ProdutoFormModal'

export class ProdutosPage {
    readonly btnNovoProduto: Locator
    readonly form: ProdutoFormModal

    constructor(private readonly page: Page) {
        this.btnNovoProduto = page.getByRole('button', { name: 'Novo Produto' })
        this.form = new ProdutoFormModal(page)
    }

    async abrir() {
        await this.page.goto('/estoque/produtos')
    }

    linha(nome: string): Locator {
        return this.page.getByRole('row').filter({ hasText: nome })
    }

    async inativar(nome: string) {
        await this.linha(nome).getByRole('button', { name: 'Inativar produto' }).click()
    }

    async cadastrar(dados: DadosProduto) {
        await this.btnNovoProduto.click()
        await this.form.preencher(dados)
        await this.form.salvar()
    }
}
