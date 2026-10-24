import { useQuery } from '@tanstack/react-query'
import { Boxes, Package, AlertTriangle } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL, formatQuantidade } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD, EmptyState } from '@/shared/components/ui'
import PainelRelatorio, { Indicador } from './PainelRelatorio'

interface RelatorioEstoque {
  valorTotalEstoque: number
  quantidadeProdutos: number
  produtosAbaixoMinimo: number
  alertas: {
    produtoId: number; nome: string; unidadeMedida: string
    estoqueAtual: number; estoqueMinimo: number; diferenca: number
  }[]
}

/** Valor do estoque a custo e produtos abaixo do mínimo — posição de hoje (sem filtro de período). */
export default function PosicaoEstoqueRelatorio() {
  const { data, isLoading } = useQuery<RelatorioEstoque>({
    queryKey: ['rel-estoque-posicao'],
    queryFn: () => api.get('/financeiro/relatorios/estoque').then(r => r.data),
  })

  return (
    <PainelRelatorio id="rel-posicao-estoque-print" titulo="Posição de Estoque" descricaoImpressao="Posição atual">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 print:grid-cols-3 print:gap-2">
        <Indicador label="Valor do estoque (a custo)" valor={formatBRL(data?.valorTotalEstoque ?? 0)}
          icone={<Boxes size={20} />} cor="text-info-600 bg-info-50" />
        <Indicador label="Produtos ativos" valor={data?.quantidadeProdutos ?? 0}
          icone={<Package size={20} />} cor="text-success-600 bg-success-50" />
        <Indicador label="Abaixo do estoque mínimo" valor={data?.produtosAbaixoMinimo ?? 0}
          icone={<AlertTriangle size={20} />}
          cor={(data?.produtosAbaixoMinimo ?? 0) > 0 ? 'text-warning-600 bg-warning-50' : 'text-success-600 bg-success-50'} />
      </div>

      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        <div className="px-4 pt-4 pb-2">
          <h2 className="text-base font-semibold text-gray-700">Produtos abaixo do mínimo</h2>
        </div>
        <Table>
          <THead>
            <tr>
              <TH>Produto</TH>
              <TH align="right">Estoque atual</TH>
              <TH align="right">Mínimo</TH>
              <TH align="right">Faltam</TH>
            </tr>
          </THead>
          <TBody>
            {(data?.alertas ?? []).map(a => (
              <TR key={a.produtoId}>
                <TD className="font-medium">{a.nome}</TD>
                <TD align="right" className="tabular-nums text-warning-700 font-semibold">
                  {formatQuantidade(a.estoqueAtual, a.unidadeMedida)} {a.unidadeMedida}
                </TD>
                <TD align="right" className="tabular-nums text-gray-500">
                  {formatQuantidade(a.estoqueMinimo, a.unidadeMedida)} {a.unidadeMedida}
                </TD>
                <TD align="right" className="tabular-nums">
                  {formatQuantidade(Math.abs(a.diferenca ?? 0), a.unidadeMedida)} {a.unidadeMedida}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
        {!isLoading && (data?.alertas?.length ?? 0) === 0 && <EmptyState>Nenhum produto abaixo do mínimo.</EmptyState>}
      </Card>
    </PainelRelatorio>
  )
}