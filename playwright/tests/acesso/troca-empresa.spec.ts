import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'
import type { Page } from '@playwright/test'

const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
const senha = 'Senha@123456'

let empresa: { id: number; codigo: string }
const adminLogin = `admin${faker.string.alphanumeric(6).toLowerCase()}`

async function entrar(page: Page, codigo: string, usuario: string, senhaUsuario: string) {
    await page.getByPlaceholder('Ex: 001').fill(codigo)
    await page.getByPlaceholder('Digite seu usuário').fill(usuario)
    await page.getByPlaceholder('••••••').fill(senhaUsuario)
    await page.getByRole('button', { name: 'Entrar' }).click()
    await expect(page).toHaveURL(/\/inicio/)
}

// Pelo menu, sem recarregar a página: é assim que o cache antigo apareceria
async function irParaProdutos(page: Page) {
    await page.getByRole('navigation').getByRole('link', { name: 'Produtos' }).click()
    await expect(page.getByRole('heading', { name: 'Produtos' })).toBeVisible()
}

test.describe('Troca de empresa no mesmo navegador', () => {
    test.describe.configure({ timeout: 120_000 })

    test.beforeAll(async ({ request }) => {
        const resp = await request.post('/api/admin/tenants', {
            headers,
            timeout: 90_000,
            data: { nome: `Empresa ${faker.string.uuid()}`, adminNome: 'Admin', adminLogin, adminSenha: senha },
        })
        expect(resp.ok()).toBeTruthy()
        empresa = await resp.json()
        // Empresa nova nasce inativa
        await request.patch(`/api/admin/tenants/${empresa.id}/ativar`, { headers })
    })

    test.afterAll(async ({ request }) => {
        if (empresa) await request.patch(`/api/admin/tenants/${empresa.id}/desativar`, { headers })
    })

    test('Não deve mostrar dados da empresa anterior depois de sair e entrar em outra', async ({ page, produtosPage }) => {
        const nome = `Só da principal ${faker.string.uuid()}`
        const criado = await page.request.post('/api/estoque/produtos', {
            headers,
            data: { nome, unidadeMedida: 'UN', tipoProduto: 'BEBIDA', precoVenda: 10, estoqueMinimo: 0 },
        })
        expect(criado.ok()).toBeTruthy()

        await produtosPage.abrir()
        await expect(produtosPage.linha(nome)).toHaveCount(1)

        await page.getByRole('button', { name: 'Sair' }).click()
        await entrar(page, empresa.codigo, adminLogin, senha)
        await irParaProdutos(page)

        await expect(page.getByText('Nenhum produto encontrado.')).toBeVisible()
        await expect(produtosPage.linha(nome)).toHaveCount(0)
    })

    test('Aba aberta não deve continuar em outra empresa quando a sessão muda', async ({ page, context, produtosPage }) => {
        await produtosPage.abrir()
        await expect(page.locator('tbody tr').first()).toBeVisible()

        const outraAba = await context.newPage()
        await outraAba.goto('/login')
        await entrar(outraAba, empresa.codigo, adminLogin, senha)

        await irParaProdutos(page)
        await expect(page.getByText('Nenhum produto encontrado.')).toBeVisible()
    })
})
