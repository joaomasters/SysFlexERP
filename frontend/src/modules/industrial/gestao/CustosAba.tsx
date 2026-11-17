import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/api/axios'
import { Modal, Button, StatusBadge, LoadingState, EmptyState, Table, THead, TH, TBody, TR, TD, Paginacao } from '@/shared/components/ui'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import { STATUS_OP, type CustoOP, type CustoResumo } from '@/types/industrial'
import { horas, num } from '../comum'

const brl = (v?: number | null) => (v == null ? '—' : Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }))
const desvioTexto = (v?: number | null) => (v == null ? '—' : `${v > 0 ? '+' : ''}${v.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`)

/** Custo estimado × real das OPs em produção, concluídas e encerradas. */
export default function CustosAba() {
  const [aberta, setAberta] = useState<number | null>(null)
  const { data = [], isLoading } = useQuery<CustoResumo[]>({ queryKey: ['ind-custos'], queryFn: () => api.get('/industrial/gestao/custos').then(r => r.data) })
  const pag = usePaginacao(data, 'custos')
  return (
    <div>
      <p className="text-sm text-gray-600 mb-3">
        Estimado: materiais previstos × custo médio atual + tempo previsto × custo/hora. Real: consumos × custo do momento + tempo trabalhado × custo/hora.
        O desvio compara o custo por unidade.
      </p>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>OP</TH><TH>Produto</TH><TH>Situação</TH><TH align="right">Produzido</TH><TH align="right">Estimado/un.</TH><TH align="right">Real/un.</TH><TH align="right">Desvio</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(c => (
                <TR key={c.ordemId}>
                  <TD className="font-mono font-semibold">{c.numero}</TD>
                  <TD>{c.produtoNome}</TD>
                  <TD><StatusBadge tone={STATUS_OP[c.status].tom}>{STATUS_OP[c.status].rotulo}</StatusBadge></TD>
                  <TD align="right" className="tabular-nums">{num(c.produzido)} / {num(c.quantidade)} {c.unidade}</TD>
                  <TD align="right" className="tabular-nums">{brl(c.estimadoUnitario)}</TD>
                  <TD align="right" className="tabular-nums">{brl(c.realUnitario)}</TD>
                  <TD align="right" className={`tabular-nums ${c.desvioPercentual != null && c.desvioPercentual > 0 ? 'text-danger-700 font-semibold' : ''}`}>{desvioTexto(c.desvioPercentual)}</TD>
                  <TD align="right">
                    {c.avisos > 0 && <span className="text-xs text-warning-700 mr-2" title="Há dados de custo faltando">{c.avisos} aviso(s)</span>}
                    <Button size="sm" variant="secondary" onClick={() => setAberta(c.ordemId)}>Detalhar</Button>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && data.length === 0 && <EmptyState>Nenhuma OP em produção, concluída ou encerrada.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="OPs" />}
      </div>
      {aberta != null && <DetalheCusto ordemId={aberta} onClose={() => setAberta(null)} />}
    </div>
  )
}

function DetalheCusto({ ordemId, onClose }: { ordemId: number; onClose: () => void }) {
  const { data: c } = useQuery<CustoOP>({ queryKey: ['ind-custo', ordemId], queryFn: () => api.get(`/industrial/gestao/custos/${ordemId}`).then(r => r.data) })
  return (
    <Modal title={c ? `Custo da ${c.numero} · ${c.produtoNome}` : 'Custo da OP'} onClose={onClose} maxWidth="2xl"
      footer={<Button variant="secondary" onClick={onClose}>Fechar</Button>}>
      {!c ? <LoadingState /> : (
        <div className="space-y-4">
          <Table>
            <THead><tr><TH /><TH align="right">Materiais</TH><TH align="right">Processo</TH><TH align="right">Total</TH><TH align="right">Por {c.unidade}</TH></tr></THead>
            <TBody>
              <TR><TD className="font-medium">Estimado ({num(c.quantidade)} {c.unidade})</TD><TD align="right" className="tabular-nums">{brl(c.estimado.materiais)}</TD>
                <TD align="right" className="tabular-nums">{brl(c.estimado.processo)}</TD><TD align="right" className="tabular-nums">{brl(c.estimado.total)}</TD><TD align="right" className="tabular-nums">{brl(c.estimado.unitario)}</TD></TR>
              <TR><TD className="font-medium">Real ({num(c.produzido)} {c.unidade} bons)</TD><TD align="right" className="tabular-nums">{brl(c.real.materiais)}</TD>
                <TD align="right" className="tabular-nums">{brl(c.real.processo)}</TD><TD align="right" className="tabular-nums">{brl(c.real.total)}</TD><TD align="right" className="tabular-nums font-semibold">{brl(c.real.unitario)}</TD></TR>
            </TBody>
          </Table>
          <p className="text-sm">Desvio do custo por unidade: <b className="tabular-nums">{desvioTexto(c.desvioPercentual)}</b></p>
          {c.avisos.length > 0 && <ul className="text-sm text-warning-800 bg-warning-50 border border-warning-200 rounded-lg p-3 list-disc pl-6">{c.avisos.map(a => <li key={a}>{a}</li>)}</ul>}
          <Table>
            <THead><tr><TH>Material</TH><TH align="right">Previsto</TH><TH align="right">Consumido</TH><TH align="right">Custo previsto</TH><TH align="right">Custo real</TH></tr></THead>
            <TBody>{c.materiais.map(m => (
              <TR key={m.produtoId}><TD>{m.nome}</TD><TD align="right" className="tabular-nums">{num(m.quantidadePrevista, 3)} {m.unidade}</TD>
                <TD align="right" className="tabular-nums">{num(m.quantidadeConsumida, 3)}</TD><TD align="right" className="tabular-nums">{brl(m.custoPrevisto)}</TD><TD align="right" className="tabular-nums">{brl(m.custoReal)}</TD></TR>
            ))}</TBody>
          </Table>
          <Table>
            <THead><tr><TH>Operação</TH><TH align="right">Custo/hora</TH><TH align="right">Tempo previsto</TH><TH align="right">Tempo real</TH><TH align="right">Custo previsto</TH><TH align="right">Custo real</TH></tr></THead>
            <TBody>{c.processo.map(p => (
              <TR key={p.sequencia}><TD>{p.sequencia} {p.descricao} <span className="text-xs text-gray-500">· {p.centroNome}</span></TD>
                <TD align="right" className="tabular-nums">{brl(p.custoHora)}</TD><TD align="right" className="tabular-nums">{horas(p.minutosPrevistos)}</TD>
                <TD align="right" className="tabular-nums">{horas(p.minutosReais)}</TD><TD align="right" className="tabular-nums">{brl(p.custoPrevisto)}</TD><TD align="right" className="tabular-nums">{brl(p.custoReal)}</TD></TR>
            ))}</TBody>
          </Table>
        </div>
      )}
    </Modal>
  )
}
