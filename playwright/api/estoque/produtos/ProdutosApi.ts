import { faker } from '@faker-js/faker'
import { ApiBase } from '../../ApiBase'

// Valores aceitos pelo banco (migration V29 / TIPOS_PRODUTO do frontend)
export type TipoProdutoApi =
    | 'CORTE' | 'BEBIDA' | 'ALIMENTO' | 'MERCEARIA' | 'HORTIFRUTI' | 'FRIOS_LATICINIOS' | 'PADARIA'
    | 'CONGELADOS' | 'LIMPEZA' | 'HIGIENE' | 'INDUSTRIALIZADO' | 'INSUMO' | 'SUBPRODUTO' | 'OUTROS'

export interface DadosProdutoApi {
    nome: string
    unidadeMedida: 'KG' | 'UN' | 'CX' | 'G'
    tipoProduto: TipoProdutoApi
    precoVenda: number
    estoqueMinimo: number
    codigoInterno?: string
    ean13?: string
    codigoBalanca?: number
    marca?: string
}

export interface ProdutoApi extends DadosProdutoApi {
    id: number
    codigoInterno: string
    ean13: string
    ativo: boolean
}

// EAN-13 com prefixo Brasil (789); aleatório para não colidir entre testes
export const eanAleatorio = () => '789' + faker.string.numeric(10)

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
                estoqueMinimo: 0,
                // O código automático do backend (count + 1) colide quando testes criam produtos em paralelo
                codigoInterno: `E2E${faker.string.alphanumeric({ length: 12, casing: 'upper' })}`,
                ean13: eanAleatorio(),
                ...dados,
            },
        })
        await this.verificar(resposta, 'criar produto')
        return resposta.json()
    }

    // Produto ativo com exatamente esse nome (o backend busca por trecho do nome)
    async buscarPorNome(nome: string): Promise<ProdutoApi> {
        const resposta = await this.request.get(url, { headers: this.headers, params: { nome } })
        await this.verificar(resposta, 'buscar produto')
        const produto = (await resposta.json() as ProdutoApi[]).find(p => p.nome === nome)
        if (!produto) throw new Error(`Produto "${nome}" não encontrado via API`)
        return produto
    }

    // O backend não exclui produto: o DELETE só inativa (ativo = false)
    async inativar(id: number) {
        const resposta = await this.request.delete(`${url}/${id}`, { headers: this.headers })
        await this.verificar(resposta, 'inativar produto')
    }
}
