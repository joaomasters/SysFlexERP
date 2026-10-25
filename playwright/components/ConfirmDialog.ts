import { Page, Locator } from '@playwright/test'

// ConfirmDialog do frontend: usado para inativar, excluir e cancelar em várias telas
export class ConfirmDialog {
    readonly btnCancelar: Locator

    constructor(private readonly page: Page) {
        this.btnCancelar = page.getByRole('button', { name: 'Cancelar', exact: true })
    }

    titulo(texto: string): Locator {
        return this.page.getByRole('heading', { name: texto })
    }

    mensagem(texto: string): Locator {
        return this.page.getByText(texto, { exact: true })
    }

    async confirmar(rotulo = 'Confirmar') {
        await this.page.getByRole('button', { name: rotulo, exact: true }).click()
    }

    async cancelar() {
        await this.btnCancelar.click()
    }
}
