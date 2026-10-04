export interface Categoria {
  id: number
  nome: string
  descricao?: string
}

export interface Produto {
  id: number
  codigoInterno: string
  codigoBalanca?: number
  ean13?: string
  nome: string
  descricao?: string
  unidadeMedida: 'KG' | 'UN' | 'CX' | 'G'
  tipoProduto: 'CORTE' | 'INDUSTRIALIZADO' | 'INSUMO' | 'SUBPRODUTO'
  marca?: string
  fornecedor?: string
  precoCusto?: number
  precoVenda: number
  estoqueAtual: number
  estoqueMinimo: number
  categoria?: Categoria
  temFichaDesossa: boolean
  ativo: boolean
  criadoPorNome?: string   // só vem para administradores
}

export interface FichaDesossaItem {
  id: number
  produtoFilho: Produto
  percentualRendimento: number
  sequencia: number
}

export interface FichaDesossa {
  id: number
  produtoPai: Produto
  nome: string
  descricao?: string
  perdaTotalPercentual: number
  ativo: boolean
  itens: FichaDesossaItem[]
}

export interface ExecutarDesossaDTO {
  fichaDesossaId: number
  quantidadeKgEntrada: number
  custoPorKg?: number
  quantidadesReais?: Record<number, number>
  observacao?: string
  recebimentoId?: number | null
}

export interface ProcessoDesossaResultado {
  id: number
  produtoFilho: Produto
  quantidadePrevista: number
  quantidadeReal: number
  custoRateado: number
}

export interface ProcessoDesossa {
  id: number
  quantidadeEntrada: number
  dataProcesso: string
  usuarioNome?: string   // só vem para administradores
  observacao?: string
  status: string
  recebimento?: { id: number; numeroNf: string | null; fornecedor: string } | null
  resultados: ProcessoDesossaResultado[]
}

export interface ItemPendenteBalanca {
  id: number
  produtoId: number
  produtoNome: string
  codigoBalanca: number
  precoAnterior: number
  precoNovo: number
  status: 'PENDENTE' | 'ENVIADO' | 'CANCELADO'
  criadoEm: string
}