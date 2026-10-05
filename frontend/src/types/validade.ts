export type SituacaoValidade = 'VENCIDO' | 'VENCE_EM_BREVE' | 'OK'

/** Um lote com saldo e a situação de validade dele (resposta de GET /estoque/validade). */
export interface LoteValidade {
  loteId: number
  produtoId: number
  produtoNome: string
  unidadeMedida: string
  quantidadeAtual: number
  dataValidade: string
  diasRestantes: number   // negativo = já venceu
  situacao: SituacaoValidade
  documentoRef: string | null
}