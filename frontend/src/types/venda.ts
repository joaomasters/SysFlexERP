import { Produto } from './produto'

export interface Cliente {
  id: number
  nome: string
  cpfCnpj?: string
  tipoPessoa: 'PF' | 'PJ'
  telefone?: string
  email?: string
  endereco?: string
  tipoCliente: 'VAREJO' | 'ATACADO' | 'RESTAURANTE' | 'CONVENIADO' | 'FIADO'
  limiteCredito: number
  saldoFiadoAtual?: number
  ativo: boolean
}

export interface Caixa {
  id: number
  operadorId: number
  dataAbertura: string
  dataFechamento?: string
  valorAbertura: number
  valorFechamentoInformado?: number
  status: 'ABERTO' | 'FECHADO'
}

export interface ItemVenda {
  id: number
  produto: Produto
  ean13Lido?: string
  quantidade: number
  precoUnitario: number
  descontoItem: number
  totalItem: number
  tipoEntrada: 'BARCODE' | 'PESAGEM_DIRETA' | 'MANUAL'
}

export interface PagamentoVenda {
  id: number
  formaPagamento: string
  valor: number
  nsu?: string
}

export interface Venda {
  id: number
  numeroCupom?: string
  cliente?: Cliente
  tipoVenda: 'PDV' | 'FATURAMENTO' | 'DELIVERY'
  status: 'ABERTA' | 'FECHADA' | 'CANCELADA'
  subtotal: number
  desconto: number
  total: number
  troco: number
  dataVenda: string
  itens: ItemVenda[]
  pagamentos: PagamentoVenda[]
}

export interface ItemVendaDTO {
  produtoId: number
  nomeProduto: string
  unidadeMedida: string
  quantidade: number
  precoUnitario: number
  totalItem: number
  tipoEntrada: string
  ean13Lido?: string
}

export interface PagamentoDTO {
  formaPagamento: string
  valor: number
  nsu?: string
  autorizacao?: string
  // Stone TEF
  stoneNsu?: string
  stoneAuth?: string
  // PIX
  pixMpId?: string
  // Cheque
  chequeBanco?: string
  chequeNumero?: string
  chequeTitular?: string
}

export interface FecharVendaDTO {
  vendaId: number
  pagamentos: PagamentoDTO[]
}

export interface FaturamentoCliente {
  id: number
  cliente: Cliente
  periodoInicio: string
  periodoFim: string
  totalVendas: number
  totalPago: number
  saldoDevedor: number
  status: 'ABERTO' | 'PARCIAL' | 'QUITADO' | 'VENCIDO'
  dataVencimento?: string
}

export interface ContasAReceber {
  id: number
  cliente: Cliente
  descricao?: string
  valor: number
  valorPago: number
  dataEmissao: string
  dataVencimento?: string
  dataPagamento?: string
  status: 'ABERTO' | 'PARCIAL' | 'PAGO' | 'CANCELADO' | 'AGRUPADO'
  absorvidoPorFaturamento?: { id: number } | null
}