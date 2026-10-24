import { faker } from '@faker-js/faker'
import { ApiBase } from '../../ApiBase'

export interface DadosProdutoApi {
    nome: string
    unidadeMedida: 'KG' | 'UN' | 'CX' | 'G'
    tipoProduto: 'CORTE' | 'INDUSTRIALIZADO' | 'INSUMO' | 'SUBPRODUTO'
    precoVenda: number
}

export interface ProdutoApi extends DadosProdutoApi {
    id: number
    ativo: boolean
}

const url = '/api/estoque/produtos'

export class ProdutosApi extends ApiBase {
    // Campos não informados recebem valores válidos gerados pelo faker
    async criar(dados: Partial<DadosProdutoApi> = {}): Promise<ProdutoApi> {
        const resposta = await this.request.post(url, {
            headers: this.headers,
            data: {
                nome: `${faker.commerce.productName()} ${Date.now()}`,
                unidadeMedida: 'UN',
                tipoProduto: 'INDUSTRIALIZADO',
                precoVenda: Number(faker.commerce.price()),
                ...dados,
            },
        })
        await this.verificar(resposta, 'criar produto')
        return resposta.json()
    }

}
