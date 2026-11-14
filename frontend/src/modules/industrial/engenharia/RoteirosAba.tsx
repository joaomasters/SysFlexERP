import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Plus, Trash2 } from 'lucide-react'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass, BuscaProduto,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import { STATUS_VERSAO, type Roteiro, type RoteiroResumo } from '@/types/industrial'
import { num, useProdutosIndustriais } from '../comum'
import { MODULO_ENG, vigencia } from './EstruturasAba'

interface Recurso { id: number; codigo: string; nome: string }
interface Recursos { centros: Recurso[]; tiposMaquina: Recurso[] }

/** Roteiros de fabricação (todas as versões), filtráveis por produto. */
export default function RoteirosAba() {
  const { podeCriar } = usePermissao()
  const [produtoId, setProdutoId] = useState(0)
  const [aberto, setAberto] = useState<number | 'novo' | null>(null)
  const { data: produtos = [] } = useProdutosIndustriais()
  const { data: lista = [], isLoading } = useQuery<RoteiroResumo[]>({
    queryKey: ['ind-roteiros', produtoId],
    queryFn: () => api.get('/industrial/engenharia/roteiros', { params: produtoId ? { produtoId } : {} }).then(r => r.data),
  })
  const pag = usePaginacao(lista, String(produtoId))

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-3 mb-4">
        <div className="w-full sm:w-80">
          <Field label="Filtrar por produto">
            <BuscaProduto produtos={produtos} value={produtoId} onSelect={p => setProdutoId(p.id)} placeholder="Todos os produtos" />
          </Field>
        </div>
        <div className="flex gap-2">
          {produtoId !== 0 && <Button variant="ghost" onClick={() => setProdutoId(0)}>Limpar filtro</Button>}
          {podeCriar(MODULO_ENG) && <Button onClick={() => setAberto('novo')}>Novo roteiro</Button>}
        </div>
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Produto</TH><TH>Versão</TH><TH>Situação</TH><TH>Vigência</TH><TH align="right">Operações</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(r => (
                <TR key={r.id}>
                  <TD><span className="font-medium">{r.produtoNome}</span> <span className="text-xs text-gray-400 font-mono">{r.produtoCodigo}</span></TD>
                  <TD className="font-mono">v{r.versao}</TD>
                  <TD><StatusBadge tone={STATUS_VERSAO[r.status].tom}>{STATUS_VERSAO[r.status].rotulo}</StatusBadge></TD>
                  <TD className="text-gray-600 text-sm">{vigencia(r.vigenciaInicio, r.vigenciaFim)}</TD>
                  <TD align="right" className="tabular-nums">{r.operacoes}</TD>
                  <TD align="right"><Button size="sm" variant="secondary" onClick={() => setAberto(r.id)}>Abrir</Button></TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && lista.length === 0 && <EmptyState>Nenhum roteiro cadastrado{produtoId ? ' para este produto' : ''}.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="roteiros" />}
      </div>
      {aberto != null && <RoteiroModal key={aberto} id={aberto} onClose={() => setAberto(null)} onAbrir={setAberto} />}
    </div>
  )
}

interface OpForm { sequencia: string; descricao: string; centroTrabalhoId: string; tipoMaquinaId: string; setup: string; porUnidade: string; instrucoes: string }
interface Form { produtoId: number; observacao: string; operacoes: OpForm[] }

const opVazia = (seq: number): OpForm => ({ sequencia: String(seq), descricao: '', centroTrabalhoId: '', tipoMaquinaId: '', setup: '0', porUnidade: '0', instrucoes: '' })

function RoteiroModal({ id, onClose, onAbrir }: { id: number | 'novo'; onClose: () => void; onAbrir: (id: number) => void }) {
  const qc = useQueryClient()
  const { podeCriar, podeEditar, podeExcluir } = usePermissao()
  const { data: produtos = [] } = useProdutosIndustriais()
  const { data: recursos = { centros: [], tiposMaquina: [] } } = useQuery<Recursos>({
    queryKey: ['ind-eng-recursos'], queryFn: () => api.get('/industrial/engenharia/recursos').then(r => r.data),
  })
  const { data: roteiro } = useQuery<Roteiro>({
    queryKey: ['ind-roteiro', id], enabled: id !== 'novo',
    queryFn: () => api.get(`/industrial/engenharia/roteiros/${id}`).then(r => r.data),
  })
  const [form, setForm] = useState<Form | null>(id === 'novo' ? { produtoId: 0, observacao: '', operacoes: [opVazia(10)] } : null)
  const editavel = id === 'novo' || (roteiro?.status === 'RASCUNHO' && podeEditar(MODULO_ENG))

  useEffect(() => {
    if (roteiro && roteiro.status === 'RASCUNHO' && !form) {
      setForm({
        produtoId: roteiro.produtoId, observacao: roteiro.observacao ?? '',
        operacoes: roteiro.operacoes.length ? roteiro.operacoes.map(o => ({
          sequencia: String(o.sequencia), descricao: o.descricao, centroTrabalhoId: String(o.centroTrabalhoId),
          tipoMaquinaId: o.tipoMaquinaId ? String(o.tipoMaquinaId) : '', setup: String(o.tempoSetupMin),
          porUnidade: String(o.tempoPorUnidadeMin), instrucoes: o.instrucoes ?? '',
        })) : [opVazia(10)],
      })
    }
  }, [roteiro, form])

  function payload() {
    const f = form!
    return {
      produtoId: f.produtoId, observacao: f.observacao || null,
      operacoes: f.operacoes.map(o => ({
        sequencia: Number(o.sequencia), descricao: o.descricao, centroTrabalhoId: Number(o.centroTrabalhoId),
        tipoMaquinaId: o.tipoMaquinaId ? Number(o.tipoMaquinaId) : null, tempoSetupMin: Number(o.setup || 0),
        tempoPorUnidadeMin: Number(o.porUnidade || 0), instrucoes: o.instrucoes || null,
      })),
    }
  }

  function validar(): boolean {
    const f = form!
    if (!f.produtoId) { toast.error('Escolha o produto.'); return false }
    const i = f.operacoes.findIndex(o => !(Number(o.sequencia) > 0) || !o.descricao.trim() || !o.centroTrabalhoId)
    if (i >= 0) { toast.error(`Operação ${i + 1}: informe sequência, descrição e centro de trabalho.`); return false }
    return true
  }

  const salvar = useMutation({
    mutationFn: async (ativarDepois: boolean) => {
      const r = id === 'novo'
        ? await api.post('/industrial/engenharia/roteiros', payload())
        : await api.put(`/industrial/engenharia/roteiros/${id}`, payload())
      if (!ativarDepois) return { salvo: r.data as Roteiro, ativou: false }
      const ativado = await api.post(`/industrial/engenharia/roteiros/${r.data.id}/ativar`)
      return { salvo: ativado.data as Roteiro, ativou: true }
    },
    onSuccess: ({ salvo, ativou }) => {
      toast.success(ativou ? `Versão ${salvo.versao} ativada.` : 'Roteiro salvo.')
      qc.setQueryData(['ind-roteiro', salvo.id], salvo)
      qc.invalidateQueries({ queryKey: ['ind-roteiros'] })
      setForm(null)
      if (id === 'novo') onAbrir(salvo.id)
    },
    onError: () => qc.invalidateQueries({ queryKey: ['ind-roteiros'] }),
  })
  const acao = useMutation({
    mutationFn: (tipo: 'excluir' | 'obsoletar' | 'nova-versao') => tipo === 'excluir'
      ? api.delete(`/industrial/engenharia/roteiros/${id}`)
      : api.post(`/industrial/engenharia/roteiros/${id}/${tipo}`),
    onSuccess: (r, tipo) => {
      qc.invalidateQueries({ queryKey: ['ind-roteiros'] })
      qc.invalidateQueries({ queryKey: ['ind-roteiro'] })
      if (tipo === 'excluir') { toast.success('Rascunho excluído.'); onClose() }
      if (tipo === 'obsoletar') toast.success('Roteiro marcado como obsoleto.')
      if (tipo === 'nova-versao') { toast.success(`Rascunho v${r.data.versao} criado.`); onAbrir(r.data.id) }
    },
  })

  const atualizar = (i: number, campo: Partial<OpForm>) =>
    setForm(f => f && { ...f, operacoes: f.operacoes.map((o, j) => (j === i ? { ...o, ...campo } : o)) })
  const proximaSeq = () => (form ? Math.max(0, ...form.operacoes.map(o => Number(o.sequencia) || 0)) + 10 : 10)
  const unidade = produtos.find(p => p.id === (form?.produtoId ?? roteiro?.produtoId))?.unidadeMedida ?? roteiro?.unidade ?? 'un.'

  const titulo = id === 'novo' ? 'Novo roteiro de fabricação'
    : roteiro ? `Roteiro de ${roteiro.produtoNome} · v${roteiro.versao}` : 'Roteiro'

  const footer = editavel && form ? (
    <>
      {id !== 'novo' && podeExcluir(MODULO_ENG) && (
        <Button variant="outline-danger" onClick={() => window.confirm('Excluir este rascunho?') && acao.mutate('excluir')}>Excluir rascunho</Button>
      )}
      <Button variant="secondary" onClick={onClose}>Fechar</Button>
      <Button loading={salvar.isPending && !salvar.variables} onClick={() => validar() && salvar.mutate(false)}>Salvar rascunho</Button>
      {podeEditar(MODULO_ENG) && (
        <Button variant="success" loading={salvar.isPending && !!salvar.variables}
          onClick={() => validar() && window.confirm('Ativar esta versão? O roteiro ativo atual fica obsoleto e este não poderá mais ser alterado.') && salvar.mutate(true)}>
          Salvar e ativar
        </Button>
      )}
    </>
  ) : (
    <>
      <Button variant="secondary" onClick={onClose}>Fechar</Button>
      {roteiro?.status === 'ATIVO' && podeEditar(MODULO_ENG) && (
        <Button variant="warning" onClick={() => window.confirm('Tornar este roteiro obsoleto? O produto fica sem roteiro ativo e não poderá ter OP liberada.') && acao.mutate('obsoletar')}>
          Tornar obsoleto
        </Button>
      )}
      {roteiro && podeCriar(MODULO_ENG) && <Button loading={acao.isPending} onClick={() => acao.mutate('nova-versao')}>Nova versão a partir deste</Button>}
    </>
  )

  return (
    <Modal title={titulo} onClose={onClose} maxWidth="2xl" footer={footer}>
      {editavel && form ? (
        <div className="space-y-4">
          <Field label="Produto *" hint={id === 'novo' ? 'O produto fabricado por este roteiro' : 'O produto de um roteiro não muda'}>
            {id === 'novo'
              ? <BuscaProduto produtos={produtos} value={form.produtoId} onSelect={p => setForm({ ...form, produtoId: p.id })} placeholder="Buscar o produto fabricado..." />
              : <div className="py-1.5 font-medium">{roteiro?.produtoNome}</div>}
          </Field>
          {recursos.centros.length === 0 && (
            <p className="text-sm text-warning-700">Nenhum centro de trabalho ativo. Cadastre em Indústria → Estrutura industrial.</p>
          )}
          <div className="space-y-3">
            {form.operacoes.map((o, i) => (
              <div key={i} className="bg-gray-50 rounded-lg p-3 grid grid-cols-12 gap-2">
                <div className="col-span-3 sm:col-span-2">
                  <Field label="Seq. *" htmlFor={`op-seq-${i}`}>
                    <input id={`op-seq-${i}`} type="number" min="1" value={o.sequencia} onChange={e => atualizar(i, { sequencia: e.target.value })} className={baseInputClass} />
                  </Field>
                </div>
                <div className="col-span-9 sm:col-span-5">
                  <Field label="Descrição *" htmlFor={`op-desc-${i}`}>
                    <input id={`op-desc-${i}`} value={o.descricao} maxLength={120} placeholder="Ex.: extrudar, cortar, envelhecer"
                      onChange={e => atualizar(i, { descricao: e.target.value })} className={baseInputClass} />
                  </Field>
                </div>
                <div className="col-span-12 sm:col-span-5">
                  <Field label="Centro de trabalho *" htmlFor={`op-centro-${i}`}>
                    <select id={`op-centro-${i}`} value={o.centroTrabalhoId} onChange={e => atualizar(i, { centroTrabalhoId: e.target.value })} className={baseInputClass}>
                      <option value="">Selecione...</option>
                      {recursos.centros.map(c => <option key={c.id} value={c.id}>{c.codigo} · {c.nome}</option>)}
                    </select>
                  </Field>
                </div>
                <div className="col-span-12 sm:col-span-4">
                  <Field label="Tipo de máquina" htmlFor={`op-tipo-${i}`}>
                    <select id={`op-tipo-${i}`} value={o.tipoMaquinaId} onChange={e => atualizar(i, { tipoMaquinaId: e.target.value })} className={baseInputClass}>
                      <option value="">Qualquer do centro</option>
                      {recursos.tiposMaquina.map(t => <option key={t.id} value={t.id}>{t.nome}</option>)}
                    </select>
                  </Field>
                </div>
                <div className="col-span-6 sm:col-span-3">
                  <Field label="Preparação (min)" htmlFor={`op-setup-${i}`}>
                    <input id={`op-setup-${i}`} type="number" step="any" min="0" value={o.setup} onChange={e => atualizar(i, { setup: e.target.value })} className={baseInputClass} />
                  </Field>
                </div>
                <div className="col-span-6 sm:col-span-4">
                  <Field label={`Minutos por ${unidade}`} htmlFor={`op-unid-${i}`}>
                    <input id={`op-unid-${i}`} type="number" step="any" min="0" value={o.porUnidade} onChange={e => atualizar(i, { porUnidade: e.target.value })} className={baseInputClass} />
                  </Field>
                </div>
                <div className="col-span-12 sm:col-span-1 flex sm:items-end justify-end">
                  <button type="button" aria-label={`Remover operação ${i + 1}`} className="p-2 text-gray-400 hover:text-danger-600"
                    onClick={() => setForm({ ...form, operacoes: form.operacoes.length > 1 ? form.operacoes.filter((_, j) => j !== i) : [opVazia(10)] })}>
                    <Trash2 size={15} />
                  </button>
                </div>
                <div className="col-span-12">
                  <Field label="Instruções de trabalho" htmlFor={`op-instr-${i}`}>
                    <textarea id={`op-instr-${i}`} rows={2} value={o.instrucoes} onChange={e => atualizar(i, { instrucoes: e.target.value })} className={baseInputClass} />
                  </Field>
                </div>
              </div>
            ))}
          </div>
          <Button size="sm" variant="secondary" onClick={() => setForm({ ...form, operacoes: [...form.operacoes, opVazia(proximaSeq())] })}>
            <Plus size={14} /> Operação
          </Button>
          <p className="text-xs text-gray-500">Tempos padrão: a OP prevê preparação + minutos por unidade × quantidade, em cada operação.</p>
        </div>
      ) : !roteiro ? <LoadingState /> : (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
            <StatusBadge tone={STATUS_VERSAO[roteiro.status].tom}>{STATUS_VERSAO[roteiro.status].rotulo}</StatusBadge>
            <span className="text-gray-600">Vigência: {vigencia(roteiro.vigenciaInicio, roteiro.vigenciaFim)}</span>
          </div>
          <Table>
            <THead><tr><TH>Seq.</TH><TH>Operação</TH><TH>Centro</TH><TH>Tipo de máquina</TH><TH align="right">Preparação</TH><TH align="right">Min/{roteiro.unidade}</TH></tr></THead>
            <TBody>
              {roteiro.operacoes.map(o => (
                <TR key={o.id}>
                  <TD className="tabular-nums">{o.sequencia}</TD>
                  <TD><div className="font-medium">{o.descricao}</div>{o.instrucoes && <div className="text-xs text-gray-500 whitespace-pre-wrap">{o.instrucoes}</div>}</TD>
                  <TD>{o.centroTrabalhoNome}</TD>
                  <TD className="text-gray-600">{o.tipoMaquinaNome ?? 'Qualquer'}</TD>
                  <TD align="right" className="tabular-nums">{num(o.tempoSetupMin, 2)} min</TD>
                  <TD align="right" className="tabular-nums">{num(o.tempoPorUnidadeMin, 6)}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
          {roteiro.observacao && <p className="text-sm text-gray-600 whitespace-pre-wrap">{roteiro.observacao}</p>}
        </div>
      )}
    </Modal>
  )
}
