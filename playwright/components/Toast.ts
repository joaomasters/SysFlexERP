import { Page, Locator } from '@playwright/test'

// Toasts do react-hot-toast (role="status")
export class Toast {
    constructor(private readonly page: Page) {}

    comMensagem(mensagem: string): Locator {
        return this.page.getByRole('status').filter({ hasText: mensagem })
    }
}
