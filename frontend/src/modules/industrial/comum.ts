import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/api/axios'
import type { ProdutoIndustrial } from '@/types/industrial'

/** Número no formato brasileiro, sem zeros à direita. */
export const num = (v?: number | null, casas = 3) =>
  v == null ? '—' : Number(v).toLocaleString('pt-BR', { maximumFractionDigits: casas })

export const dataBR = (iso?: string | null) => (iso ? iso.slice(0, 10).split('-').reverse().join('/') : '—')

export const dataHoraBR = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'

/** Minutos em "12h30". */
export const horas = (min?: number | null) => {
  if (min == null) return '—'
  const total = Math.round(Number(min))
  const h = Math.floor(total / 60)
  const m = total % 60
  return `${h.toLocaleString('pt-BR')}h${m ? String(m).padStart(2, '0') : ''}`
}

/** Produtos ativos com o tipo da ficha técnica (vale para quem vê a engenharia ou o PCP). */
export function useProdutosIndustriais() {
  return useQuery<ProdutoIndustrial[]>({
    queryKey: ['ind-produtos'],
    queryFn: () => api.get('/industrial/produtos').then(r => r.data),
    staleTime: 60_000,
  })
}

