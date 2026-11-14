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
import { STATUS_VERSAO, type Bom, type BomResumo, type ExplosaoLinha } from '@/types/industrial'
import { dataBR, num, useProdutosIndustriais } from '../comum'

export const MODULO_ENG = 'IND_ENGENHARIA'

export const vigencia = (de?: string, ate?: string) =>
  !de ? '—' : ate ? `${dataBR(de)} a ${dataBR(ate)}` : `desde ${dataBR(de)}`

/** Lista de estruturas (todas as versões), filtrável por produto. */
export default function EstruturasAba() {
  const { podeCriar } = usePermissao()
  const [produtoId, setProdutoId] = useState(0)
  const [aberta, setAberta] = useState<number | 'nova' | null>(null)
  const { data: produtos = [] } = useProdutosIndustriais()
  const { data: lista = [], isLoading } = useQuery<BomResumo[]>({
    queryKey: ['ind-boms', produtoId],
    queryFn: () => api.get('/industrial/engenharia/boms', { params: produtoId ? { produtoId } : {} }).then(r => r.data),
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
          {podeCriar(MODULO_ENG) && <Button onClick={() => setAberta('nova')}>Nova estrutura</Button>}
        </div>
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Produto</TH><TH>Versão</TH><TH>Situação</TH><TH align="right">Base</TH><TH>Vigência</TH><TH align="right">Componentes</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(b => (
                <TR key={b.id}>
                  <TD><span className="font-medium">{b.produtoNome}</span> <span className="text-xs text-gray-400 font-mono">{b.produtoCodigo}</span></TD>
                  <TD className="font-mono">v{b.versao}</TD>
                  <TD><StatusBadge tone={STATUS_VERSAO[b.status].tom}>{STATUS_VERSAO[b.status].rotulo}</StatusBadge></TD>
                  <TD align="right" className="tabular-nums">{num(b.quantidadeBase)} {b.unidade}</TD>
                  <TD className="text-gray-600 text-sm">{vigencia(b.vigenciaInicio, b.vigenciaFim)}</TD>
                  <TD align="right" className="tabular-nums">{b.itens}</TD>
                  <TD align="right"><Button size="sm" variant="secondary" onClick={() => setAberta(b.id)}>Abrir</Button></TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && lista.length === 0 && <EmptyState>Nenhuma estrutura cadastrada{produtoId ? ' para este produto' : ''}.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="estruturas" />}
      </div>
      {aberta != null && <BomModal key={aberta} id={aberta} onClose={() => setAberta(null)} onAbrir={setAberta} />}
    </div>
  )
}

interface LinhaForm { componenteId: number; quantidade: string; perda: string; observacao: string }
interface Form { produtoId: number; quantidadeBase: string; observacao: string; itens: LinhaForm[] }

const linhaVazia = (): LinhaForm => ({ componenteId: 0, quantidade: '', perda: '', observacao: '' })

function BomModal({ id, onClose, onAbrir }: { id: number | 'nova'; onClose: () => void; onAbrir: (id: number) => void }) {
  const qc = useQueryClient()
  const { podeCriar, podeEditar, podeExcluir } = usePermissao()
  const { data: produtos = [] } = useProdutosIndustriais()
  const { data: bom } = useQuery<Bom>({
    queryKey: ['ind-bom', id], enabled: id !== 'nova',
    queryFn: () => api.get(`/industrial/engenharia/boms/${id}`).then(r => r.data),
  })
  const [form, setForm] = useState<Form | null>(id === 'nova' ? { produtoId: 0, quantidadeBase: '1', observacao: '', itens: [linhaVazia()] } : null)
  const [qtdExplosao, setQtdExplosao] = useState('')
  const [explosao, setExplosao] = useState<ExplosaoLinha[] | null>(null)

  const editavel = id === 'nova' || (bom?.status === 'RASCUNHO' && podeEditar(MODULO_ENG))
  useEffect(() => {
    if (bom && bom.status === 'RASCUNHO' && !form) {
      setForm({
        produtoId: bom.produtoId, quantidadeBase: String(bom.quantidadeBase), observacao: bom.observacao ?? '',
        itens: bom.itens.length ? bom.itens.map(i => ({
          componenteId: i.componenteId, quantidade: String(i.quantidade), perda: i.perdaPercentual ? String(i.perdaPercentual) : '',
          observacao: i.observacao ?? '',
        })) : [linhaVazia()],
      })
    }
  }, [bom, form])

  const recarregar = () => {
    qc.invalidateQueries({ queryKey: ['ind-boms'] })
    qc.invalidateQueries({ queryKey: ['ind-bom'] })
  }

  function payload() {
    const f = form!
    return {
      produtoId: f.produtoId, quantidadeBase: Number(f.quantidadeBase), observacao: f.observacao || null,
      itens: f.itens.filter(i => i.componenteId).map(i => ({
        componenteId: i.componenteId, quantidade: Number(i.quantidade),
        perdaPercentual: i.perda.trim() ? Number(i.perda) : 0, observacao: i.observacao || null,
      })),
    }
  }

  function validar(): boolean {
    const f = form!
    if (!f.produtoId) { toast.error('Escolha o produto.'); return false }
    if (!(Number(f.quantidadeBase) > 0)) { toast.error('A quantidade base precisa ser maior que zero.'); return false }
    const ruim = f.itens.findIndex(i => i.componenteId && !(Number(i.quantidade) > 0))
    if (ruim >= 0) { toast.error(`Informe a quantidade do componente ${ruim + 1}.`); return false }
    return true
  }

  const salvar = useMutation({
    mutationFn: async (ativarDepois: boolean) => {
      const r = id === 'nova'
        ? await api.post('/industrial/engenharia/boms', payload())
        : await api.put(`/industrial/engenharia/boms/${id}`, payload())
      if (!ativarDepois) return { salvo: r.data as Bom, ativou: false }
      const ativada = await api.post(`/industrial/engenharia/boms/${r.data.id}/ativar`)
      return { salvo: ativada.data as Bom, ativou: true }
    },
    onSuccess: ({ salvo, ativou }) => {
      toast.success(ativou ? `Versão ${salvo.versao} ativada.` : 'Estrutura salva.')
      qc.setQueryData(['ind-bom', salvo.id], salvo)
      qc.invalidateQueries({ queryKey: ['ind-boms'] })
      setForm(null)
      if (id === 'nova') onAbrir(salvo.id)
    },
    onError: () => recarregar(),
  })
  const acao = useMutation({
    mutationFn: (tipo: 'excluir' | 'obsoletar' | 'nova-versao') => tipo === 'excluir'
      ? api.delete(`/industrial/engenharia/boms/${id}`)
      : api.post(`/industrial/engenharia/boms/${id}/${tipo}`),
    onSuccess: (r, tipo) => {
      recarregar()
      if (tipo === 'excluir') { toast.success('Rascunho excluído.'); onClose() }
      if (tipo === 'obsoletar') toast.success('Estrutura marcada como obsoleta.')
      if (tipo === 'nova-versao') { toast.success(`Rascunho v${r.data.versao} criado.`); setForm(null); onAbrir(r.data.id) }
    },
  })
  const explodir = useMutation({
    mutationFn: () => api.get(`/industrial/engenharia/boms/${id}/explosao`, { params: qtdExplosao ? { quantidade: qtdExplosao } : {} }).then(r => r.data),
    onSuccess: setExplosao,
  })

  const atualizarLinha = (i: number, campo: Partial<LinhaForm>) =>
    setForm(f => f && { ...f, itens: f.itens.map((l, j) => (j === i ? { ...l, ...campo } : l)) })
  const produtoSel = produtos.find(p => p.id === (form?.produtoId ?? bom?.produtoId))

  const titulo = id === 'nova' ? 'Nova estrutura de produto'
    : bom ? `Estrutura de ${bom.produtoNome} · v${bom.versao}` : 'Estrutura'

  const footer = editavel && form ? (
    <>
      {id !== 'nova' && podeExcluir(MODULO_ENG) && (
        <Button variant="outline-danger" onClick={() => window.confirm('Excluir este rascunho?') && acao.mutate('excluir')}>Excluir rascunho</Button>
      )}
      <Button variant="secondary" onClick={onClose}>Fechar</Button>
      <Button loading={salvar.isPending && !salvar.variables} onClick={() => validar() && salvar.mutate(false)}>Salvar rascunho</Button>
      {podeEditar(MODULO_ENG) && (
        <Button variant="success" loading={salvar.isPending && !!salvar.variables}
          onClick={() => validar() && window.confirm('Ativar esta versão? A versão ativa atual fica obsoleta e esta não poderá mais ser alterada.') && salvar.mutate(true)}>
          Salvar e ativar
        </Button>
      )}
    </>
  ) : (
    <>
      <Button variant="secondary" onClick={onClose}>Fechar</Button>
      {bom?.status === 'ATIVA' && podeEditar(MODULO_ENG) && (
        <Button variant="warning" onClick={() => window.confirm('Tornar esta estrutura obsoleta? O produto fica sem estrutura ativa e não poderá ter OP liberada.') && acao.mutate('obsoletar')}>
          Tornar obsoleta
        </Button>
      )}
      {bom && podeCriar(MODULO_ENG) && <Button loading={acao.isPending} onClick={() => acao.mutate('nova-versao')}>Nova versão a partir desta</Button>}
    </>
  )

  return (
    <Modal title={titulo} onClose={onClose} maxWidth="2xl" footer={footer}>
      {editavel && form ? (
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="sm:col-span-2">
              <Field label="Produto *" hint={id === 'nova' ? 'O produto que esta estrutura fabrica' : 'O produto de uma estrutura não muda'}>
                {id === 'nova'
                  ? <BuscaProduto produtos={produtos} value={form.produtoId} onSelect={p => setForm({ ...form, produtoId: p.id })} placeholder="Buscar o produto fabricado..." />
                  : <div className="py-1.5 font-medium">{bom?.produtoNome}</div>}
              </Field>
            </div>
            <Field label={`Quantidade base *${produtoSel ? ` (${produtoSel.unidadeMedida})` : ''}`} htmlFor="bom-base" hint="Os componentes abaixo são para esta quantidade">
              <input id="bom-base" type="number" step="any" min="0" value={form.quantidadeBase}
                onChange={e => setForm({ ...form, quantidadeBase: e.target.value })} className={baseInputClass} />
            </Field>
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-gray-700">Componentes</h3>
              <Button size="sm" variant="secondary" onClick={() => setForm({ ...form, itens: [...form.itens, linhaVazia()] })}>
                <Plus size={14} /> Componente
              </Button>
            </div>
            <div className="space-y-2">
              {form.itens.map((l, i) => {
                const comp = produtos.find(p => p.id === l.componenteId)
                return (
                  <div key={i} className="grid grid-cols-12 gap-2 items-start bg-gray-50 rounded-lg p-2">
                    <div className="col-span-12 sm:col-span-5">
                      <BuscaProduto produtos={produtos.filter(p => p.id !== form.produtoId)} value={l.componenteId}
                        onSelect={p => atualizarLinha(i, { componenteId: p.id })} placeholder={`Componente ${i + 1}...`} />
                    </div>
                    <div className="col-span-5 sm:col-span-3 flex items-center gap-1">
                      <input type="number" step="any" min="0" aria-label={`Quantidade do componente ${i + 1}`} placeholder="Qtd."
                        value={l.quantidade} onChange={e => atualizarLinha(i, { quantidade: e.target.value })} className={baseInputClass} />
                      <span className="text-xs text-gray-500 w-8">{comp?.unidadeMedida ?? ''}</span>
                    </div>
                    <div className="col-span-5 sm:col-span-3 flex items-center gap-1">
                      <input type="number" step="any" min="0" max="99.99" aria-label={`Perda do componente ${i + 1}`} placeholder="Perda"
                        value={l.perda} onChange={e => atualizarLinha(i, { perda: e.target.value })} className={baseInputClass} />
                      <span className="text-xs text-gray-500">%</span>
                    </div>
                    <div className="col-span-2 sm:col-span-1 flex justify-end">
                      <button type="button" aria-label={`Remover componente ${i + 1}`} className="p-2 text-gray-400 hover:text-danger-600"
                        onClick={() => setForm({ ...form, itens: form.itens.length > 1 ? form.itens.filter((_, j) => j !== i) : [linhaVazia()] })}>
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
            <p className="text-xs text-gray-500 mt-2">
              Quantidade na unidade do componente. A perda é sobre o que entra: com 10% de perda, entra 1/0,9 da quantidade líquida.
            </p>
          </div>

          <Field label="Observação" htmlFor="bom-obs">
            <textarea id="bom-obs" rows={2} value={form.observacao} onChange={e => setForm({ ...form, observacao: e.target.value })} className={baseInputClass} />
          </Field>
        </div>
      ) : !bom ? <LoadingState /> : (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
            <span><StatusBadge tone={STATUS_VERSAO[bom.status].tom}>{STATUS_VERSAO[bom.status].rotulo}</StatusBadge></span>
            <span className="text-gray-600">Base: <b className="tabular-nums">{num(bom.quantidadeBase)} {bom.unidade}</b></span>
            <span className="text-gray-600">Vigência: {vigencia(bom.vigenciaInicio, bom.vigenciaFim)}</span>
          </div>
          {bom.status === 'RASCUNHO' && <p className="text-sm text-warning-700">Rascunho: só quem pode editar a engenharia altera ou ativa.</p>}
          <Table>
            <THead><tr><TH>Componente</TH><TH align="right">Quantidade</TH><TH align="right">Perda</TH><TH>Observação</TH></tr></THead>
            <TBody>
              {bom.itens.map(i => (
                <TR key={i.id}>
                  <TD><span className="font-medium">{i.componenteNome}</span> <span className="text-xs text-gray-400 font-mono">{i.componenteCodigo}</span></TD>
                  <TD align="right" className="tabular-nums">{num(i.quantidade, 6)} {i.unidade}</TD>
                  <TD align="right" className="tabular-nums">{num(i.perdaPercentual, 2)}%</TD>
                  <TD className="text-gray-600 text-sm">{i.observacao ?? ''}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
          {bom.observacao && <p className="text-sm text-gray-600 whitespace-pre-wrap">{bom.observacao}</p>}

          <div className="border-t border-gray-100 pt-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-2">Explosão em todos os níveis</h3>
            <div className="flex flex-wrap items-end gap-2">
              <Field label={`Quantidade a produzir (${bom.unidade})`} htmlFor="bom-explosao-qtd">
                <input id="bom-explosao-qtd" type="number" step="any" min="0" placeholder={String(bom.quantidadeBase)} value={qtdExplosao}
                  onChange={e => setQtdExplosao(e.target.value)} className={`${baseInputClass} w-40`} />
              </Field>
              <Button variant="secondary" loading={explodir.isPending} onClick={() => explodir.mutate()}>Calcular</Button>
            </div>
            {explosao && (
              <div className="mt-3">
                <Table>
                  <THead><tr><TH>Nível</TH><TH>Item</TH><TH align="right">Necessário</TH></tr></THead>
                  <TBody>
                    {explosao.map((l, k) => (
                      <TR key={k}>
                        <TD className="tabular-nums">{l.nivel}</TD>
                        <TD><span style={{ paddingLeft: (l.nivel - 1) * 16 }} className="font-medium">{l.nome}</span>
                          {l.versaoEstrutura && <span className="ml-2 text-xs text-gray-400">fabricado · v{l.versaoEstrutura}</span>}</TD>
                        <TD align="right" className="tabular-nums">{num(l.quantidade, 4)} {l.unidade}</TD>
                      </TR>
                    ))}
                  </TBody>
                </Table>
              </div>
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}
