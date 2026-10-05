import type { BadgeTone } from '@/shared/components/ui'
import type { SituacaoValidade } from '@/types/validade'

// Apresentação da situação de validade (tom do badge e rótulo) — compartilhado
// entre a tela de Controle de Validade e o relatório.

export const situacaoTom: Record<SituacaoValidade, BadgeTone> = {
  VENCIDO: 'danger',
  VENCE_EM_BREVE: 'warning',
  OK: 'success',
}

export const situacaoRotulo: Record<SituacaoValidade, string> = {
  VENCIDO: 'Vencido',
  VENCE_EM_BREVE: 'VENCE EM BREVE',
  OK: 'Em dia',
}

export function textoPrazo(dias: number) {
  if (dias < 0)   return `Venceu há ${-dias} dia${dias === -1 ? '' : 's'}`
  if (dias === 0) return 'Vence hoje'
  return `${dias} dia${dias === 1 ? '' : 's'}`
}

export const formatarDataValidade = (iso: string) =>
  new Date(iso + 'T00:00:00').toLocaleDateString('pt-BR')
