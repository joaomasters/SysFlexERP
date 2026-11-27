import { test, expect } from '../../fixtures'

const auth = () => ({ Authorization: `Bearer ${process.env.E2E_TOKEN}` })
const semAcento = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

test.describe('Inventário — pesquisa de itens', () => {
    test('Deve filtrar os itens pelo nome sem acento e pelo código', async ({ page, request }) => {
        // Só pode haver um inventário aberto: usa o que já estiver aberto, senão abre e cancela no fim
        const aberto = await request.post('/api/estoque/inventario/abrir', {
            headers: auth(), data: { observacao: 'e2e pesquisa' },
        })
        let id: number
        if (aberto.ok()) {
            id = (await aberto.json()).id
        } else {
            const lista = await (await request.get('/api/estoque/inventario', { headers: auth() })).json()
            id = lista.find((i: { status: string }) => i.status === 'ABERTO').id
        }

        try {
            const itens = await (await request.get(`/api/estoque/inventario/${id}/itens`, { headers: auth() })).json()
            expect(itens.length).toBeGreaterThan(1)
            const alvo = itens[0].produto

            await page.goto('/estoque/inventario')
            await page.getByRole('button', { name: new RegExp(`#${id}\\b`) }).click()

            const busca = page.getByLabel('Pesquisar produto no inventário')
            const linhas = page.locator('tbody tr')
            await expect(linhas).toHaveCount(itens.length)

            await busca.fill(semAcento(alvo.nome))
            await expect(linhas.filter({ hasText: alvo.nome })).toHaveCount(1)
            expect(await linhas.count()).toBeLessThan(itens.length)

            if (alvo.codigoInterno) {
                await busca.fill(alvo.codigoInterno)
                await expect(linhas.filter({ hasText: alvo.nome })).toHaveCount(1)
            }

            await busca.fill('zzz-nao-existe-zzz')
            await expect(page.getByText(/Nenhum produto encontrado/)).toBeVisible()

            await page.getByRole('button', { name: 'Limpar pesquisa' }).click()
            await expect(busca).toHaveValue('')
            await expect(linhas).toHaveCount(itens.length)
        } finally {
            if (aberto.ok()) await request.post(`/api/estoque/inventario/${id}/cancelar`, { headers: auth() })
        }
    })
})
