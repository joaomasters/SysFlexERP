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
  VENCIDO: 'VENCIDO',
  VENCE_EM_BREVE: 'VENCE EM BREVE',
  OK: 'EM DIA',
}

export function textoPrazo(dias: number) {
  if (dias < 0)   return `Venceu há ${-dias} dia${dias === -1 ? '' : 's'}`
  if (dias === 0) return 'VENCE HOJE'
  return `${dias} dia${dias === 1 ? '' : 's'}`
}

export const formatarDataValidade = (iso: string) =>
  new Date(iso + 'T00:00:00').toLocaleDateString('pt-BR')

// ── Limite de 3 anos de validade (mesma regra do backend: RegraValidade) ────

/** Máximo de anos de validade a partir da data de entrada da mercadoria. */
export const MAX_ANOS_VALIDADE = 3

const pad2 = (n: number) => String(n).padStart(2, '0')

/** Hoje no fuso local, como yyyy-MM-dd (toISOString usaria UTC e viraria o dia à noite). */
export function hojeISO(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

// Lê yyyy-MM-dd aceitando ano com mais de 4 dígitos: o campo de data do
// navegador deixa digitar anos como 120111, e comparar como texto daria errado.
const partesData = (iso: string): [number, number, number] | null => {
  const m = /^(\d{1,6})-(\d{2})-(\d{2})$/.exec(iso)
  return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null
}

/** Última validade aceita para uma entrada feita em dataEntradaISO (padrão: hoje). */
export function limiteValidadeISO(dataEntradaISO: string = hojeISO()): string {
  const [a, m, d] = partesData(dataEntradaISO) ?? partesData(hojeISO())!
  const ano = a + MAX_ANOS_VALIDADE
  const bissexto = (ano % 4 === 0 && ano % 100 !== 0) || ano % 400 === 0
  const dia = m === 2 && d === 29 && !bissexto ? 28 : d
  return `${String(ano).padStart(4, '0')}-${pad2(m)}-${pad2(dia)}`
}

/** true se a validade informada passa do limite. Data vazia/ inválida não conta (outra regra cobre). */
export function validadeAcimaDoLimite(validadeISO: string, limiteISO: string): boolean {
  const v = partesData(validadeISO)
  const l = partesData(limiteISO)
  if (!v || !l) return false
  for (let i = 0; i < 3; i++) {
    if (v[i] !== l[i]) return v[i] > l[i]
  }
  return false
}