import { request, type FullConfig } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173'
const tenant = process.env.E2E_TENANT
const username = process.env.E2E_USERNAME
const password = process.env.E2E_PASSWORD

export default async function globalSetup(config: FullConfig) {
  // Caminho definido uma vez só, no storageState do playwright.config.ts
  const authFile = config.projects[0].use.storageState as string

  if (!username || !password || !tenant) {
    throw new Error('Defina as variáveis E2E_USERNAME, E2E_PASSWORD e E2E_TENANT antes de executar o Playwright.')
  }

  const apiContext = await request.newContext({ baseURL })
  const response = await apiContext.post('/api/auth/login', {
    headers: {
      'X-Tenant-Codigo': tenant,
    },
    data: {
      username,
      password,
    },
  })

  if (!response.ok()) {
    throw new Error(`Falha no login do Playwright: ${response.status()} ${await response.text()}`)
  }

  const sessao = await response.json()
  // Variáveis definidas aqui chegam aos testes: o `request` das fixtures usa o token no header
  process.env.E2E_TOKEN = sessao.token
  await mkdir(path.dirname(authFile), { recursive: true })
  await writeFile(authFile, JSON.stringify({
    cookies: [],
    origins: [{
      origin: new URL(baseURL).origin,
      localStorage: [{
        name: 'acougue_sessao',
        value: JSON.stringify(sessao),
      }],
    }],
  }, null, 2))

  await apiContext.dispose()
}