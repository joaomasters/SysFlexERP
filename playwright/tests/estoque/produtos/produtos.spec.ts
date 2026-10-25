import { test, expect } from '../../../fixtures'
import { faker } from '@faker-js/faker'

test.describe('Cadastro de produtos', () => {
  test.beforeEach(async ({ produtosPage }) => {
    await produtosPage.abrir()
  })

  test('Não deve permitir cadastrar um produto com espaços em branco em seu nome', async ({ produtosPage, toast }) => {
    await produtosPage.cadastrar({ 
      nome: ' ', 
      precoVenda: faker.commerce.price() 
    })

    await expect(toast.comMensagem('não deve estar em branco')).toBeVisible()
  })

  test('Deve permitir o cadastro de produto apenas informando os campos obrigatórios', async ({ produtosPage, toast }) => {
    await produtosPage.cadastrar({ 
      nome: faker.commerce.productName(), 
      precoVenda: faker.commerce.price() 
    })

    await expect(toast.comMensagem('Produto criado!')).toBeVisible()
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
    // A listagem só mostra produtos ativos
    await expect(produtosPage.linha(produto.nome)).toHaveCount(0)
  })
})
