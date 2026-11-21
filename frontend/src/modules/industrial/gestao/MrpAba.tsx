import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Download } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { Button, StatusBadge, LoadingState, EmptyState, Table, THead, TH, TBody, TR, TD } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import type { ResultadoMrp, SugestaoMrp } from '@/types/industrial'
import { dataBR, num } from '../comum'

/** Exporta a lista de compras sugerida em CSV (abre no Excel). */
function baixarCompras(s: SugestaoMrp[]) {
  const linhas = [['Código', 'Produto', 'Unidade', 'Quantidade sugerida', 'Necessário em', 'Origem'].join(';'),
    ...s.map(x => [x.codigo ?? '', x.nome, x.unidade, String(x.sugerida).replace('.', ','), dataBR(x.necessarioEm), x.origens.join(', ')]
      .map(c => `"${String(c).replace(/"/g, '""')}"`).join(';'))]
  const blob = new Blob(['﻿' + linhas.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `compras-mrp-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

/**
 * MRP: sugestões de produção e de compra a partir das OPs abertas. Nada é aprovado sozinho:
 * a OP de uma sugestão só nasce (planejada) quando o usuário clica.
 */
export default function MrpAba() {
  const qc = useQueryClient()
  const { podeCriar } = usePermissao()
  const [estoqueMinimo, setEstoqueMinimo] = useState(false)
  const [criadas, setCriadas] = useState<Record<number, string>>({})
  const { data: r, isFetching, refetch } = useQuery<ResultadoMrp>({
    queryKey: ['ind-mrp', estoqueMinimo], queryFn: () => api.get('/industrial/gestao/mrp', { params: { incluirEstoqueMinimo: estoqueMinimo } }).then(x => x.data),
  })
  const criarOp = useMutation({
    mutationFn: (s: SugestaoMrp) => api.post('/industrial/ordens', {
      produtoId: s.produtoId, quantidade: s.sugerida, inicioPrevisto: s.iniciarEm, fimPrevisto: s.necessarioEm < s.iniciarEm ? s.iniciarEm : s.necessarioEm,
      observacao: `Sugestão do MRP (${s.origens.join(', ')})`,
    }).then(x => x.data as { ordem: { numero: string } }),
    onSuccess: (o, s) => {
      toast.success(`${o.ordem.numero} criada como planejada.`)
      setCriadas(c => ({ ...c, [s.produtoId]: o.ordem.numero }))
      qc.invalidateQueries({ queryKey: ['ind-ordens'] })
    },
  })
  const compras = r?.sugestoes.filter(s => s.tipo === 'COMPRAR') ?? []

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={estoqueMinimo} onChange={e => setEstoqueMinimo(e.target.checked)} /> Repor o estoque mínimo do cadastro
        </label>
        <Button variant="secondary" loading={isFetching} onClick={() => refetch()}>Recalcular</Button>
        {compras.length > 0 && <Button variant="secondary" onClick={() => baixarCompras(compras)}><Download size={15} /> Lista de compras (CSV)</Button>}
        {r && <span className="text-xs text-gray-500">{r.ordensConsideradas} OPs abertas consideradas</span>}
      </div>
      {r?.avisos.length ? <ul className="text-sm text-warning-800 bg-warning-50 border border-warning-200 rounded-lg p-3 list-disc pl-6">{r.avisos.map(a => <li key={a}>{a}</li>)}</ul> : null}
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {!r ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Sugestão</TH><TH>Produto</TH><TH align="right">Bruta</TH><TH align="right">Estoque</TH><TH align="right">Em produção</TH><TH align="right">Líquida</TH><TH align="right">Sugerida</TH><TH>Iniciar / precisa em</TH><TH /></tr></THead>
            <TBody>
              {r.sugestoes.map(s => (
                <TR key={`${s.tipo}-${s.produtoId}`}>
                  <TD><StatusBadge tone={s.tipo === 'PRODUZIR' ? 'info' : 'purple'}>{s.tipo === 'PRODUZIR' ? 'Produzir' : 'Comprar'}</StatusBadge></TD>
                  <TD><div className="font-medium">{s.nome}</div><div className="text-xs text-gray-500">{s.origens.join(', ')}</div>
                    {s.observacao && <div className="text-xs text-warning-700">{s.observacao}</div>}</TD>
                  <TD align="right" className="tabular-nums">{num(s.necessidadeBruta, 3)}</TD>
                  <TD align="right" className="tabular-nums">{num(s.estoque, 3)}</TD>
                  <TD align="right" className="tabular-nums">{num(s.emProducao, 3)}</TD>
                  <TD align="right" className="tabular-nums">{num(s.liquida, 3)}</TD>
                  <TD align="right" className="tabular-nums font-semibold">{num(s.sugerida, 3)} {s.unidade}</TD>
                  <TD className="tabular-nums text-sm">{dataBR(s.iniciarEm)} → {dataBR(s.necessarioEm)}</TD>
                  <TD align="right">
                    {s.tipo === 'PRODUZIR' && (criadas[s.produtoId]
                      ? <span className="text-sm text-success-700">{criadas[s.produtoId]} criada</span>
                      : podeCriar('IND_PCP') && <Button size="sm" loading={criarOp.isPending && criarOp.variables?.produtoId === s.produtoId} onClick={() => criarOp.mutate(s)}>Criar OP planejada</Button>)}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {r && r.sugestoes.length === 0 && <EmptyState>Nada a produzir nem comprar: o estoque e as OPs em andamento cobrem as necessidades.</EmptyState>}
      </div>
      <p className="text-xs text-gray-500">
        Bruta: o que as OPs abertas ainda vão consumir. Líquida: bruta − estoque − o que já está sendo produzido. Itens com estrutura e roteiro
        ativos viram produção (arredondada pelo lote da ficha técnica) e explodem para os componentes; os demais viram compra.
      </p>
    </div>
  )
}
