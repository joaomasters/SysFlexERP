import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'

test.describe('Busca de produtos', () => {
    test('Deve achar pelo nome sem acento', async ({ produtosApi, produtosPage }) => {
        const sufixo = faker.string.alphanumeric(8)
        const produto = await produtosApi.criar({ nome: `Limão Taiti ${sufixo}` })

        await produtosPage.abrir()
        await produtosPage.buscar(`limao taiti ${sufixo}`)

        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)

        // Limpeza: o backend não exclui produto, só inativa
        await produtosApi.inativar(produto.id)
    })

    test('Deve achar pelo código interno', async ({ produtosApi, produtosPage }) => {
        const produto = await produtosApi.criar()

        await produtosPage.abrir()
        await produtosPage.buscar(produto.codigoInterno)

        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)

        // Limpeza: o backend não exclui produto, só inativa
        await produtosApi.inativar(produto.id)
    })

    test('Deve achar pelo código de barras completo e pelo início dele', async ({ produtosApi, produtosPage }) => {
        const produto = await produtosApi.criar()

        await produtosPage.abrir()

        await produtosPage.buscar(produto.ean13)
        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)

        await produtosPage.buscar(produto.ean13.slice(0, 11))
        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)

        // Limpeza: o backend não exclui produto, só inativa
        await produtosApi.inativar(produto.id)
    })

    test('Deve avisar quando nenhum produto é encontrado', async ({ produtosPage }) => {
        await produtosPage.abrir()
        await produtosPage.buscar('0000000000000')

        await expect(produtosPage.nenhumEncontrado('0000000000000')).toBeVisible()
    })

    test('Deve achar com o leitor, e o segundo código bipado substitui o primeiro', async ({ produtosApi, produtosPage }) => {
        const a = await produtosApi.criar({ nome: `Bipado A ${faker.string.uuid()}` })
        const b = await produtosApi.criar({ nome: `Bipado B ${faker.string.uuid()}` })

        await produtosPage.abrir()
        // Bipar só com a lista carregada: renderizar a tabela no meio da leitura atrasa as teclas
        await expect(produtosPage.tabela).toBeVisible()

        // Foco fora do campo: o leitor preenche a busca
        await produtosPage.titulo.click()
        await produtosPage.bipar(a.ean13)
        await expect(produtosPage.busca).toHaveValue(a.ean13)
        await expect(produtosPage.linha(a.nome)).toHaveCount(1)
        await expect(produtosPage.linha(b.nome)).toHaveCount(0)

        // Foco já no campo, com o código anterior selecionado: os dígitos substituem o valor
        await expect(produtosPage.busca).toBeFocused()
        await produtosPage.bipar(b.ean13)
        await expect(produtosPage.busca).toHaveValue(b.ean13)
        await expect(produtosPage.linha(b.nome)).toHaveCount(1)
        await expect(produtosPage.linha(a.nome)).toHaveCount(0)

        // Limpeza: o backend não exclui produto, só inativa
        await produtosApi.inativar(a.id)
        await produtosApi.inativar(b.id)
    })

    test('Deve achar produto de balança pela etiqueta', async ({ produtosApi, produtosPage }) => {
        const plu = faker.number.int({ min: 100000, max: 899999 })
        const produto = await produtosApi.criar({
            nome: `Pesado ${faker.string.uuid()}`,
            unidadeMedida: 'KG',
            tipoProduto: 'CORTE',
            codigoBalanca: plu,
        })

        await produtosPage.abrir()
        // Etiqueta da balança: 2 + PLU + preço/peso + dígito verificador
        await produtosPage.buscar(`2${plu}012340`)

        await expect(produtosPage.linha(produto.nome)).toHaveCount(1)

        // Limpeza: o backend não exclui produto, só inativa
        await produtosApi.inativar(produto.id)
    })
})

test.describe('Relatório de estoque na tela de Produtos', () => {
    test('Deve abrir, filtrar por tipo e buscar por código de barras', async ({ produtosApi, produtosPage }) => {
        const higiene = await produtosApi.criar({ nome: `Sabonete ${faker.string.uuid()}`, tipoProduto: 'HIGIENE' })
        const limpeza = await produtosApi.criar({ nome: `Detergente ${faker.string.uuid()}`, tipoProduto: 'LIMPEZA' })
        const { relatorio } = produtosPage

        await produtosPage.abrir()
        await produtosPage.abrirRelatorio()
        await expect(relatorio.linha(higiene.nome)).toContainText('Higiene')

        await relatorio.filtrarPorTipo('LIMPEZA')
        await expect(relatorio.linha(limpeza.nome)).toHaveCount(1)
        await expect(relatorio.linha(higiene.nome)).toHaveCount(0)

        await relatorio.filtrarPorTipo('')
        await relatorio.buscar(higiene.ean13)
        await expect(relatorio.linha(higiene.nome)).toHaveCount(1)
        await expect(relatorio.linha(limpeza.nome)).toHaveCount(0)

        await relatorio.fechar()
        await expect(relatorio.titulo).toBeHidden()

        // Limpeza: o backend não exclui produto, só inativa
        await produtosApi.inativar(higiene.id)
        await produtosApi.inativar(limpeza.id)
    })
})
