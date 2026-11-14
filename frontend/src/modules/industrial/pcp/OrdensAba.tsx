import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { AlertTriangle } from 'lucide-react'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass, BuscaProduto, FilterTabs,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import type { ButtonVariant } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import { hojeISO } from '@/shared/utils/data'
import {
  STATUS_OP, exigeMotivo,
  type MaquinaOpcao, type OrdemDetalhe, type OrdemResumo, type StatusOP,
} from '@/types/industrial'
import { dataBR, dataHoraBR, horas, num, useProdutosIndustriais } from '../comum'
import ExecucaoOPSecao from '../execucao/ExecucaoOPSecao'

export const MODULO_PCP = 'IND_PCP'

type Filtro = 'abertas' | 'todas' | StatusOP
const FILTROS: { value: Filtro; label: string }[] = [
  { value: 'abertas', label: 'Abertas' },
  { value: 'PLANEJADA', label: 'Planejadas' },
  { value: 'LIBERADA', label: 'Liberadas' },
  { value: 'EM_PRODUCAO', label: 'Em produção' },
  { value: 'CONCLUIDA', label: 'Concluídas' },
  { value: 'todas', label: 'Todas' },
]
const ABERTAS: StatusOP[] = ['PLANEJADA', 'LIBERADA', 'EM_PRODUCAO', 'PAUSADA']

/** Rótulo e cor do botão de cada transição. */
const ACAO_STATUS: Record<StatusOP, { rotulo: string; variante: ButtonVariant }> = {
  PLANEJADA: { rotulo: 'Voltar para planejada', variante: 'secondary' },
  LIBERADA: { rotulo: 'Liberar para produção', variante: 'success' },
  EM_PRODUCAO: { rotulo: 'Iniciar produção', variante: 'primary' },
  PAUSADA: { rotulo: 'Pausar', variante: 'warning' },
  CONCLUIDA: { rotulo: 'Concluir', variante: 'success' },
  ENCERRADA: { rotulo: 'Encerrar', variante: 'secondary' },
  CANCELADA: { rotulo: 'Cancelar OP', variante: 'outline-danger' },
}
const rotuloAcao = (de: StatusOP, para: StatusOP) =>
  de === 'PAUSADA' && para === 'EM_PRODUCAO' ? 'Retomar produção'
    : de === 'CONCLUIDA' && para === 'EM_PRODUCAO' ? 'Reabrir' : ACAO_STATUS[para].rotulo

export default function OrdensAba() {
  const { podeCriar } = usePermissao()
  const [filtro, setFiltro] = useState<Filtro>('abertas')
  const [busca, setBusca] = useState('')
  const [aberta, setAberta] = useState<number | null>(null)
  const [nova, setNova] = useState(false)
  const status = filtro === 'abertas' || filtro === 'todas' ? undefined : filtro
  const { data: ordens = [], isLoading } = useQuery<OrdemResumo[]>({
    queryKey: ['ind-ordens', status ?? 'todas'],
    queryFn: () => api.get('/industrial/ordens', { params: status ? { status } : {} }).then(r => r.data),
  })
  const t = busca.trim().toLowerCase()
  const visiveis = ordens
    .filter(o => filtro !== 'abertas' || ABERTAS.includes(o.status))
    .filter(o => !t || `${o.numero} ${o.produtoNome} ${o.produtoCodigo ?? ''} ${o.pedidoReferencia ?? ''}`.toLowerCase().includes(t))
  const pag = usePaginacao(visiveis, `${filtro}|${busca}`)

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex flex-wrap gap-2"><FilterTabs options={FILTROS} value={filtro} onChange={setFiltro} /></div>
        <div className="flex gap-2 w-full sm:w-auto">
          <input type="search" value={busca} onChange={e => setBusca(e.target.value)} placeholder="OP, produto ou pedido..."
            aria-label="Buscar ordens" className={`${baseInputClass} sm:w-64`} />
          {podeCriar(MODULO_PCP) && <Button onClick={() => setNova(true)}>Nova OP</Button>}
        </div>
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>OP</TH><TH>Produto</TH><TH align="right">Quantidade</TH><TH>Situação</TH><TH align="center">Prior.</TH><TH>Período previsto</TH><TH>Pedido</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(o => (
                <TR key={o.id}>
                  <TD className="font-mono font-semibold">{o.numero}</TD>
                  <TD className="font-medium">{o.produtoNome}</TD>
                  <TD align="right" className="tabular-nums">{num(o.quantidade)} {o.unidade}</TD>
                  <TD>
                    <StatusBadge tone={STATUS_OP[o.status].tom}>{STATUS_OP[o.status].rotulo}</StatusBadge>
                    {o.atrasada && <StatusBadge tone="danger" className="ml-1">Atrasada</StatusBadge>}
                  </TD>
                  <TD align="center" className="tabular-nums">{o.prioridade}</TD>
                  <TD className="text-sm text-gray-600 tabular-nums">{dataBR(o.inicioPrevisto)} a {dataBR(o.fimPrevisto)}</TD>
                  <TD className="text-sm text-gray-600">{o.pedidoReferencia ?? '—'}</TD>
                  <TD align="right"><Button size="sm" variant="secondary" onClick={() => setAberta(o.id)}>Abrir</Button></TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && visiveis.length === 0 && <EmptyState>Nenhuma ordem de produção {filtro === 'abertas' ? 'aberta' : 'neste filtro'}.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="ordens" />}
      </div>
      {nova && <OrdemForm onClose={() => setNova(false)} onSalva={id => { setNova(false); setAberta(id) }} />}
      {aberta != null && <OrdemModal key={aberta} id={aberta} onClose={() => setAberta(null)} />}
    </div>
  )
}

interface FormOP { produtoId: number; quantidade: string; prioridade: string; inicioPrevisto: string; fimPrevisto: string; pedidoReferencia: string; observacao: string }

function OrdemForm({ atual, onClose, onSalva }: { atual?: OrdemDetalhe; onClose: () => void; onSalva: (id: number) => void }) {
  const qc = useQueryClient()
  const { data: produtos = [] } = useProdutosIndustriais()
  const [f, setF] = useState<FormOP>(atual ? {
    produtoId: atual.ordem.produtoId, quantidade: String(atual.ordem.quantidade), prioridade: String(atual.ordem.prioridade),
    inicioPrevisto: atual.ordem.inicioPrevisto, fimPrevisto: atual.ordem.fimPrevisto,
    pedidoReferencia: atual.ordem.pedidoReferencia ?? '', observacao: atual.observacao ?? '',
  } : { produtoId: 0, quantidade: '', prioridade: '3', inicioPrevisto: hojeISO(), fimPrevisto: hojeISO(), pedidoReferencia: '', observacao: '' })
  const produto = produtos.find(p => p.id === f.produtoId)

  const salvar = useMutation({
    mutationFn: () => {
      const corpo = {
        produtoId: f.produtoId, quantidade: Number(f.quantidade), prioridade: Number(f.prioridade),
        inicioPrevisto: f.inicioPrevisto, fimPrevisto: f.fimPrevisto, clienteId: atual?.clienteId ?? null,
        pedidoReferencia: f.pedidoReferencia || null, observacao: f.observacao || null, versao: atual?.versao ?? null,
      }
      return (atual ? api.put(`/industrial/ordens/${atual.ordem.id}`, corpo) : api.post('/industrial/ordens', corpo)).then(r => r.data as OrdemDetalhe)
    },
    onSuccess: o => {
      toast.success(atual ? 'OP atualizada.' : `${o.ordem.numero} criada.`)
      qc.invalidateQueries({ queryKey: ['ind-ordens'] })
      qc.setQueryData(['ind-ordem', o.ordem.id], o)
      onSalva(o.ordem.id)
    },
  })

  function tentar() {
    if (!f.produtoId) return toast.error('Escolha o produto.')
    if (!(Number(f.quantidade) > 0)) return toast.error('Informe a quantidade.')
    if (!f.inicioPrevisto || !f.fimPrevisto) return toast.error('Informe o período previsto.')
    if (f.fimPrevisto < f.inicioPrevisto) return toast.error('O fim previsto não pode ser antes do início.')
    salvar.mutate()
  }

  return (
    <Modal title={atual ? `Editar ${atual.ordem.numero}` : 'Nova ordem de produção'} onClose={onClose} maxWidth="lg"
      footer={<>
        <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
        <Button fullWidth loading={salvar.isPending} onClick={tentar}>Salvar</Button>
      </>}>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div className="sm:col-span-2">
          <Field label="Produto *" hint={produto?.tipoItem ? undefined : 'Produto fabricado: precisa de estrutura e roteiro ativos para liberar'}>
            <BuscaProduto produtos={produtos} value={f.produtoId} onSelect={p => setF({ ...f, produtoId: p.id })} placeholder="Buscar o produto a fabricar..." />
          </Field>
        </div>
        <Field label={`Quantidade *${produto ? ` (${produto.unidadeMedida})` : ''}`} htmlFor="op-qtd">
          <input id="op-qtd" type="number" step="any" min="0" value={f.quantidade} onChange={e => setF({ ...f, quantidade: e.target.value })} className={baseInputClass} />
        </Field>
        <Field label="Prioridade" htmlFor="op-prioridade" hint="1 = mais urgente">
          <select id="op-prioridade" value={f.prioridade} onChange={e => setF({ ...f, prioridade: e.target.value })} className={baseInputClass}>
            {[1, 2, 3, 4, 5].map(p => <option key={p} value={p}>{p}</option>)}
          </select>
        </Field>
        <Field label="Início previsto *" htmlFor="op-inicio">
          <input id="op-inicio" type="date" value={f.inicioPrevisto} onChange={e => setF({ ...f, inicioPrevisto: e.target.value })} className={baseInputClass} />
        </Field>
        <Field label="Fim previsto *" htmlFor="op-fim">
          <input id="op-fim" type="date" value={f.fimPrevisto} min={f.inicioPrevisto} onChange={e => setF({ ...f, fimPrevisto: e.target.value })} className={baseInputClass} />
        </Field>
        <Field label="Pedido do cliente" htmlFor="op-pedido">
          <input id="op-pedido" value={f.pedidoReferencia} maxLength={40} onChange={e => setF({ ...f, pedidoReferencia: e.target.value })} className={baseInputClass} />
        </Field>
        <div className="sm:col-span-2">
          <Field label="Observação" htmlFor="op-obs">
            <textarea id="op-obs" rows={2} value={f.observacao} onChange={e => setF({ ...f, observacao: e.target.value })} className={baseInputClass} />
          </Field>
        </div>
      </div>
    </Modal>
  )
}

function OrdemModal({ id, onClose }: { id: number; onClose: () => void }) {
  const qc = useQueryClient()
  const { podeEditar } = usePermissao()
  const [editando, setEditando] = useState(false)
  const [transicao, setTransicao] = useState<{ para: StatusOP; motivo: string } | null>(null)
  const { data: d } = useQuery<OrdemDetalhe>({
    queryKey: ['ind-ordem', id], queryFn: () => api.get(`/industrial/ordens/${id}`).then(r => r.data),
  })
  const liberada = !!d && !d.previa && ['LIBERADA', 'EM_PRODUCAO', 'PAUSADA'].includes(d.ordem.status)
  const { data: maquinas = [] } = useQuery<MaquinaOpcao[]>({
    queryKey: ['ind-pcp-maquinas'], enabled: liberada && podeEditar(MODULO_PCP),
    queryFn: () => api.get('/industrial/pcp/maquinas').then(r => r.data),
  })

  const atualizar = (o: OrdemDetalhe) => {
    qc.setQueryData(['ind-ordem', id], o)
    qc.invalidateQueries({ queryKey: ['ind-ordens'] })
  }
  const mudar = useMutation({
    mutationFn: (t: { para: StatusOP; motivo: string }) =>
      api.post(`/industrial/ordens/${id}/status`, { status: t.para, versao: d!.versao, motivo: t.motivo || null }).then(r => r.data),
    onSuccess: (o: OrdemDetalhe) => { toast.success(`OP ${STATUS_OP[o.ordem.status].rotulo.toLowerCase()}.`); atualizar(o); setTransicao(null) },
    onError: () => qc.invalidateQueries({ queryKey: ['ind-ordem', id] }),
  })
  const maquina = useMutation({
    mutationFn: ({ operacaoId, maquinaId }: { operacaoId: number; maquinaId: number | null }) =>
      api.put(`/industrial/ordens/${id}/operacoes/${operacaoId}/maquina`, { maquinaId, versao: d!.versao }).then(r => r.data),
    onSuccess: (o: OrdemDetalhe) => { toast.success('Máquina definida.'); atualizar(o) },
    onError: () => qc.invalidateQueries({ queryKey: ['ind-ordem', id] }),
  })

  function pedir(para: StatusOP) {
    if (!d) return
    if (exigeMotivo(d.ordem.status, para) || para === 'LIBERADA') setTransicao({ para, motivo: '' })
    else mudar.mutate({ para, motivo: '' })
  }

  if (editando && d) return <OrdemForm atual={d} onClose={() => setEditando(false)} onSalva={() => setEditando(false)} />

  const o = d?.ordem
  return (
    <Modal title={o ? `${o.numero} · ${o.produtoNome}` : 'Ordem de produção'} onClose={onClose} maxWidth="2xl"
      footer={d && <>
        <Button variant="secondary" onClick={onClose}>Fechar</Button>
        {podeEditar(MODULO_PCP) && d.ordem.status === 'PLANEJADA' && <Button variant="secondary" onClick={() => setEditando(true)}>Editar</Button>}
        {podeEditar(MODULO_PCP) && d.proximosStatus.map(p => (
          <Button key={p} variant={ACAO_STATUS[p].variante} loading={mudar.isPending && mudar.variables?.para === p}
            disabled={p === 'LIBERADA' && d.pendenciasLiberacao.length > 0} onClick={() => pedir(p)}>
            {rotuloAcao(d.ordem.status, p)}
          </Button>
        ))}
      </>}>
      {!d || !o ? <LoadingState /> : (
        <div className="space-y-5">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
            <Info rotulo="Situação">
              <StatusBadge tone={STATUS_OP[o.status].tom}>{STATUS_OP[o.status].rotulo}</StatusBadge>
              {o.atrasada && <StatusBadge tone="danger" className="ml-1">Atrasada</StatusBadge>}
            </Info>
            <Info rotulo="Quantidade"><b className="tabular-nums">{num(o.quantidade)} {o.unidade}</b></Info>
            <Info rotulo="Período previsto"><span className="tabular-nums">{dataBR(o.inicioPrevisto)} a {dataBR(o.fimPrevisto)}</span></Info>
            <Info rotulo="Prioridade / pedido">{o.prioridade} · {o.pedidoReferencia ?? 'sem pedido'}</Info>
            {!d.previa && d.bomVersao != null && (
              <Info rotulo="Engenharia congelada">Estrutura v{d.bomVersao} · roteiro v{d.roteiroVersao}<div className="text-xs text-gray-500">liberada em {dataHoraBR(d.liberadaEm)}</div></Info>
            )}
          </div>
          {d.observacao && <p className="text-sm text-gray-600 whitespace-pre-wrap">{d.observacao}</p>}

          {d.pendenciasLiberacao.length > 0 && (
            <div className="rounded-lg border border-warning-200 bg-warning-50 p-3 text-sm text-warning-800">
              <div className="flex items-center gap-2 font-semibold mb-1"><AlertTriangle size={15} /> Antes de liberar</div>
              <ul className="list-disc pl-5">{d.pendenciasLiberacao.map(p => <li key={p}>{p}</li>)}</ul>
            </div>
          )}

          <section>
            <h3 className="text-sm font-semibold text-gray-700 mb-1">Materiais {d.previa && <span className="font-normal text-gray-500">(prévia da estrutura ativa; congela na liberação)</span>}</h3>
            {d.materiais.length === 0 ? <p className="text-sm text-gray-500">Sem materiais.</p> : (
              <Table>
                <THead><tr><TH>Material</TH><TH align="right">Líquido</TH><TH align="right">Perda</TH><TH align="right">Previsto</TH><TH align="right">Em estoque</TH><TH align="right">Falta</TH></tr></THead>
                <TBody>
                  {d.materiais.map(m => (
                    <TR key={m.produtoId}>
                      <TD><span className="font-medium">{m.nome}</span> <span className="text-xs text-gray-400 font-mono">{m.codigo}</span></TD>
                      <TD align="right" className="tabular-nums">{num(m.quantidadeLiquida, 4)}</TD>
                      <TD align="right" className="tabular-nums">{num(m.perdaPercentual, 2)}%</TD>
                      <TD align="right" className="tabular-nums font-medium">{num(m.quantidadePrevista, 4)} {m.unidade}</TD>
                      <TD align="right" className="tabular-nums">{num(m.saldoEstoque, 4)}</TD>
                      <TD align="right" className={`tabular-nums ${m.falta > 0 ? 'text-danger-600 font-semibold' : 'text-gray-400'}`}>{m.falta > 0 ? num(m.falta, 4) : '—'}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            )}
            <p className="text-xs text-gray-500 mt-1">Saldo atual do produto. A baixa acontece no consumo registrado (seção Execução).</p>
          </section>

          <section>
            <h3 className="text-sm font-semibold text-gray-700 mb-1">Operações</h3>
            {d.operacoes.length === 0 ? <p className="text-sm text-gray-500">Sem operações.</p> : (
              <Table>
                <THead><tr><TH>Seq.</TH><TH>Operação</TH><TH>Centro</TH><TH>Máquina</TH><TH align="right">Tempo previsto</TH></tr></THead>
                <TBody>
                  {d.operacoes.map(op => (
                    <TR key={op.sequencia}>
                      <TD className="tabular-nums">{op.sequencia}</TD>
                      <TD><div className="font-medium">{op.descricao}</div>{op.tipoMaquinaNome && <div className="text-xs text-gray-500">{op.tipoMaquinaNome}</div>}</TD>
                      <TD>{op.centroTrabalhoNome}</TD>
                      <TD>
                        {liberada && op.id && podeEditar(MODULO_PCP) && op.status !== 'CONCLUIDA' ? (
                          <select aria-label={`Máquina da operação ${op.sequencia}`} value={op.maquinaId ?? ''} disabled={maquina.isPending}
                            className="text-sm border border-gray-300 rounded-lg px-2 py-1"
                            onChange={e => maquina.mutate({ operacaoId: op.id!, maquinaId: e.target.value ? Number(e.target.value) : null })}>
                            <option value="">A definir</option>
                            {maquinas.filter(m => m.centroTrabalhoId === op.centroTrabalhoId && (!op.tipoMaquinaId || m.tipoMaquinaId === op.tipoMaquinaId))
                              .map(m => <option key={m.id} value={m.id}>{m.codigo} · {m.descricao}</option>)}
                          </select>
                        ) : <span className="text-sm text-gray-600">{op.maquinaCodigo ? `${op.maquinaCodigo} · ${op.maquinaDescricao}` : '—'}</span>}
                      </TD>
                      <TD align="right" className="tabular-nums">{horas(op.tempoPrevistoMin)}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            )}
          </section>

          {!d.previa && <ExecucaoOPSecao ordemId={o.id} />}

          <section>
            <h3 className="text-sm font-semibold text-gray-700 mb-1">Histórico</h3>
            <ul className="space-y-1 text-sm">
              {d.eventos.map(e => (
                <li key={e.id} className="text-gray-600">
                  <span className="tabular-nums text-gray-400">{dataHoraBR(e.createdAt)}</span>{' '}
                  {e.statusAnterior ? `${STATUS_OP[e.statusAnterior].rotulo} → ${STATUS_OP[e.statusNovo].rotulo}` : `Criada como ${STATUS_OP[e.statusNovo].rotulo.toLowerCase()}`}
                  {e.motivo && <>: <i>{e.motivo}</i></>}
                  {e.usuarioNome && <span className="text-gray-400"> · {e.usuarioNome}</span>}
                </li>
              ))}
            </ul>
          </section>
        </div>
      )}

      {transicao && d && (
        <Modal title={rotuloAcao(d.ordem.status, transicao.para)} onClose={() => setTransicao(null)}
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setTransicao(null)}>Voltar</Button>
            <Button fullWidth variant={ACAO_STATUS[transicao.para].variante === 'outline-danger' ? 'danger' : ACAO_STATUS[transicao.para].variante}
              loading={mudar.isPending}
              onClick={() => exigeMotivo(d.ordem.status, transicao.para) && !transicao.motivo.trim()
                ? toast.error('Informe o motivo.') : mudar.mutate(transicao)}>
              Confirmar
            </Button>
          </>}>
          {transicao.para === 'LIBERADA' ? (
            <p className="text-sm text-gray-700">
              A estrutura e o roteiro ativos de hoje serão copiados para a OP. Mudanças futuras na engenharia não alteram esta OP.
            </p>
          ) : (
            <Field label="Motivo *" htmlFor="op-motivo">
              <textarea id="op-motivo" rows={3} maxLength={500} value={transicao.motivo} autoFocus
                onChange={e => setTransicao({ ...transicao, motivo: e.target.value })} className={baseInputClass} />
            </Field>
          )}
        </Modal>
      )}
    </Modal>
  )
}

function Info({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-xs text-gray-500 mb-0.5">{rotulo}</div>
      <div>{children}</div>
    </div>
  )
}
