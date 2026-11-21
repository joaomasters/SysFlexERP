import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Plus, Trash2 } from 'lucide-react'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass, BuscaProduto,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { ETAPAS_RECEITA, faixaTexto, type EtapaReceita, type Liga, type Receita } from '@/types/industrial'
import { useProdutosIndustriais } from '../comum'
import { MODULO_EXTRUSAO } from './LigasAba'

interface Opcao { id: number; nome: string }
interface LinhaParam { codigo: string; nome: string; unidade: string; alvo: string; minimo: string; maximo: string; obrigatorio: boolean }
interface Form { codigo: string; descricao: string; etapa: EtapaReceita | ''; ligaId: string; tipoMaquinaId: string; produtoId: number; observacao: string; parametros: LinhaParam[] }

const numero = (v: string) => (v.trim() === '' ? null : Number(v))
const linhaVazia = (): LinhaParam => ({ codigo: '', nome: '', unidade: '', alvo: '', minimo: '', maximo: '', obrigatorio: true })
const txt = (v?: number | null) => (v == null ? '' : String(v))

/**
 * Receitas de processo por liga e tipo de máquina. O sistema não traz valores prontos:
 * temperaturas, tempos e velocidades são os que a engenharia da fábrica cadastrar.
 */
export default function ReceitasAba() {
  const qc = useQueryClient()
  const { podeCriar, podeEditar, podeExcluir } = usePermissao()
  const [editando, setEditando] = useState<{ id: number | null; form: Form } | null>(null)
  const { data: produtos = [] } = useProdutosIndustriais()
  const { data: ligas = [] } = useQuery<Liga[]>({ queryKey: ['ind-ligas'], queryFn: () => api.get('/industrial/extrusao/ligas').then(r => r.data) })
  const { data: recursos } = useQuery<{ tiposMaquina: Opcao[] }>({ queryKey: ['ind-ext-recursos'], queryFn: () => api.get('/industrial/extrusao/recursos').then(r => r.data) })
  const { data: receitas = [], isLoading } = useQuery<Receita[]>({
    queryKey: ['ind-receitas'], queryFn: () => api.get('/industrial/extrusao/receitas').then(r => r.data),
  })

  const salvar = useMutation({
    mutationFn: () => {
      const f = editando!.form
      const corpo = {
        codigo: f.codigo, descricao: f.descricao, etapa: f.etapa, ligaId: Number(f.ligaId), tipoMaquinaId: Number(f.tipoMaquinaId),
        produtoId: f.produtoId || null, observacao: f.observacao || null,
        parametros: f.parametros.filter(p => p.codigo.trim()).map(p => ({
          codigo: p.codigo, nome: p.nome, unidade: p.unidade || null, alvo: numero(p.alvo), minimo: numero(p.minimo), maximo: numero(p.maximo), obrigatorio: p.obrigatorio,
        })),
      }
      return editando!.id ? api.put(`/industrial/extrusao/receitas/${editando!.id}`, corpo) : api.post('/industrial/extrusao/receitas', corpo)
    },
    onSuccess: r => { toast.success(`Receita salva (revisão ${r.data.revisao}).`); qc.invalidateQueries({ queryKey: ['ind-receitas'] }); setEditando(null) },
  })
  const ativar = useMutation({
    mutationFn: ({ id, ativo }: { id: number; ativo: boolean }) => api.post(`/industrial/extrusao/receitas/${id}/${ativo ? 'reativar' : 'inativar'}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ind-receitas'] }),
  })

  const abrir = (r?: Receita) => setEditando({
    id: r?.id ?? null,
    form: {
      codigo: r?.codigo ?? '', descricao: r?.descricao ?? '', etapa: r?.etapa ?? '', ligaId: r ? String(r.ligaId) : '',
      tipoMaquinaId: r ? String(r.tipoMaquinaId) : '', produtoId: r?.produtoId ?? 0, observacao: r?.observacao ?? '',
      parametros: r?.parametros.length ? r.parametros.map(p => ({ codigo: p.codigo, nome: p.nome, unidade: p.unidade ?? '', alvo: txt(p.alvo), minimo: txt(p.minimo), maximo: txt(p.maximo), obrigatorio: p.obrigatorio }))
        : [linhaVazia()],
    },
  })
  const f = editando?.form
  const mudar = (campo: Partial<Form>) => setEditando(e => e && { ...e, form: { ...e.form, ...campo } })
  const mudarParam = (i: number, campo: Partial<LinhaParam>) => mudar({ parametros: f!.parametros.map((p, j) => (j === i ? { ...p, ...campo } : p)) })

  function tentarSalvar() {
    if (!f!.codigo.trim() || !f!.descricao.trim() || !f!.etapa || !f!.ligaId || !f!.tipoMaquinaId) {
      toast.error('Informe código, descrição, etapa, liga e tipo de máquina.')
      return
    }
    if (!f!.parametros.some(p => p.codigo.trim() && p.nome.trim())) { toast.error('Inclua ao menos um parâmetro.'); return }
    salvar.mutate()
  }

  return (
    <div>
      <div className="flex justify-end mb-4">{podeCriar(MODULO_EXTRUSAO) && <Button onClick={() => abrir()}>Nova receita</Button>}</div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Receita</TH><TH>Etapa</TH><TH>Liga · máquina</TH><TH>Parâmetros</TH><TH>Rev.</TH><TH /></tr></THead>
            <TBody>
              {receitas.map(r => (
                <TR key={r.id} className={r.ativo ? '' : 'opacity-50'}>
                  <TD><div className="font-mono font-semibold">{r.codigo}</div><div className="text-sm text-gray-600">{r.descricao}</div></TD>
                  <TD>{ETAPAS_RECEITA[r.etapa]}</TD>
                  <TD className="text-sm"><span className="font-mono">{r.ligaCodigo}</span> · {r.tipoMaquinaNome}{r.produtoNome && <div className="text-xs text-gray-500">só {r.produtoNome}</div>}</TD>
                  <TD className="text-xs text-gray-600">{r.parametros.map(p => `${p.nome}: ${faixaTexto(p.minimo, p.maximo, p.unidade)}`).join(' · ')}</TD>
                  <TD className="tabular-nums">{r.revisao}</TD>
                  <TD align="right" className="whitespace-nowrap">
                    {r.ativo && <StatusBadge tone="success" className="mr-2">Ativa</StatusBadge>}
                    {podeEditar(MODULO_EXTRUSAO) && r.ativo && <Button size="sm" variant="secondary" onClick={() => abrir(r)}>Editar</Button>}
                    {podeExcluir(MODULO_EXTRUSAO) && r.ativo && <Button size="sm" variant="ghost" onClick={() => ativar.mutate({ id: r.id, ativo: false })}>Inativar</Button>}
                    {podeEditar(MODULO_EXTRUSAO) && !r.ativo && <Button size="sm" variant="ghost" onClick={() => ativar.mutate({ id: r.id, ativo: true })}>Reativar</Button>}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && receitas.length === 0 && <EmptyState>Nenhuma receita cadastrada.</EmptyState>}
      </div>

      {editando && f && (
        <Modal title={editando.id ? `Receita ${f.codigo}` : 'Nova receita de processo'} onClose={() => setEditando(null)} maxWidth="2xl"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setEditando(null)}>Cancelar</Button>
            <Button fullWidth loading={salvar.isPending} onClick={tentarSalvar}>Salvar</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label="Código *" htmlFor="rec-codigo"><input id="rec-codigo" value={f.codigo} maxLength={20} onChange={e => mudar({ codigo: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Descrição *" htmlFor="rec-desc"><input id="rec-desc" value={f.descricao} maxLength={120} onChange={e => mudar({ descricao: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Etapa *" htmlFor="rec-etapa">
              <select id="rec-etapa" value={f.etapa} onChange={e => mudar({ etapa: e.target.value as EtapaReceita })} className={baseInputClass}>
                <option value="">Selecione...</option>
                {Object.entries(ETAPAS_RECEITA).map(([v, r]) => <option key={v} value={v}>{r}</option>)}
              </select>
            </Field>
            <Field label="Liga *" htmlFor="rec-liga">
              <select id="rec-liga" value={f.ligaId} onChange={e => mudar({ ligaId: e.target.value })} className={baseInputClass}>
                <option value="">Selecione...</option>
                {ligas.filter(l => l.ativo).map(l => <option key={l.id} value={l.id}>{l.codigo} · {l.descricao}</option>)}
              </select>
            </Field>
            <Field label="Tipo de máquina *" htmlFor="rec-tipo">
              <select id="rec-tipo" value={f.tipoMaquinaId} onChange={e => mudar({ tipoMaquinaId: e.target.value })} className={baseInputClass}>
                <option value="">Selecione...</option>
                {recursos?.tiposMaquina.map(t => <option key={t.id} value={t.id}>{t.nome}</option>)}
              </select>
            </Field>
            <Field label="Só para o produto" hint="Em branco: vale para qualquer produto da liga">
              <BuscaProduto produtos={produtos} value={f.produtoId} onSelect={p => mudar({ produtoId: p.id })} placeholder="Qualquer produto da liga" />
            </Field>
          </div>
          <div>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-gray-700">Parâmetros</h3>
              <Button size="sm" variant="secondary" onClick={() => mudar({ parametros: [...f.parametros, linhaVazia()] })}><Plus size={14} /> Parâmetro</Button>
            </div>
            <div className="space-y-2">
              {f.parametros.map((p, i) => (
                <div key={i} className="grid grid-cols-12 gap-2 items-center bg-gray-50 rounded-lg p-2">
                  <input aria-label={`Código do parâmetro ${i + 1}`} value={p.codigo} maxLength={30} placeholder="TEMP_TARUGO" onChange={e => mudarParam(i, { codigo: e.target.value })} className={`${baseInputClass} col-span-6 sm:col-span-2`} />
                  <input aria-label={`Nome do parâmetro ${i + 1}`} value={p.nome} maxLength={100} placeholder="Temperatura do tarugo" onChange={e => mudarParam(i, { nome: e.target.value })} className={`${baseInputClass} col-span-6 sm:col-span-3`} />
                  <input aria-label={`Unidade do parâmetro ${i + 1}`} value={p.unidade} maxLength={15} placeholder="°C" onChange={e => mudarParam(i, { unidade: e.target.value })} className={`${baseInputClass} col-span-3 sm:col-span-1`} />
                  <input aria-label={`Mínimo do parâmetro ${i + 1}`} type="number" step="any" value={p.minimo} placeholder="mín." onChange={e => mudarParam(i, { minimo: e.target.value })} className={`${baseInputClass} col-span-3 sm:col-span-2`} />
                  <input aria-label={`Alvo do parâmetro ${i + 1}`} type="number" step="any" value={p.alvo} placeholder="alvo" onChange={e => mudarParam(i, { alvo: e.target.value })} className={`${baseInputClass} col-span-3 sm:col-span-1`} />
                  <input aria-label={`Máximo do parâmetro ${i + 1}`} type="number" step="any" value={p.maximo} placeholder="máx." onChange={e => mudarParam(i, { maximo: e.target.value })} className={`${baseInputClass} col-span-3 sm:col-span-2`} />
                  <button type="button" aria-label={`Remover parâmetro ${i + 1}`} className="col-span-12 sm:col-span-1 p-2 text-gray-400 hover:text-danger-600 justify-self-end"
                    onClick={() => mudar({ parametros: f.parametros.length > 1 ? f.parametros.filter((_, j) => j !== i) : [linhaVazia()] })}><Trash2 size={15} /></button>
                </div>
              ))}
            </div>
            <p className="text-xs text-gray-500 mt-2">Cada alteração sobe a revisão. As medições já feitas guardam a faixa da época.</p>
          </div>
        </Modal>
      )}
    </div>
  )
}
