import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { X, Printer, Search } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL, formatQuantidade } from '@/shared/utils/mask'
import { Button, baseInputClass } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'

type Ordem = 'data' | 'custo' | 'produto'

interface Perda {
  id: number
  produto: { nome: string; unidadeMedida: string }
  quantidade: number
  custoTotal: number
  motivo: string
  observacao: string
  createdAt: string
  usuarioNome?: string   // só vem para administradores
}

interface Props {
  /** Período inicial (YYYY-MM-DD) — vem do filtro da tela de Perdas, mas pode ser alterado aqui. */
  inicio: string
  fim: string
  onClose?: () => void
  /** Dentro da página de Relatórios: sem overlay, sem botão de fechar. */
  embedded?: boolean
}

const MOTIVOS = ['VENCIMENTO', 'AVARIA', 'FURTO', 'DESOSSA', 'OUTROS']
const selectClass = 'border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white'
const dataBR = (iso: string) => iso.split('-').reverse().join('/')

const rotuloOrdem: Record<Ordem, string> = {
  data: 'data (mais recente primeiro)',
  custo: 'maior custo',
  produto: 'produto',
}

/**
 * Relatório de perdas, pensado pra impressão em papel: período, motivo, busca por produto e
 * ordenação, com resumo por motivo. Mesma técnica de "print isolado" dos demais relatórios — na
 * impressão, tudo fora de #relatorio-perdas-print fica oculto.
 */
export default function RelatorioPerdasModal({ inicio: inicioInicial, fim: fimInicial, onClose, embedded = false }: Props) {
  const { podeVerIdentificacao } = usePermissao()
  const [inicio, setInicio] = useState(inicioInicial)
  const [fim, setFim]       = useState(fimInicial)
  const [motivo, setMotivo] = useState('TODOS')
  const [busca, setBusca]   = useState('')
  const [ordenarPor, setOrdenarPor] = useState<Ordem>('data')

  const periodoValido = !!inicio && !!fim && inicio <= fim

  const { data: perdas = [], isLoading } = useQuery<Perda[]>({
    queryKey: ['perdas', inicio, fim],
    queryFn: () => api.get(`/estoque/perdas?inicio=${inicio}&fim=${fim}`).then(r => r.data),
    enabled: periodoValido,
  })

  const linhas = useMemo(() => {
    const termo = busca.trim().toLowerCase()
    const filtradas = perdas.filter(p =>
      (motivo === 'TODOS' || p.motivo === motivo) &&
      (!termo || p.produto.nome.toLowerCase().includes(termo))
    )
    return [...filtradas].sort((a, b) => {
      if (ordenarPor === 'produto') return a.produto.nome.localeCompare(b.produto.nome, 'pt-BR')
      if (ordenarPor === 'custo')   return (b.custoTotal ?? 0) - (a.custoTotal ?? 0)
      return b.createdAt.localeCompare(a.createdAt)
    })
  }, [perdas, motivo, busca, ordenarPor])

  const total = linhas.reduce((s, p) => s + (p.custoTotal ?? 0), 0)

  // Resumo por motivo — considera o que está filtrado na tela (período, produto, motivo).
  const porMotivo = useMemo(() => {
    const mapa = new Map<string, { qtd: number; custo: number }>()
    linhas.forEach(p => {
      const atual = mapa.get(p.motivo) ?? { qtd: 0, custo: 0 }
      mapa.set(p.motivo, { qtd: atual.qtd + 1, custo: atual.custo + (p.custoTotal ?? 0) })
    })
    return [...mapa.entries()].map(([m, v]) => ({ motivo: m, ...v })).sort((a, b) => b.custo - a.custo)
  }, [linhas])

  const filtrosAplicados = [
    motivo !== 'TODOS' ? `motivo: ${motivo}` : '',
    busca.trim() ? `produto: "${busca.trim()}"` : '',
  ].filter(Boolean).join(' — ')

  const corpo = (
    <div
      className={embedded
        ? 'bg-white rounded-xl border border-gray-100 shadow-sm w-full flex flex-col print:border-0 print:shadow-none print:rounded-none'
        : 'bg-white rounded-2xl w-full max-w-4xl h-[85vh] flex flex-col shadow-xl print:h-auto print:shadow-none print:rounded-none print:max-w-full'}
    >
      {/* Cabeçalho — some na impressão */}
      <div className="flex items-center justify-between px-6 py-4 border-b print:hidden">
        <div>
          <h2 className="font-bold text-gray-900">Relatório de Perdas</h2>
          <p className="text-xs text-gray-400">{linhas.length} lançamento(s) — {formatBRL(total)}</p>
        </div>
        {!embedded && (
          <button onClick={onClose} aria-label="Fechar">
            <X size={20} className="text-gray-400 hover:text-gray-600" />
          </button>
        )}
      </div>

      {/* Filtros — somem na impressão */}
      <div className="flex flex-wrap items-end gap-3 px-6 py-3 border-b bg-gray-50 print:hidden">
        <div>
          <label className="text-[11px] text-gray-500 block mb-1">De</label>
          <input type="date" value={inicio} onChange={e => setInicio(e.target.value)} className={selectClass} />
        </div>
        <div>
          <label className="text-[11px] text-gray-500 block mb-1">Até</label>
          <input type="date" value={fim} onChange={e => setFim(e.target.value)} className={selectClass} />
        </div>
        <div>
          <label className="text-[11px] text-gray-500 block mb-1">Motivo</label>
          <select value={motivo} onChange={e => setMotivo(e.target.value)} className={selectClass}>
            <option value="TODOS">Todos</option>
            {MOTIVOS.map(m => <option key={m} value={m}>{m}</option>)}
          </select>
        </div>
        <div className="relative flex-1 min-w-[180px]">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text" value={busca} onChange={e => setBusca(e.target.value)}
            placeholder="Buscar produto..."
            className={`${baseInputClass} pl-8 py-1.5 text-sm`}
          />
        </div>
        <div>
          <label className="text-[11px] text-gray-500 block mb-1">Ordenar por</label>
          <select value={ordenarPor} onChange={e => setOrdenarPor(e.target.value as Ordem)} className={selectClass}>
            <option value="data">Data</option>
            <option value="custo">Maior custo</option>
            <option value="produto">Produto</option>
          </select>
        </div>
        <Button variant="primary" size="sm" onClick={() => window.print()} disabled={!periodoValido} className="ml-auto">
          <Printer size={14} /> Imprimir
        </Button>
      </div>

      {/* Área impressa */}
      <div id="relatorio-perdas-print" className="flex-1 overflow-y-auto p-6 print:overflow-visible print:p-8">
        <div className="hidden print:block mb-4">
          <h1 className="text-xl font-bold text-gray-900">Relatório de Perdas</h1>
          <p className="text-xs text-gray-500">
            Período: {periodoValido ? `${dataBR(inicio)} a ${dataBR(fim)}` : '—'}
            {filtrosAplicados && ` — ${filtrosAplicados}`}
            {' '}— ordenado por {rotuloOrdem[ordenarPor]}
            {' '}— Gerado em {new Date().toLocaleString('pt-BR')}
          </p>
        </div>

        {!periodoValido ? (
          <p className="text-center text-gray-400 text-sm py-8">Informe um período válido (a data inicial não pode ser maior que a final).</p>
        ) : isLoading ? (
          <p className="text-sm text-gray-400">Carregando...</p>
        ) : (
          <>
            {porMotivo.length > 0 && (
              <div className="mb-5">
                <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Resumo por motivo</p>
                <table className="w-full text-sm">
                  <tbody>
                    {porMotivo.map(m => (
                      <tr key={m.motivo} className="border-b border-gray-100">
                        <td className="py-1.5 pr-3 font-medium text-gray-700">{m.motivo}</td>
                        <td className="py-1.5 pr-3 text-right text-gray-500 tabular-nums">{m.qtd} lançamento(s)</td>
                        <td className="py-1.5 pr-3 text-right tabular-nums text-gray-700">
                          {total > 0 ? `${((m.custo / total) * 100).toFixed(1).replace('.', ',')}%` : '—'}
                        </td>
                        <td className="py-1.5 text-right font-medium tabular-nums text-danger-600">{formatBRL(m.custo)}</td>
                      </tr>
                    ))}
                    <tr>
                      <td className="pt-2 font-bold text-gray-900" colSpan={3}>Total</td>
                      <td className="pt-2 text-right font-bold tabular-nums text-danger-600">{formatBRL(total)}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            )}

            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-gray-500 uppercase tracking-wide border-b-2 border-gray-300">
                  <th className="text-left py-2 pr-3">Data</th>
                  <th className="text-left py-2 pr-3">Produto</th>
                  <th className="text-left py-2 pr-3">Motivo</th>
                  <th className="text-right py-2 pr-3">Qtd</th>
                  <th className="text-right py-2 pr-3">Custo</th>
                  <th className="text-left py-2 pr-3">Observação</th>
                  {podeVerIdentificacao && <th className="text-left py-2">Lançado por</th>}
                </tr>
              </thead>
              <tbody>
                {linhas.map(p => (
                  <tr key={p.id} className="border-b border-gray-100">
                    <td className="py-2 pr-3 text-gray-500 whitespace-nowrap">{new Date(p.createdAt).toLocaleDateString('pt-BR')}</td>
                    <td className="py-2 pr-3 font-medium text-gray-800">{p.produto.nome}</td>
                    <td className="py-2 pr-3 text-gray-600">{p.motivo}</td>
                    <td className="py-2 pr-3 text-right tabular-nums text-gray-700 whitespace-nowrap">
                      {formatQuantidade(p.quantidade, p.produto.unidadeMedida)} {p.produto.unidadeMedida}
                    </td>
                    <td className="py-2 pr-3 text-right tabular-nums text-danger-600 font-medium">{formatBRL(p.custoTotal ?? 0)}</td>
                    <td className="py-2 pr-3 text-xs text-gray-500">{p.observacao || '—'}</td>
                    {podeVerIdentificacao && <td className="py-2 text-gray-600">{p.usuarioNome ?? '—'}</td>}
                  </tr>
                ))}
              </tbody>
            </table>

            {linhas.length === 0 && (
              <p className="text-center text-gray-400 text-sm py-8">Nenhuma perda encontrada.</p>
            )}
          </>
        )}
      </div>
    </div>
  )

  return (
    <>
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #relatorio-perdas-print, #relatorio-perdas-print * { visibility: visible; }
          #relatorio-perdas-print { position: absolute; top: 0; left: 0; width: 100%; }
        }
      `}</style>
      {embedded ? corpo : (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4 print:static print:bg-white print:p-0">
          {corpo}
        </div>
      )}
    </>
  )
}