import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Award, Layers, Package } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL, formatQuantidade } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD, EmptyState, StatusBadge } from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'
import { nomeTipoProduto } from '@/types/produto'
import PainelRelatorio, { Indicador, usePeriodo, pct } from './PainelRelatorio'

interface ProdutoAbc {
  produtoId: number; nome: string; tipoProduto: string; quantidade: number; faturamento: number; custo: number
  margem: number; percentualMargem: number; participacao: number; acumulado: number; classe: 'A' | 'B' | 'C'
}

const TOM: Record<ProdutoAbc['classe'], BadgeTone> = { A: 'success', B: 'warning', C: 'neutral' }
const corMargem = (m: number) => (m < 15 ? 'text-danger-600' : m < 30 ? 'text-warning-600' : 'text-success-600')

/**
 * Curva ABC: os poucos produtos que fazem a maior parte do faturamento (A, até 80% acumulado),
 * os intermediários (B, até 95%) e a cauda (C). Com a margem de cada um, para não faltar o que vende
 * e para enxergar o que vende muito ganhando pouco.
 */
export default function CurvaAbcRelatorio() {
  const p = usePeriodo()
  const [classe, setClasse] = useState<'' | ProdutoAbc['classe']>('')
  const { data = [], isLoading } = useQuery<ProdutoAbc[]>({
    queryKey: ['rel-curva-abc', p.inicio, p.fim],
    queryFn: () => api.get(`/financeiro/relatorios/curva-abc?${p.query}`).then(r => r.data),
    enabled: p.valido,
  })

  const resumo = (['A', 'B', 'C'] as const).map(c => {
    const itens = data.filter(x => x.classe === c)
    return { classe: c, produtos: itens.length, faturamento: itens.reduce((s, x) => s + x.faturamento, 0) }
  })
  const lista = classe ? data.filter(x => x.classe === classe) : data

  return (
    <PainelRelatorio
      id="rel-curva-abc-print"
      titulo="Curva ABC de produtos"
      descricaoImpressao={p.descricao}
      filtros={
        <>
          {p.filtros}
          <div>
            <label className="text-[11px] text-gray-500 block mb-1">Classe</label>
            <select value={classe} onChange={e => setClasse(e.target.value as typeof classe)}
              className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white">
              <option value="">Todas</option>
              <option value="A">A — maior faturamento</option>
              <option value="B">B — intermediários</option>
              <option value="C">C — cauda</option>
            </select>
          </div>
        </>
      }
    >
      <div className="grid grid-cols-3 gap-4 print:gap-2">
        {resumo.map(r => (
          <Indicador key={r.classe} label={`Classe ${r.classe}: ${r.produtos} produto(s)`} valor={formatBRL(r.faturamento)}
            icone={r.classe === 'A' ? <Award size={20} /> : r.classe === 'B' ? <Layers size={20} /> : <Package size={20} />}
            cor={r.classe === 'A' ? 'text-success-600 bg-success-50' : r.classe === 'B' ? 'text-warning-600 bg-warning-50' : 'text-gray-500 bg-gray-100'} />
        ))}
      </div>

      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        <Table>
          <THead>
            <tr>
              <TH>Classe</TH><TH>Produto</TH><TH>Tipo</TH><TH align="right">Qtd.</TH><TH align="right">Faturamento</TH>
              <TH align="right">Margem</TH><TH align="right">Participação</TH><TH align="right">Acumulado</TH>
            </tr>
          </THead>
          <TBody>
            {lista.map(x => (
              <TR key={x.produtoId}>
                <TD><StatusBadge tone={TOM[x.classe]}>{x.classe}</StatusBadge></TD>
                <TD className="font-medium">{x.nome}</TD>
                <TD className="text-gray-500">{nomeTipoProduto(x.tipoProduto)}</TD>
                <TD align="right" className="tabular-nums">{formatQuantidade(x.quantidade)}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(x.faturamento)}</TD>
                <TD align="right" className={`tabular-nums font-medium ${corMargem(x.percentualMargem)}`}>
                  {pct(x.percentualMargem)}
                </TD>
                <TD align="right" className="tabular-nums text-gray-600">{pct(x.participacao)}</TD>
                <TD align="right" className="tabular-nums text-gray-600">{pct(x.acumulado)}</TD>
              </TR>
            ))}
          </TBody>
        </Table>
        {!isLoading && lista.length === 0 && <EmptyState>Nenhuma venda no período.</EmptyState>}
      </Card>
    </PainelRelatorio>
  )
}
