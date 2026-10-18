export type ProvedorPagamento = 'STONE' | 'MERCADO_PAGO' | 'SICREDI'
export type ModoIntegracao = 'API_NUVEM' | 'AGENTE_LOCAL' | 'MANUAL'
export type StatusTransacao = 'PENDENTE' | 'APROVADA' | 'NEGADA' | 'CANCELADA' | 'ERRO'

export const ROTULO_PROVEDOR: Record<ProvedorPagamento, string> = {
  STONE: 'Stone',
  MERCADO_PAGO: 'Mercado Pago',
  SICREDI: 'Sicredi',
}

export const ROTULO_MODO: Record<ModoIntegracao, string> = {
  API_NUVEM: 'API em nuvem',
  AGENTE_LOCAL: 'Agente local (TEF)',
  MANUAL: 'Manual',
}

export interface ProvedorConfig {
  provedor: ProvedorPagamento
  rotulo: string
  ativo: boolean
  ambiente: 'SANDBOX' | 'PRODUCAO'
  codigoEstabelecimento?: string | null
  observacoes?: string | null
  credencialConfigurada: boolean
  webhookSecretConfigurado: boolean
  suportaApiNuvem: boolean
  criptoConfigurada: boolean
  updatedAt?: string
}

export interface TerminalPagamento {
  id: number
  nome: string
  provedor: ProvedorPagamento
  modoIntegracao: ModoIntegracao
  identificadorDispositivo?: string | null
  agenteUrl?: string | null
  aceitaDebito: boolean
  aceitaCredito: boolean
  maxParcelas: number
  ativo: boolean
}

export interface TransacaoTerminal {
  id: number
  terminalId: number
  terminalNome: string
  vendaId: number
  provedor: ProvedorPagamento
  modoIntegracao: ModoIntegracao
  tipo: 'DEBITO' | 'CREDITO'
  parcelas: number
  valor: number
  status: StatusTransacao
  nsu?: string | null
  autorizacao?: string | null
  bandeira?: string | null
  mensagem?: string | null
  agenteUrl?: string | null
}

export interface DispositivoRemoto {
  id: string
  modoOperacao: string
  descricao: string
}

export interface TesteConexao {
  ok: boolean
  mensagem: string
}
