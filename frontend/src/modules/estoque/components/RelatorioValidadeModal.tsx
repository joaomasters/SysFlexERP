import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { X, Printer, Search } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatWeightDisplay } from '@/shared/utils/mask'
import { Button, baseInputClass } from '@/shared/components/ui'
import { situacaoRotulo, textoPrazo, formatarDataValidade } from '../validadeUtils'
import type { LoteValidade, SituacaoValidade } from '@/types/validade'

type FiltroSituacao = 'TODOS' | 'ATENCAO' | SituacaoValidade
type Ordem = 'validade' | 'produto'

interface Props {
  onClose: () => void
}

const qtd = (v: number) => formatWeightDisplay(v, 3)

const rotuloFiltro: Record<FiltroSituacao, string> = {
  TODOS: 'todos os lotes',
  ATENCAO: 'vencidos e vencendo em breve',
  VENCIDO: 'somente vencidos',
  VENCE_EM_BREVE: 'somente vencendo em breve',
  OK: 'somente em dia',
}

/**
 * Relatório de validade dos lotes, pensado pra impressão em papel: busca por produto, filtro
 * por situação e ordenação por validade (padrão — o que vence primeiro no topo) ou por produto.
 * Mesma técnica de "print isolado" do Relatório de Estoque: na impressão, tudo fora de
 * #relatorio-validade-print fica oculto.
 *
 * Usa a mesma query da tela (['validade-lotes']), então abre instantâneo e sem nova chamada.
 */
export default function RelatorioValidadeModal({ onClose }: Props) {
  const [busca, setBusca]       = useState('')
  const [situacao, setSituacao] = useState<FiltroSituacao>('TODOS')
  const [ordenarPor, setOrdenarPor] = useState<Ordem>('validade')

  const { data: lotes = [], isLoading } = useQuery<LoteValidade[]>({
    queryKey: ['validade-lotes'],
    queryFn: () => api.get('/estoque/validade').then(r => r.data),
  })

  const linhas = useMemo(() => {
    const buscaLower = busca.trim().toLowerCase()
    let filtrados = lotes

    if (buscaLower) {
      filtrados = filtrados.filter(l => l.produtoNome.toLowerCase().includes(buscaLower))
    }
    if (situacao === 'ATENCAO') {
      filtrados = filtrados.filter(l => l.situacao !== 'OK')
    } else if (situacao !== 'TODOS') {
      filtrados = filtrados.filter(l => l.situacao === situacao)
    }

    return [...filtrados].sort((a, b) => {
      if (ordenarPor === 'produto') {
        return a.produtoNome.localeCompare(b.produtoNome, 'pt-BR') || a.dataValidade.localeCompare(b.dataValidade)
      }
      return a.dataValidade.localeCompare(b.dataValidade) || a.produtoNome.localeCompare(b.produtoNome, 'pt-BR')
    })
  }, [lotes, busca, situacao, ordenarPor])

  const qtdVencidos = linhas.filter(l => l.situacao === 'VENCIDO').length
  const qtdEmBreve  = linhas.filter(l => l.situacao === 'VENCE_EM_BREVE').length
  const qtdOk       = linhas.filter(l => l.situacao === 'OK').length

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4 print:static print:bg-white print:p-0">
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #relatorio-validade-print, #relatorio-validade-print * { visibility: visible; }
          #relatorio-validade-print { position: absolute; top: 0; left: 0; width: 100%; }
        }
      `}</style>

      <div className="bg-white rounded-2xl w-full max-w-4xl h-[85vh] flex flex-col shadow-xl print:h-auto print:shadow-none print:rounded-none print:max-w-full">
        {/* Cabeçalho — some na impressão */}
        <div className="flex items-center justify-between px-6 py-4 border-b print:hidden">
          <div>
            <h2 className="font-bold text-gray-900">Relatório de Validade</h2>
            <p className="text-xs text-gray-400">{linhas.length} lote(s)</p>
          </div>
          <button onClick={onClose} aria-label="Fechar">
            <X size={20} className="text-gray-400 hover:text-gray-600" />
          </button>
        </div>

        {/* Filtros — somem na impressão */}
        <div className="flex flex-wrap items-end gap-3 px-6 py-3 border-b bg-gray-50 print:hidden">
          <div className="relative flex-1 min-w-[200px]">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={busca}
              onChange={e => setBusca(e.target.value)}
              placeholder="Buscar por produto..."
              className={`${baseInputClass} pl-8 py-1.5 text-sm`}
            />
          </div>
          <div>
            <label className="text-[11px] text-gray-500 block mb-1">Situação</label>
            <select
              value={situacao}
              onChange={e => setSituacao(e.target.value as FiltroSituacao)}
              className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white"
            >
              <option value="TODOS">Todos</option>
              <option value="ATENCAO">Requer atenção</option>
              <option value="VENCIDO">Vencidos</option>
              <option value="VENCE_EM_BREVE">Vencendo em breve</option>
              <option value="OK">Em dia</option>
            </select>
          </div>
          <div>
            <label className="text-[11px] text-gray-500 block mb-1">Ordenar por</label>
            <select
              value={ordenarPor}
              onChange={e => setOrdenarPor(e.target.value as Ordem)}
              className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white"
            >
              <option value="validade">Validade (vence primeiro)</option>
              <option value="produto">Nome do produto</option>
            </select>
          </div>
          <Button variant="primary" size="sm" onClick={() => window.print()} disabled={linhas.length === 0} className="ml-auto">
            <Printer size={14} /> Imprimir
          </Button>
        </div>

        {/* Área impressa */}
        <div id="relatorio-validade-print" className="flex-1 overflow-y-auto p-6 print:overflow-visible print:p-8">
          {/* Cabeçalho só visível na impressão */}
          <div className="hidden print:block mb-4">
            <h1 className="text-xl font-bold text-gray-900">Relatório de Validade</h1>
            <p className="text-xs text-gray-500">
              Gerado em {new Date().toLocaleString('pt-BR')} — {rotuloFiltro[situacao]}
              {busca && ` — produto: "${busca}"`}
              {' '}— ordenado por {ordenarPor === 'validade' ? 'validade' : 'produto'}
            </p>
          </div>

          {isLoading ? (
            <p className="text-sm text-gray-400">Carregando...</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-gray-500 uppercase tracking-wide border-b-2 border-gray-300">
                  <th className="text-left py-2 pr-3">Produto</th>
                  <th className="text-right py-2 pr-3">Saldo do lote</th>
                  <th className="text-left py-2 pr-3">Validade</th>
                  <th className="text-left py-2 pr-3">Prazo</th>
                  <th className="text-left py-2 pr-3">Situação</th>
                  <th className="text-left py-2">Origem</th>
                </tr>
              </thead>
              <tbody>
                {linhas.map(l => (
                  <tr key={l.loteId} className="border-b border-gray-100">
                    <td className="py-2 pr-3 font-medium text-gray-800">{l.produtoNome}</td>
                    <td className="py-2 pr-3 text-right tabular-nums text-gray-700">{qtd(l.quantidadeAtual)} {l.unidadeMedida}</td>
                    <td className="py-2 pr-3 text-gray-600">{formatarDataValidade(l.dataValidade)}</td>
                    <td className={`py-2 pr-3 ${l.situacao === 'VENCIDO' ? 'text-danger-700 font-semibold' : 'text-gray-600'}`}>
                      {textoPrazo(l.diasRestantes)}
                    </td>
                    {/* Texto (não badge): badges perdem a cor na impressão em preto e branco */}
                    <td className={`py-2 pr-3 font-medium ${
                      l.situacao === 'VENCIDO' ? 'text-danger-700'
                        : l.situacao === 'VENCE_EM_BREVE' ? 'text-warning-700' : 'text-gray-600'}`}>
                      {situacaoRotulo[l.situacao]}
                    </td>
                    <td className="py-2 text-gray-500">{l.documentoRef ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {!isLoading && linhas.length === 0 && (
            <p className="text-center text-gray-400 text-sm py-8">Nenhum lote encontrado.</p>
          )}

          {linhas.length > 0 && (
            <p className="text-right text-xs text-gray-500 border-t pt-3 mt-3">
              {linhas.length} lote(s) — {qtdVencidos} vencido(s), {qtdEmBreve} vencendo em breve, {qtdOk} em dia
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
