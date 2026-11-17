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
import { faixaTexto, type PlanoInspecao } from '@/types/industrial'
import { useProdutosIndustriais } from '../comum'
import { MODULO_QUALIDADE } from './LotesQualidadeAba'

interface Linha { codigo: string; nome: string; tipo: 'MEDIDA' | 'ATRIBUTO'; unidade: string; minimo: string; maximo: string; obrigatorio: boolean }
interface Form { codigo: string; descricao: string; produtoId: number; observacao: string; caracteristicas: Linha[] }

const vazia = (): Linha => ({ codigo: '', nome: '', tipo: 'MEDIDA', unidade: '', minimo: '', maximo: '', obrigatorio: true })
const numero = (v: string) => (v.trim() === '' ? null : Number(v))

/** Planos de inspeção por produto: com plano ativo, os lotes produzidos ficam em quarentena até a inspeção. */
export default function PlanosAba() {
  const qc = useQueryClient()
  const { podeCriar, podeEditar, podeExcluir } = usePermissao()
  const [editando, setEditando] = useState<{ id: number | null; form: Form } | null>(null)
  const { data: produtos = [] } = useProdutosIndustriais()
  const { data: planos = [], isLoading } = useQuery<PlanoInspecao[]>({ queryKey: ['ind-planos'], queryFn: () => api.get('/industrial/qualidade/planos').then(r => r.data) })

  const salvar = useMutation({
    mutationFn: () => {
      const f = editando!.form
      const corpo = {
        codigo: f.codigo, descricao: f.descricao, produtoId: f.produtoId, observacao: f.observacao || null,
        caracteristicas: f.caracteristicas.filter(c => c.codigo.trim()).map(c => ({
          codigo: c.codigo, nome: c.nome, tipo: c.tipo, unidade: c.unidade || null, minimo: numero(c.minimo), maximo: numero(c.maximo), obrigatorio: c.obrigatorio,
        })),
      }
      return editando!.id ? api.put(`/industrial/qualidade/planos/${editando!.id}`, corpo) : api.post('/industrial/qualidade/planos', corpo)
    },
    onSuccess: r => { toast.success(`Plano salvo (revisão ${r.data.revisao}).`); qc.invalidateQueries({ queryKey: ['ind-planos'] }); setEditando(null) },
  })
  const ativar = useMutation({
    mutationFn: ({ id, ativo }: { id: number; ativo: boolean }) => api.post(`/industrial/qualidade/planos/${id}/${ativo ? 'reativar' : 'inativar'}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ind-planos'] }),
  })

  const abrir = (p?: PlanoInspecao) => setEditando({
    id: p?.id ?? null,
    form: {
      codigo: p?.codigo ?? '', descricao: p?.descricao ?? '', produtoId: p?.produtoId ?? 0, observacao: p?.observacao ?? '',
      caracteristicas: p?.caracteristicas.length ? p.caracteristicas.map(c => ({
        codigo: c.codigo, nome: c.nome, tipo: c.tipo, unidade: c.unidade ?? '', minimo: c.minimo != null ? String(c.minimo) : '',
        maximo: c.maximo != null ? String(c.maximo) : '', obrigatorio: c.obrigatorio,
      })) : [vazia()],
    },
  })
  const f = editando?.form
  const mudar = (campo: Partial<Form>) => setEditando(e => e && { ...e, form: { ...e.form, ...campo } })
  const mudarLinha = (i: number, campo: Partial<Linha>) => mudar({ caracteristicas: f!.caracteristicas.map((c, j) => (j === i ? { ...c, ...campo } : c)) })

  return (
    <div>
      <div className="flex justify-end mb-4">{podeCriar(MODULO_QUALIDADE) && <Button onClick={() => abrir()}>Novo plano de inspeção</Button>}</div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Plano</TH><TH>Produto</TH><TH>Características</TH><TH>Rev.</TH><TH /></tr></THead>
            <TBody>
              {planos.map(p => (
                <TR key={p.id} className={p.ativo ? '' : 'opacity-50'}>
                  <TD><div className="font-mono font-semibold">{p.codigo}</div><div className="text-sm text-gray-600">{p.descricao}</div></TD>
                  <TD>{p.produtoNome}</TD>
                  <TD className="text-xs text-gray-600">{p.caracteristicas.map(c => c.tipo === 'MEDIDA' ? `${c.nome}: ${faixaTexto(c.minimo, c.maximo, c.unidade)}` : `${c.nome}: conforme`).join(' · ')}</TD>
                  <TD className="tabular-nums">{p.revisao}</TD>
                  <TD align="right" className="whitespace-nowrap">
                    <StatusBadge tone={p.ativo ? 'success' : 'neutral'} className="mr-2">{p.ativo ? 'Ativo' : 'Inativo'}</StatusBadge>
                    {podeEditar(MODULO_QUALIDADE) && <Button size="sm" variant="secondary" onClick={() => abrir(p)}>Editar</Button>}
                    {podeExcluir(MODULO_QUALIDADE) && p.ativo && <Button size="sm" variant="ghost" onClick={() => ativar.mutate({ id: p.id, ativo: false })}>Inativar</Button>}
                    {podeEditar(MODULO_QUALIDADE) && !p.ativo && <Button size="sm" variant="ghost" onClick={() => ativar.mutate({ id: p.id, ativo: true })}>Reativar</Button>}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && planos.length === 0 && <EmptyState>Nenhum plano. Sem plano, os lotes produzidos nascem liberados.</EmptyState>}
      </div>

      {editando && f && (
        <Modal title={editando.id ? `Plano ${f.codigo}` : 'Novo plano de inspeção'} onClose={() => setEditando(null)} maxWidth="2xl"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setEditando(null)}>Cancelar</Button>
            <Button fullWidth loading={salvar.isPending} onClick={() => (!f.codigo.trim() || !f.descricao.trim() || !f.produtoId ? toast.error('Informe código, descrição e produto.') : salvar.mutate())}>Salvar</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label="Código *" htmlFor="plano-codigo"><input id="plano-codigo" value={f.codigo} maxLength={20} onChange={e => mudar({ codigo: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Descrição *" htmlFor="plano-desc"><input id="plano-desc" value={f.descricao} maxLength={120} onChange={e => mudar({ descricao: e.target.value })} className={baseInputClass} /></Field>
            <div className="sm:col-span-2"><Field label="Produto *"><BuscaProduto produtos={produtos} value={f.produtoId} onSelect={p => mudar({ produtoId: p.id })} placeholder="Produto inspecionado..." /></Field></div>
          </div>
          <div>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-gray-700">Características</h3>
              <Button size="sm" variant="secondary" onClick={() => mudar({ caracteristicas: [...f.caracteristicas, vazia()] })}><Plus size={14} /> Característica</Button>
            </div>
            <div className="space-y-2">
              {f.caracteristicas.map((c, i) => (
                <div key={i} className="grid grid-cols-12 gap-2 items-center bg-gray-50 rounded-lg p-2">
                  <input aria-label={`Código da característica ${i + 1}`} value={c.codigo} maxLength={30} placeholder="ESPESSURA" onChange={e => mudarLinha(i, { codigo: e.target.value })} className={`${baseInputClass} col-span-6 sm:col-span-2`} />
                  <input aria-label={`Nome da característica ${i + 1}`} value={c.nome} maxLength={100} placeholder="Espessura da parede" onChange={e => mudarLinha(i, { nome: e.target.value })} className={`${baseInputClass} col-span-6 sm:col-span-3`} />
                  <select aria-label={`Tipo da característica ${i + 1}`} value={c.tipo} onChange={e => mudarLinha(i, { tipo: e.target.value as Linha['tipo'] })} className={`${baseInputClass} col-span-4 sm:col-span-2`}>
                    <option value="MEDIDA">Medida</option><option value="ATRIBUTO">Atributo</option>
                  </select>
                  {c.tipo === 'MEDIDA' ? (<>
                    <input aria-label={`Unidade da característica ${i + 1}`} value={c.unidade} maxLength={15} placeholder="mm" onChange={e => mudarLinha(i, { unidade: e.target.value })} className={`${baseInputClass} col-span-2 sm:col-span-1`} />
                    <input aria-label={`Mínimo da característica ${i + 1}`} type="number" step="any" value={c.minimo} placeholder="mín." onChange={e => mudarLinha(i, { minimo: e.target.value })} className={`${baseInputClass} col-span-3 sm:col-span-2`} />
                    <input aria-label={`Máximo da característica ${i + 1}`} type="number" step="any" value={c.maximo} placeholder="máx." onChange={e => mudarLinha(i, { maximo: e.target.value })} className={`${baseInputClass} col-span-3 sm:col-span-1`} />
                  </>) : <span className="col-span-8 sm:col-span-4 text-xs text-gray-500">Conforme / não conforme</span>}
                  <button type="button" aria-label={`Remover característica ${i + 1}`} className="col-span-12 sm:col-span-1 justify-self-end p-2 text-gray-400 hover:text-danger-600"
                    onClick={() => mudar({ caracteristicas: f.caracteristicas.length > 1 ? f.caracteristicas.filter((_, j) => j !== i) : [vazia()] })}><Trash2 size={15} /></button>
                </div>
              ))}
            </div>
            <p className="text-xs text-gray-500 mt-2">As faixas são as da especificação do cliente ou do desenho. Cada alteração sobe a revisão; as inspeções guardam a faixa da época.</p>
          </div>
        </Modal>
      )}
    </div>
  )
}
