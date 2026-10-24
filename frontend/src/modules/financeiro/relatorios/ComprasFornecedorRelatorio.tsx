import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Truck, Receipt, Wallet } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { Card, Table, THead, TH, TBody, TR, TD, EmptyState } from '@/shared/components/ui'
import PainelRelatorio, { periodoPadrao, dataBR, Indicador, BarraPercentual, FiltroData, pct } from './PainelRelatorio'

interface Recebimento {
  id: number
  fornecedor: string
  valorTotal: number | null
  status: string
  dataRecebimento: string
}

/** Compras (notas de recebimento) agrupadas por fornecedor no período — ignora notas canceladas. */
export default function ComprasFornecedorRelatorio() {
  const padrao = periodoPadrao()
  const [inicio, setInicio] = useState(padrao.inicio)
  const [fim, setFim] = useState(padrao.fim)
  const periodoValido = !!inicio && !!fim && inicio <= fim

  const { data: notas = [], isLoading } = useQuery<Recebimento[]>({
    queryKey: ['rel-compras-fornecedor', inicio, fim],
    queryFn: () => api.get('/estoque/recebimentos', { params: { inicio, fim } }).then(r => r.data),
    enabled: periodoValido,
  })

  const { linhas, totalGeral, totalNotas } = useMemo(() => {
    const validas = notas.filter(n => n.status !== 'CANCELADO')
    const mapa = new Map<string, { nome: string; notas: number; total: number }>()
    validas.forEach(n => {
      const chave = (n.fornecedor ?? '').trim().toLowerCase()
      const atual = mapa.get(chave) ?? { nome: (n.fornecedor ?? '').trim() || 'Sem fornecedor', notas: 0, total: 0 }
      mapa.set(chave, { ...atual, notas: atual.notas + 1, total: atual.total + (n.valorTotal ?? 0) })
    })
    const soma = validas.reduce((s, n) => s + (n.valorTotal ?? 0), 0)
    return {
      linhas: [...mapa.values()].sort((a, b) => b.total - a.total),
      totalGeral: soma,
      totalNotas: validas.length,
    }
  }, [notas])

  return (
    <PainelRelatorio
      id="rel-compras-fornecedor-print"
      titulo="Compras por Fornecedor"
      descricaoImpressao={periodoValido ? `Período: ${dataBR(inicio)} a ${dataBR(fim)} — notas canceladas não entram` : undefined}
      filtros={
        <>
          <FiltroData label="De" value={inicio} onChange={setInicio} />
          <FiltroData label="Até" value={fim} onChange={setFim} erro={!periodoValido ? 'Período inválido.' : undefined} />
        </>
      }
    >
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 print:grid-cols-3 print:gap-2">
        <Indicador label="Total comprado" valor={formatBRL(totalGeral)} icone={<Wallet size={20} />} cor="text-info-600 bg-info-50" />
        <Indicador label="Notas recebidas" valor={totalNotas} icone={<Receipt size={20} />} cor="text-success-600 bg-success-50" />
        <Indicador label="Fornecedores" valor={linhas.length} icone={<Truck size={20} />} cor="text-purple-600 bg-purple-50" />
      </div>

      <Card padding="none" className="overflow-hidden print:border print:shadow-none">
        <Table>
          <THead>
            <tr>
              <TH>Fornecedor</TH>
              <TH align="right">Notas</TH>
              <TH align="right">Total comprado</TH>
              <TH align="right">Participação</TH>
              <TH><span className="sr-only">Proporção</span></TH>
            </tr>
          </THead>
          <TBody>
            {linhas.map(l => {
              const p = totalGeral > 0 ? (l.total / totalGeral) * 100 : 0
              return (
                <TR key={l.nome}>
                  <TD className="font-medium">{l.nome}</TD>
                  <TD align="right" className="tabular-nums">{l.notas}</TD>
                  <TD align="right" className="tabular-nums font-medium">{formatBRL(l.total)}</TD>
                  <TD align="right" className="tabular-nums text-gray-600">{pct(p)}</TD>
                  <TD className="w-1/5"><BarraPercentual valor={p} /></TD>
                </TR>
              )
            })}
          </TBody>
        </Table>
        {!isLoading && linhas.length === 0 && <EmptyState>Nenhuma nota de recebimento no período.</EmptyState>}
      </Card>
    </PainelRelatorio>
  )
}