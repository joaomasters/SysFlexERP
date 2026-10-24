import type { ReactNode } from 'react'
import { Printer } from 'lucide-react'
import { Card, Button } from '@/shared/components/ui'

interface Props {
  /** id da área impressa — único por relatório (a impressão isola esse elemento). */
  id: string
  titulo: string
  /** Texto de apoio na impressão (período, filtros). */
  descricaoImpressao?: string
  /** Filtros exibidos acima do relatório (some na impressão). */
  filtros?: ReactNode
  children: ReactNode
}

/**
 * Moldura comum dos relatórios/indicadores da página Relatórios, com o MESMO layout dos
 * relatórios em modal (Estoque, Validade, Perdas...): cabeçalho com título, barra de filtros
 * cinza com o botão Imprimir e a área do relatório. Cabeçalho e filtros somem na impressão,
 * que usa um cabeçalho próprio — mesma técnica de "print isolado": só #id fica visível.
 */
export default function PainelRelatorio({ id, titulo, descricaoImpressao, filtros, children }: Props) {
  return (
    <div className="space-y-4">
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #${id}, #${id} * { visibility: visible; }
          #${id} { position: absolute; top: 0; left: 0; width: 100%; }
        }
      `}</style>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm w-full flex flex-col overflow-hidden print:border-0 print:shadow-none print:rounded-none">
        {/* Cabeçalho — some na impressão */}
        <div className="px-6 py-4 border-b print:hidden">
          <h2 className="font-bold text-gray-900">{titulo}</h2>
          {descricaoImpressao && <p className="text-xs text-gray-400">{descricaoImpressao}</p>}
        </div>

        {/* Filtros + Imprimir — somem na impressão */}
        <div className="flex flex-wrap items-end gap-3 px-6 py-3 border-b bg-gray-50 print:hidden">
          {filtros}
          <Button variant="primary" size="sm" onClick={() => window.print()} className="ml-auto">
            <Printer size={14} /> Imprimir
          </Button>
        </div>

        {/* Área impressa */}
        <div id={id} className="p-6 flex flex-col gap-6 bg-white print:p-8">
          <div className="hidden print:block">
            <h1 className="text-xl font-bold text-gray-900">{titulo}</h1>
            <p className="text-xs text-gray-500">
              {descricaoImpressao ? `${descricaoImpressao} — ` : ''}Gerado em {new Date().toLocaleString('pt-BR')}
            </p>
          </div>
          {children}
        </div>
      </div>
    </div>
  )
}

const selectClass = 'border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white'

/** Campo de data compacto da barra de filtros (mesmo estilo dos relatórios em modal). */
export function FiltroData({ label, value, onChange, erro }: {
  label: string; value: string; onChange: (v: string) => void; erro?: string
}) {
  return (
    <div>
      <label className="text-[11px] text-gray-500 block mb-1">{label}</label>
      <input type="date" value={value} onChange={e => onChange(e.target.value)}
        className={`${selectClass} ${erro ? '!border-danger-500' : ''}`} />
      {erro && <p className="text-danger-600 text-xs mt-1">{erro}</p>}
    </div>
  )
}

/** Intervalo de datas padrão: do 1º dia do mês até hoje. */
export function periodoPadrao() {
  const hoje = new Date()
  const iso = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
  return {
    inicio: iso(new Date(hoje.getFullYear(), hoje.getMonth(), 1)),
    fim: iso(hoje),
  }
}

export const dataBR = (iso: string) => iso.split('-').reverse().join('/')

/** Cartão de indicador (número grande + legenda). */
export function Indicador({ label, valor, icone, cor, detalhe }: {
  label: string; valor: ReactNode; icone: ReactNode; cor: string; detalhe?: string
}) {
  return (
    <Card padding="md" className="print:border print:shadow-none">
      <div className={`inline-flex p-2 rounded-lg ${cor} mb-3`}>{icone}</div>
      <p className="text-2xl font-bold text-gray-900 tabular-nums">{valor}</p>
      <p className="text-xs text-gray-500 mt-1">{label}</p>
      {detalhe && <p className="text-[11px] text-gray-400 mt-0.5">{detalhe}</p>}
    </Card>
  )
}

/** Barra horizontal de proporção (0–100) usada nas tabelas de participação. */
export function BarraPercentual({ valor, tom = 'bg-primary-500' }: { valor: number; tom?: string }) {
  return (
    <div className="h-2 w-full bg-gray-100 rounded-full overflow-hidden min-w-[80px] print:hidden">
      <div className={`h-full ${tom} rounded-full`} style={{ width: `${Math.min(100, Math.max(0, valor))}%` }} />
    </div>
  )
}

export const pct = (v: number) => `${(v ?? 0).toFixed(1).replace('.', ',')}%`