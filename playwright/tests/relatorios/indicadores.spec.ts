import { test, expect } from '../../fixtures'

const headers = { Authorization: `Bearer ${process.env.E2E_TOKEN}` }

// Os dados de demonstração têm vendas nos últimos 30 dias: os relatórios usam um período que as cobre
const hoje = new Date()
const iso = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const periodo = `inicio=${iso(new Date(hoje.getTime() - 40 * 86_400_000))}&fim=${iso(hoje)}`

test.describe('Relatórios de indicadores', () => {
    for (const [id, titulo] of [
        ['horarios', 'Vendas por horário e dia da semana'],
        ['curva-abc', 'Curva ABC de produtos'],
        ['margem-tipo', 'Margem por tipo de produto'],
        ['operadores', 'Vendas por operador'],
        ['fechamentos-caixa', 'Fechamentos de caixa (sobras e faltas)'],
        ['inadimplencia', 'Contas a receber — inadimplência'],
    ] as const) {
        test(`Deve abrir o relatório ${titulo}`, async ({ page }) => {
            await page.goto(`/financeiro/relatorios?relatorio=${id}`)
            await expect(page.getByRole('heading', { name: titulo })).toBeVisible()
            await expect(page.getByText(/Erro interno|Request failed/)).toHaveCount(0)
        })
    }

    test('Curva ABC deve somar 100% e começar pela classe A', async ({ request }) => {
        const abc = await (await request.get(`/api/financeiro/relatorios/curva-abc?${periodo}`, { headers })).json()
        expect(abc.length).toBeGreaterThan(0)
        expect(abc[0].classe).toBe('A')
        expect(Math.round(abc[abc.length - 1].acumulado)).toBe(100)
    })

    test('Horários devem ter as 24 horas e os 7 dias', async ({ request }) => {
        const h = await (await request.get(`/api/financeiro/relatorios/horarios?${periodo}`, { headers })).json()
        expect(h.porHora).toHaveLength(24)
        expect(h.porDiaSemana).toHaveLength(7)
    })

    test('Desempenho deve comparar com o período anterior', async ({ page }) => {
        await page.goto('/financeiro/relatorios?relatorio=desempenho')
        await expect(page.getByText(/vs \d{2}\/\d{2}–\d{2}\/\d{2}/).first()).toBeVisible()
    })
})
