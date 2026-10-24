import { APIRequestContext, APIResponse } from '@playwright/test'

// Base das classes de API: autenticação e tratamento de erro ficam num lugar só
export abstract class ApiBase {
    constructor(protected readonly request: APIRequestContext) {}

    // O token vem do globalSetup (auth.setup.ts)
    protected get headers() {
        return { Authorization: `Bearer ${process.env.E2E_TOKEN}` }
    }

    protected async verificar(resposta: APIResponse, acao: string): Promise<APIResponse> {
        if (!resposta.ok()) {
            throw new Error(`Falha ao ${acao} via API: ${resposta.status()} ${await resposta.text()}`)
        }
        return resposta
    }
}
