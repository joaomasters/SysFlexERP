import { test, expect } from '../../fixtures'
import { faker } from '@faker-js/faker'
import { eanAleatorio } from '../../api/estoque/produtos/ProdutosApi'
import { TIPOS_PRODUTO } from '../../pages/produtos/ProdutoCadastroPage'

test.describe('Cadastro de produtos', () => {
  test.beforeEach(async ({ produtosPage }) => {
    await produtosPage.abrir()
  })

  test('Não deve permitir cadastrar um produto com espaços em branco em seu nome', async ({ produtosPage }) => {
    await produtosPage.cadastrar({
      nome: ' ',
      ean13: eanAleatorio(),
      precoVenda: faker.commerce.price()
    })

    await expect(produtosPage.cadastro.erro('Informe a descrição do produto.')).toBeVisible()
    await expect(produtosPage.cadastro.titulo('Novo Produto')).toBeVisible()
  })

  test('Deve permitir o cadastro de produto apenas informando os campos obrigatórios', async ({ produtosPage, produtosApi, toast }) => {
    const nome = `${faker.commerce.productName()} ${Date.now()}`

    await produtosPage.cadastrar({
      nome,
      ean13: eanAleatorio(),
      precoVenda: faker.commerce.price()
    })

    await expect(toast.comMensagem('Produto cadastrado!')).toBeVisible()

    await produtosPage.buscar(nome)
    await expect(produtosPage.linha(nome)).toHaveCount(1)

    // Limpeza: o backend não exclui produto, só inativa
    const produto = await produtosApi.buscarPorNome(nome)
    await produtosApi.inativar(produto.id)
  })

  test('Não deve permitir dois produtos com o mesmo código de barras', async ({ produtosPage, produtosApi, toast }) => {
    const primeiro = await produtosApi.criar()

    await produtosPage.cadastrar({
      nome: `Segundo ${faker.string.uuid()}`,
      ean13: primeiro.ean13,
      precoVenda: faker.commerce.price()
    })

    await expect(toast.comMensagem(`Código de barras ${primeiro.ean13} já cadastrado no produto "${primeiro.nome}".`)).toBeVisible()
    await expect(produtosPage.cadastro.titulo('Novo Produto')).toBeVisible()

    // Limpeza: o segundo não foi gravado; inativa o primeiro
    await produtosApi.inativar(primeiro.id)
  })

  test('Deve mostrar todos os tipos e gravar um tipo novo', async ({ produtosPage, produtosApi, toast }) => {
    const nome = `Queijo ${faker.string.uuid()}`

    await produtosPage.btnNovoProduto.click()
    await expect(produtosPage.cadastro.opcoesTipo).toHaveText(TIPOS_PRODUTO.map(t => t.nome))

    await produtosPage.cadastro.preencher({
      nome,
      ean13: eanAleatorio(),
      precoVenda: faker.commerce.price(),
      tipo: 'FRIOS_LATICINIOS'
    })
    await produtosPage.cadastro.salvar()

    await expect(toast.comMensagem('Produto cadastrado!')).toBeVisible()
    await produtosPage.buscar(nome)
    await expect(produtosPage.linha(nome)).toContainText('Frios e Laticínios')

    // Limpeza: o backend não exclui produto, só inativa
    const produto = await produtosApi.buscarPorNome(nome)
    await produtosApi.inativar(produto.id)
  })
})

test.describe('Exclusão de produtos', () => {
  test('Deve inativar um produto pela listagem', async ({ produtosApi, produtosPage, confirmDialog, toast }) => {
    const produto = await produtosApi.criar()

    await produtosPage.abrir()
    await produtosPage.inativar(produto.nome)

    await expect(confirmDialog.titulo('Inativar produto?')).toBeVisible()
    await expect(confirmDialog.mensagem(`"${produto.nome}" deixará de aparecer nas vendas e listagens ativas.`)).toBeVisible()
    await confirmDialog.confirmar('Inativar')

    await expect(toast.comMensagem('Produto inativado')).toBeVisible()
    await expect(produtosPage.linha(produto.nome)).toHaveCount(0)
  })
})
