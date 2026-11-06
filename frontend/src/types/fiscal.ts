export type AmbienteFiscal = 'HOMOLOGACAO' | 'PRODUCAO'
export type StatusDocumentoFiscal =
  | 'PENDENTE' | 'AUTORIZADA' | 'REJEITADA' | 'DENEGADA' | 'CANCELADA' | 'CONTINGENCIA' | 'INUTILIZADA' | 'ERRO'

export interface ConfiguracaoFiscal {
  emitirNfce: boolean
  emitirNfe: boolean
  ambiente: AmbienteFiscal
  uf: string
  provedor: string
  razaoSocial: string | null
  nomeFantasia: string | null
  cnpj: string | null
  inscricaoEstadual: string | null
  regimeTributario: number
  logradouro: string | null
  numeroEndereco: string | null
  complemento: string | null
  bairro: string | null
  codigoMunicipio: string | null
  municipio: string | null
  cep: string | null
  telefone: string | null
  nfceSerie: number
  nfceProximoNumero: number
  nfeSerie: number
  nfeProximoNumero: number
  cscId: string | null
  cscConfigurado: boolean
  certificadoConfigurado: boolean
  certificadoTitular: string | null
  certificadoCnpj: string | null
  certificadoValidade: string | null
  criptoConfigurada: boolean
  provedores: { codigo: string; rotulo: string }[]
  pendenciasNfce: string[]
  pendenciasNfe: string[]
  produtosSemNcm: number
}

export interface DocumentoFiscal {
  id: number
  modelo: 55 | 65
  serie: number
  numero: number
  ambiente: AmbienteFiscal
  status: StatusDocumentoFiscal
  chave: string | null
  protocolo: string | null
  mensagem: string | null
  valorTotal: number
  vendaId: number | null
  notaFiscalSaidaId: number | null
  destinatarioDocumento: string | null
  destinatarioNome: string | null
  emitidaEm: string | null
  canceladaEm: string | null
  createdAt: string
}

export const REGIMES_TRIBUTARIOS: { value: number; label: string }[] = [
  { value: 1, label: '1 — Simples Nacional' },
  { value: 2, label: '2 — Simples Nacional, excesso de sublimite' },
  { value: 3, label: '3 — Regime normal' },
  { value: 4, label: '4 — MEI' },
]
