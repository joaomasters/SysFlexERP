export interface ComissaoFuncionario {
  usuarioId: number
  nome: string
  login: string
  ativo: boolean
  percentualAtual: number
  quantidadeVendas: number
  totalVendido: number
  totalComissao: number
}

export interface VendaComissao {
  vendaId: number
  numeroCupom: string | null
  dataVenda: string
  total: number
  /** null = venda fechada antes do módulo de comissão existir */
  percentualComissao: number | null
  valorComissao: number
}
