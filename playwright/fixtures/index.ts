import { test as base } from '@playwright/test'
import { Toast } from '../components/Toast'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ProdutosPage } from '../pages/estoque/produtos/ProdutosPage'
import { ProdutosApi } from '../api/estoque/produtos/ProdutosApi'

type Fixtures = {
    toast: Toast
    confirmDialog: ConfirmDialog
    produtosPage: ProdutosPage
    produtosApi: ProdutosApi
}

// Os testes importam `test` daqui e recebem as páginas prontas como parâmetro
export const test = base.extend<Fixtures>({
    toast: async ({ page }, use) => use(new Toast(page)),
    confirmDialog: async ({ page }, use) => use(new ConfirmDialog(page)),
    produtosPage: async ({ page }, use) => use(new ProdutosPage(page)),
    produtosApi: async ({ request }, use) => use(new ProdutosApi(request)),
})

export { expect } from '@playwright/test'
