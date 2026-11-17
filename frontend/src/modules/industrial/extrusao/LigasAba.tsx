import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Plus, Trash2 } from 'lucide-react'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { faixaTexto, type Liga } from '@/types/industrial'

export const MODULO_EXTRUSAO = 'IND_EXTRUSAO'

interface LinhaElemento { elemento: string; minimo: string; maximo: string }
interface Form { codigo: string; descricao: string; norma: string; densidade: string; observacao: string; elementos: LinhaElemento[] }

const numero = (v: string) => (v.trim() === '' ? null : Number(v))

/** Ligas e a faixa de composição de cada elemento, como a norma ou o cliente especificam. */
export default function LigasAba() {
  const qc = useQueryClient()
  const { podeCriar, podeEditar, podeExcluir } = usePermissao()
  const [editando, setEditando] = useState<{ id: number | null; form: Form } | null>(null)
  const { data: ligas = [], isLoading } = useQuery<Liga[]>({
    queryKey: ['ind-ligas'], queryFn: () => api.get('/industrial/extrusao/ligas').then(r => r.data),
  })

  const salvar = useMutation({
    mutationFn: () => {
      const f = editando!.form
      const corpo = {
        codigo: f.codigo, descricao: f.descricao, norma: f.norma || null, densidadeGCm3: numero(f.densidade), observacao: f.observacao || null,
        elementos: f.elementos.filter(e => e.elemento.trim()).map(e => ({ elemento: e.elemento.trim(), minimo: numero(e.minimo), maximo: numero(e.maximo) })),
      }
      return editando!.id ? api.put(`/industrial/extrusao/ligas/${editando!.id}`, corpo) : api.post('/industrial/extrusao/ligas', corpo)
    },
    onSuccess: () => { toast.success('Liga salva.'); qc.invalidateQueries({ queryKey: ['ind-ligas'] }); setEditando(null) },
  })
  const ativar = useMutation({
    mutationFn: ({ id, ativo }: { id: number; ativo: boolean }) => api.post(`/industrial/extrusao/ligas/${id}/${ativo ? 'reativar' : 'inativar'}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ind-ligas'] }),
  })

  const abrir = (l?: Liga) => setEditando({
    id: l?.id ?? null,
    form: {
      codigo: l?.codigo ?? '', descricao: l?.descricao ?? '', norma: l?.norma ?? '', densidade: l?.densidadeGCm3 != null ? String(l.densidadeGCm3) : '',
      observacao: l?.observacao ?? '',
      elementos: l?.elementos.length ? l.elementos.map(e => ({ elemento: e.elemento, minimo: e.minimo != null ? String(e.minimo) : '', maximo: e.maximo != null ? String(e.maximo) : '' }))
        : [{ elemento: '', minimo: '', maximo: '' }],
    },
  })
  const f = editando?.form
  const mudar = (campo: Partial<Form>) => setEditando(e => e && { ...e, form: { ...e.form, ...campo } })
  const mudarElemento = (i: number, campo: Partial<LinhaElemento>) =>
    mudar({ elementos: f!.elementos.map((e, j) => (j === i ? { ...e, ...campo } : e)) })

  return (
    <div>
      <div className="flex justify-end mb-4">{podeCriar(MODULO_EXTRUSAO) && <Button onClick={() => abrir()}>Nova liga</Button>}</div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Código</TH><TH>Descrição</TH><TH>Composição (% em massa)</TH><TH>Situação</TH><TH /></tr></THead>
            <TBody>
              {ligas.map(l => (
                <TR key={l.id} className={l.ativo ? '' : 'opacity-50'}>
                  <TD className="font-mono font-semibold">{l.codigo}</TD>
                  <TD><div className="font-medium">{l.descricao}</div>{l.norma && <div className="text-xs text-gray-500">{l.norma}</div>}</TD>
                  <TD className="text-xs text-gray-600">{l.elementos.map(e => `${e.elemento} ${faixaTexto(e.minimo, e.maximo)}`).join(' · ') || '—'}</TD>
                  <TD><StatusBadge tone={l.ativo ? 'success' : 'neutral'}>{l.ativo ? 'Ativa' : 'Inativa'}</StatusBadge></TD>
                  <TD align="right" className="whitespace-nowrap">
                    {podeEditar(MODULO_EXTRUSAO) && l.ativo && <Button size="sm" variant="secondary" onClick={() => abrir(l)}>Editar</Button>}
                    {podeExcluir(MODULO_EXTRUSAO) && l.ativo && <Button size="sm" variant="ghost" onClick={() => ativar.mutate({ id: l.id, ativo: false })}>Inativar</Button>}
                    {podeEditar(MODULO_EXTRUSAO) && !l.ativo && <Button size="sm" variant="ghost" onClick={() => ativar.mutate({ id: l.id, ativo: true })}>Reativar</Button>}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && ligas.length === 0 && <EmptyState>Nenhuma liga cadastrada. Cadastre as ligas que a fábrica usa, com a faixa de cada elemento.</EmptyState>}
      </div>

      {editando && f && (
        <Modal title={editando.id ? `Liga ${f.codigo}` : 'Nova liga'} onClose={() => setEditando(null)} maxWidth="xl"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setEditando(null)}>Cancelar</Button>
            <Button fullWidth loading={salvar.isPending} onClick={() => (!f.codigo.trim() || !f.descricao.trim() ? toast.error('Informe código e descrição.') : salvar.mutate())}>Salvar</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label="Código *" htmlFor="liga-codigo"><input id="liga-codigo" value={f.codigo} maxLength={20} onChange={e => mudar({ codigo: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Descrição *" htmlFor="liga-desc"><input id="liga-desc" value={f.descricao} maxLength={120} onChange={e => mudar({ descricao: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Norma" htmlFor="liga-norma"><input id="liga-norma" value={f.norma} maxLength={60} placeholder="Ex.: ABNT NBR 6834" onChange={e => mudar({ norma: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Densidade (g/cm³)" htmlFor="liga-dens"><input id="liga-dens" type="number" step="any" min="0" value={f.densidade} onChange={e => mudar({ densidade: e.target.value })} className={baseInputClass} /></Field>
          </div>
          <div>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-gray-700">Composição química (% em massa)</h3>
              <Button size="sm" variant="secondary" onClick={() => mudar({ elementos: [...f.elementos, { elemento: '', minimo: '', maximo: '' }] })}><Plus size={14} /> Elemento</Button>
            </div>
            <div className="space-y-2">
              {f.elementos.map((e, i) => (
                <div key={i} className="grid grid-cols-12 gap-2 items-center">
                  <input aria-label={`Elemento ${i + 1}`} value={e.elemento} maxLength={2} placeholder="Mg"
                    onChange={ev => mudarElemento(i, { elemento: ev.target.value })} className={`${baseInputClass} col-span-3`} />
                  <input aria-label={`Mínimo do elemento ${i + 1}`} type="number" step="any" min="0" max="100" value={e.minimo} placeholder="mín."
                    onChange={ev => mudarElemento(i, { minimo: ev.target.value })} className={`${baseInputClass} col-span-4`} />
                  <input aria-label={`Máximo do elemento ${i + 1}`} type="number" step="any" min="0" max="100" value={e.maximo} placeholder="máx."
                    onChange={ev => mudarElemento(i, { maximo: ev.target.value })} className={`${baseInputClass} col-span-4`} />
                  <button type="button" aria-label={`Remover elemento ${i + 1}`} className="col-span-1 p-2 text-gray-400 hover:text-danger-600"
                    onClick={() => mudar({ elementos: f.elementos.filter((_, j) => j !== i) })}><Trash2 size={15} /></button>
                </div>
              ))}
            </div>
            <p className="text-xs text-gray-500 mt-2">Use o símbolo químico (Si, Fe, Cu, Mn, Mg…). A liberação dos lotes confere a composição do certificado contra estas faixas.</p>
          </div>
        </Modal>
      )}
    </div>
  )
}
