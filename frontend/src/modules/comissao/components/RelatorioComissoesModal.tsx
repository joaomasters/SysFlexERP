import { useMemo, useState } from 'react'
import { useQuery, useQueries } from '@tanstack/react-query'
import { X, Printer, Download } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL, formatPercent } from '@/shared/utils/mask'
import { Button } from '@/shared/components/ui'
import type { ComissaoFuncionario, VendaComissao } from '@/types/comissao'

type Modo = 'resumo' | 'detalhado'
type Ordem = 'comissao' | 'nome'

interface Props {
  /** Período inicial (YYYY-MM-DD) — vem do filtro da aba Apuração, mas pode ser alterado aqui. */
  inicio: string
  fim: string
  onClose: () => void
}

const selectClass = 'border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white'

const dataBR = (iso: string) => iso.split('-').reverse().join('/')

const dataHoraBR = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit',
  })

// ── CSV (mesmo formato do Histórico de Pagamentos: ';' + BOM, para o Excel pt-BR) ──
const csvNum = (v: number) => v.toFixed(2).replace('.', ',')
const csvTxt = (v: string) => {
  // Texto que começa com = + - @ vira fórmula no Excel; o apóstrofo neutraliza.
  const seguro = /^[=+\-@]/.test(v) ? `'${v}` : v
  return `"${seguro.replace(/"/g, '""')}"`
}

function baixarCsv(nomeArquivo: string, linhas: string[]) {
  const blob = new Blob(['\uFEFF' + linhas.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nomeArquivo
  a.click()
  URL.revokeObjectURL(url)
}

/**
 * Relatório de comissões, pensado pra impressão em papel e exportação CSV.
 *
 *  - Resumo: uma linha por funcionário (vendas, total vendido, % atual, comissão).
 *  - Detalhado: o resumo + as vendas de cada funcionário, com o percentual e o valor
 *    de comissão gravados em cada venda no fechamento.
 *
 * Usa os mesmos endpoints e as mesmas queryKeys da tela de Apuração
 * (['comissoes-apuracao'] e ['comissoes-vendas']), então, com o mesmo período, abre
 * sem nova chamada. Impressão: técnica de "print isolado" (igual aos relatórios de
 * Estoque e Validade) — só o conteúdo de #relatorio-comissoes-print vai pro papel.
 */
export default function RelatorioComissoesModal({ inicio: inicioInicial, fim: fimInicial, onClose }: Props) {
  const [inicio, setInicio]   = useState(inicioInicial)
  const [fim, setFim]         = useState(fimInicial)
  const [modo, setModo]       = useState<Modo>('resumo')
  const [funcionarioId, setFuncionarioId] = useState('')
  const [ocultarSemVendas, setOcultarSemVendas] = useState(true)
  const [ordenarPor, setOrdenarPor] = useState<Ordem>('comissao')

  const periodoValido = !!inicio && !!fim && inicio <= fim

  const { data: apuracao = [], isLoading } = useQuery<ComissaoFuncionario[]>({
    queryKey: ['comissoes-apuracao', inicio, fim],
    queryFn: () => api.get('/comissoes/apuracao', { params: { inicio, fim } }).then(r => r.data),
    enabled: periodoValido,
  })

  const funcionariosOrdemAlfabetica = useMemo(
    () => [...apuracao].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')),
    [apuracao],
  )

  const linhas = useMemo(() => {
    let lista = apuracao
    if (funcionarioId) lista = lista.filter(l => String(l.usuarioId) === funcionarioId)
    if (ocultarSemVendas) lista = lista.filter(l => l.quantidadeVendas > 0)
    return [...lista].sort((a, b) =>
      ordenarPor === 'nome'
        ? a.nome.localeCompare(b.nome, 'pt-BR')
        : b.totalComissao - a.totalComissao || a.nome.localeCompare(b.nome, 'pt-BR'),
    )
  }, [apuracao, funcionarioId, ocultarSemVendas, ordenarPor])

  // Vendas de cada funcionário — só buscadas no modo detalhado.
  const alvosDetalhe = useMemo(
    () => (modo === 'detalhado' && periodoValido ? linhas.filter(l => l.quantidadeVendas > 0) : []),
    [modo, periodoValido, linhas],
  )
  const vendasQueries = useQueries({
    queries: alvosDetalhe.map(l => ({
      queryKey: ['comissoes-vendas', l.usuarioId, inicio, fim],
      queryFn: () =>
        api.get<VendaComissao[]>(`/comissoes/apuracao/${l.usuarioId}/vendas`, { params: { inicio, fim } })
          .then(r => r.data),
    })),
  })
  const carregandoVendas = vendasQueries.some(q => q.isLoading)
  const erroVendas = vendasQueries.some(q => q.isError)
  const vendasPorUsuario = new Map<number, VendaComissao[]>(
    alvosDetalhe.map((l, i) => [l.usuarioId, vendasQueries[i]?.data ?? []]),
  )

  const totalVendas   = linhas.reduce((s, l) => s + l.quantidadeVendas, 0)
  const totalVendido  = linhas.reduce((s, l) => s + l.totalVendido, 0)
  const totalComissao = linhas.reduce((s, l) => s + l.totalComissao, 0)

  const semDados = !isLoading && linhas.length === 0
  const bloqueado = !periodoValido || isLoading || semDados || (modo === 'detalhado' && (carregandoVendas || erroVendas))

  const nomeFuncionarioFiltro = funcionarioId
    ? apuracao.find(l => String(l.usuarioId) === funcionarioId)?.nome
    : undefined

  function exportarCsv() {
    const linhasCsv: string[] = []
    if (modo === 'resumo') {
      linhasCsv.push(['Funcionário', 'Login', 'Status', '% atual', 'Vendas', 'Total vendido', 'Comissão'].join(';'))
      linhas.forEach(l => linhasCsv.push([
        csvTxt(l.nome), csvTxt(l.login), l.ativo ? 'Ativo' : 'Inativo',
        csvNum(l.percentualAtual), l.quantidadeVendas, csvNum(l.totalVendido), csvNum(l.totalComissao),
      ].join(';')))
    } else {
      linhasCsv.push(['Funcionário', 'Login', 'Data', 'Venda', 'Total da venda', '% comissão', 'Comissão'].join(';'))
      alvosDetalhe.forEach(l => (vendasPorUsuario.get(l.usuarioId) ?? []).forEach(v => linhasCsv.push([
        csvTxt(l.nome), csvTxt(l.login), csvTxt(dataHoraBR(v.dataVenda)),
        csvTxt(v.numeroCupom ?? `#${v.vendaId}`), csvNum(v.total),
        v.percentualComissao === null ? '' : csvNum(v.percentualComissao), csvNum(v.valorComissao),
      ].join(';'))))
    }
    baixarCsv(`comissoes-${modo}-${inicio}-a-${fim}.csv`, linhasCsv)
  }

  const descricaoFiltros = [
    `Período: ${periodoValido ? `${dataBR(inicio)} a ${dataBR(fim)}` : '—'}`,
    modo === 'resumo' ? 'resumo por funcionário' : 'detalhado, com as vendas',
    nomeFuncionarioFiltro ? `funcionário: ${nomeFuncionarioFiltro}` : 'todos os funcionários',
    ocultarSemVendas ? 'somente com vendas no período' : 'inclui funcionários sem vendas',
  ].join(' — ')

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4 print:static print:bg-white print:p-0">
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #relatorio-comissoes-print, #relatorio-comissoes-print * { visibility: visible; }
          #relatorio-comissoes-print { position: absolute; top: 0; left: 0; width: 100%; }
        }
      `}</style>

      <div className="bg-white rounded-2xl w-full max-w-4xl h-[85vh] flex flex-col shadow-xl print:h-auto print:shadow-none print:rounded-none print:max-w-full">
        {/* Cabeçalho — some na impressão */}
        <div className="flex items-center justify-between px-6 py-4 border-b print:hidden">
          <div>
            <h2 className="font-bold text-gray-900">Relatório de Comissões</h2>
            <p className="text-xs text-gray-400">{linhas.length} funcionário(s)</p>
          </div>
          <button onClick={onClose} aria-label="Fechar">
            <X size={20} className="text-gray-400 hover:text-gray-600" />
          </button>
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
            <label className="text-[11px] text-gray-500 block mb-1">Tipo</label>
            <select value={modo} onChange={e => setModo(e.target.value as Modo)} className={selectClass}>
              <option value="resumo">Resumo por funcionário</option>
              <option value="detalhado">Detalhado (com as vendas)</option>
            </select>
          </div>
          <div>
            <label className="text-[11px] text-gray-500 block mb-1">Funcionário</label>
            <select value={funcionarioId} onChange={e => setFuncionarioId(e.target.value)} className={selectClass}>
              <option value="">Todos</option>
              {funcionariosOrdemAlfabetica.map(l => (
                <option key={l.usuarioId} value={l.usuarioId}>{l.nome}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="text-[11px] text-gray-500 block mb-1">Ordenar por</label>
            <select value={ordenarPor} onChange={e => setOrdenarPor(e.target.value as Ordem)} className={selectClass}>
              <option value="comissao">Maior comissão</option>
              <option value="nome">Nome</option>
            </select>
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-600 pb-2">
            <input
              type="checkbox"
              checked={ocultarSemVendas}
              onChange={e => setOcultarSemVendas(e.target.checked)}
            />
            Ocultar quem não vendeu
          </label>
          <div className="ml-auto flex gap-2">
            <Button variant="secondary" size="sm" onClick={exportarCsv} disabled={bloqueado}>
              <Download size={14} /> Exportar CSV
            </Button>
            <Button variant="primary" size="sm" onClick={() => window.print()} disabled={bloqueado}>
              <Printer size={14} /> Imprimir
            </Button>
          </div>
          {!periodoValido && (
            <p className="w-full text-xs text-danger-600">A data inicial não pode ser depois da data final.</p>
          )}
        </div>

        {/* Área impressa */}
        <div id="relatorio-comissoes-print" className="flex-1 overflow-y-auto p-6 print:overflow-visible print:p-8">
          {/* Cabeçalho só visível na impressão */}
          <div className="hidden print:block mb-4">
            <h1 className="text-xl font-bold text-gray-900">Relatório de Comissões</h1>
            <p className="text-xs text-gray-500">
              {descricaoFiltros} — gerado em {new Date().toLocaleString('pt-BR')}
            </p>
          </div>

          {isLoading ? (
            <p className="text-sm text-gray-400">Carregando...</p>
          ) : modo === 'resumo' ? (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-gray-500 uppercase tracking-wide border-b-2 border-gray-300">
                  <th className="text-left py-2 pr-3">Funcionário</th>
                  <th className="text-right py-2 pr-3">Vendas</th>
                  <th className="text-right py-2 pr-3">Total vendido</th>
                  <th className="text-right py-2 pr-3">% atual</th>
                  <th className="text-right py-2">Comissão</th>
                </tr>
              </thead>
              <tbody>
                {linhas.map(l => (
                  <tr key={l.usuarioId} className="border-b border-gray-100 print:break-inside-avoid">
                    <td className="py-2 pr-3 font-medium text-gray-800">
                      {l.nome}
                      <span className="text-xs text-gray-400 font-mono ml-1.5">{l.login}</span>
                      {!l.ativo && <span className="text-xs text-gray-500 ml-2">(inativo)</span>}
                    </td>
                    <td className="py-2 pr-3 text-right tabular-nums text-gray-700">{l.quantidadeVendas}</td>
                    <td className="py-2 pr-3 text-right tabular-nums text-gray-700">{formatBRL(l.totalVendido)}</td>
                    <td className="py-2 pr-3 text-right tabular-nums text-gray-500">{formatPercent(l.percentualAtual, 2)}</td>
                    <td className="py-2 text-right tabular-nums font-semibold text-gray-900">{formatBRL(l.totalComissao)}</td>
                  </tr>
                ))}
              </tbody>
              {linhas.length > 0 && (
                <tfoot>
                  <tr className="border-t-2 border-gray-300 font-semibold text-gray-900">
                    <td className="py-2 pr-3">Total</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{totalVendas}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{formatBRL(totalVendido)}</td>
                    <td className="py-2 pr-3" />
                    <td className="py-2 text-right tabular-nums">{formatBRL(totalComissao)}</td>
                  </tr>
                </tfoot>
              )}
            </table>
          ) : (
            <div className="space-y-6">
              {carregandoVendas && <p className="text-sm text-gray-400">Carregando vendas...</p>}
              {erroVendas && (
                <p className="text-sm text-danger-600">Não foi possível carregar as vendas de algum funcionário. Tente novamente.</p>
              )}
              {!carregandoVendas && linhas.map(l => {
                const vendas = vendasPorUsuario.get(l.usuarioId) ?? []
                return (
                  <section key={l.usuarioId}>
                    <div className="flex items-baseline justify-between border-b-2 border-gray-300 pb-1 mb-1 print:break-after-avoid">
                      <h3 className="font-semibold text-gray-900">
                        {l.nome}
                        <span className="text-xs text-gray-400 font-mono ml-1.5">{l.login}</span>
                        {!l.ativo && <span className="text-xs text-gray-500 ml-2 font-normal">(inativo)</span>}
                      </h3>
                      <span className="text-xs text-gray-500">
                        {l.quantidadeVendas} venda(s) — {formatBRL(l.totalVendido)} — {formatPercent(l.percentualAtual, 2)} atual
                      </span>
                    </div>
                    {vendas.length === 0 ? (
                      <p className="text-xs text-gray-400 py-2">Sem vendas no período.</p>
                    ) : (
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="text-xs text-gray-500 border-b border-gray-200">
                            <th className="text-left py-1.5 pr-3 font-medium">Data</th>
                            <th className="text-left py-1.5 pr-3 font-medium">Venda</th>
                            <th className="text-right py-1.5 pr-3 font-medium">Total</th>
                            <th className="text-right py-1.5 pr-3 font-medium">%</th>
                            <th className="text-right py-1.5 font-medium">Comissão</th>
                          </tr>
                        </thead>
                        <tbody>
                          {vendas.map(v => (
                            <tr key={v.vendaId} className="border-b border-gray-100 print:break-inside-avoid">
                              <td className="py-1.5 pr-3 text-xs text-gray-500 whitespace-nowrap">{dataHoraBR(v.dataVenda)}</td>
                              <td className="py-1.5 pr-3 font-mono text-xs">{v.numeroCupom ?? `#${v.vendaId}`}</td>
                              <td className="py-1.5 pr-3 text-right tabular-nums">{formatBRL(v.total)}</td>
                              <td className="py-1.5 pr-3 text-right tabular-nums text-gray-500">
                                {v.percentualComissao === null ? '—' : formatPercent(v.percentualComissao, 2)}
                              </td>
                              <td className="py-1.5 text-right tabular-nums font-medium">{formatBRL(v.valorComissao)}</td>
                            </tr>
                          ))}
                        </tbody>
                        <tfoot>
                          <tr className="font-semibold text-gray-900">
                            <td className="py-1.5 pr-3" colSpan={2}>Comissão de {l.nome}</td>
                            <td className="py-1.5 pr-3 text-right tabular-nums">{formatBRL(l.totalVendido)}</td>
                            <td className="py-1.5 pr-3" />
                            <td className="py-1.5 text-right tabular-nums">{formatBRL(l.totalComissao)}</td>
                          </tr>
                        </tfoot>
                      </table>
                    )}
                  </section>
                )
              })}

              {!carregandoVendas && linhas.length > 0 && (
                <p className="text-xs text-gray-500">
                  “—” em % indica venda fechada antes do módulo de comissão (sem comissão apurada).
                </p>
              )}
            </div>
          )}

          {semDados && (
            <p className="text-center text-gray-400 text-sm py-8">Nenhuma venda de funcionário comissionado no período.</p>
          )}

          {modo === 'detalhado' && !carregandoVendas && linhas.length > 0 && (
            <p className="text-right text-xs text-gray-500 border-t pt-3 mt-3">
              {linhas.length} funcionário(s) — {totalVendas} venda(s) — {formatBRL(totalVendido)} vendidos — comissão total {formatBRL(totalComissao)}
            </p>
          )}
        </div>
      </div>
    </div>
  )
}