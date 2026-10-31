export interface Categoria {
  id: number
  nome: string
  descricao?: string
}

/** Tipos aceitos pelo banco (ver migration V29). A ordem é a do formulário. */
export const TIPOS_PRODUTO = [
  { valor: 'CORTE', nome: 'Corte de Carne' },
  { valor: 'BEBIDA', nome: 'Bebida' },
  { valor: 'ALIMENTO', nome: 'Alimento' },
  { valor: 'MERCEARIA', nome: 'Mercearia' },
  { valor: 'HORTIFRUTI', nome: 'Hortifruti' },
  { valor: 'FRIOS_LATICINIOS', nome: 'Frios e Laticínios' },
  { valor: 'PADARIA', nome: 'Padaria' },
  { valor: 'CONGELADOS', nome: 'Congelados' },
  { valor: 'LIMPEZA', nome: 'Limpeza' },
  { valor: 'HIGIENE', nome: 'Higiene' },
  { valor: 'INDUSTRIALIZADO', nome: 'Industrializado' },
  { valor: 'INSUMO', nome: 'Insumo' },
  { valor: 'SUBPRODUTO', nome: 'Subproduto (sebo, osso...)' },
  { valor: 'OUTROS', nome: 'Outros' },
] as const

export type TipoProduto = (typeof TIPOS_PRODUTO)[number]['valor']

export const nomeTipoProduto = (tipo?: string) =>
  TIPOS_PRODUTO.find(t => t.valor === tipo)?.nome ?? tipo ?? ''

export interface Produto {
  id: number
  codigoInterno: string
  codigoBalanca?: number
  ean13?: string
  nome: string
  descricao?: string
  unidadeMedida: 'KG' | 'UN' | 'CX' | 'G'
  tipoProduto: TipoProduto
  marca?: string
  fornecedor?: string        // nome do fornecedor vinculado (somente leitura)
  fornecedorId?: number | null   // vínculo com o cadastro de Fornecedores
  precoCusto?: number
  precoVenda: number
  estoqueAtual: number
  estoqueMinimo: number
  categoria?: Categoria
  temFichaDesossa: boolean
  ativo: boolean
  criadoPorNome?: string   // só vem para administradores
  validadePadraoDias?: number | null   // prazo de validade padrão (dias) p/ gerar lote automático
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