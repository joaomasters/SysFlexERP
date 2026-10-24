import { test, expect } from '@playwright/test';
import sidebarLocators from '../../locators/sidebar.locators'
import produtosLocators from '../../locators/estoque/produtos.locators'
import { faker } from '@faker-js/faker';

test.describe('Cadastro de produtos', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('Não deve permitir cadastrar um produto com espaços em branco em seu nome', async ({ page }) => {

    await page.locator(sidebarLocators.produtos).click();
    await page.locator(produtosLocators.btnNovoProduto).click();
    await page.locator(produtosLocators.novoProduto.nome).fill(' ');
    await page.locator(produtosLocators.novoProduto.precoVenda).fill(faker.commerce.price());
    await page.locator(produtosLocators.novoProduto.salvar).click();
    
    await expect(page.getByText('não deve estar em branco')).toBeVisible();
  });

  test('Deve permitir o cadastro de produto apenas informando os campos obrigatórios', async ({ page }) => {
    await page.locator(sidebarLocators.produtos).click();
    await page.locator(produtosLocators.btnNovoProduto).click();
    await page.locator(produtosLocators.novoProduto.nome).fill(faker.commerce.productName());
    await page.locator(produtosLocators.novoProduto.precoVenda).fill(faker.commerce.price());
    await page.locator(produtosLocators.novoProduto.salvar).click();
    
    await expect(page.getByText('Produto criado!')).toBeVisible();
  })
})