import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, RotateCcw } from 'lucide-react'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { CATEGORIAS, type CategoriaMaquina, type Parametro, type TipoMaquina } from '@/types/industrial'
import { MODULO, numeroOuNulo, paraTexto } from './CadastroSimples'

interface ParamForm { id?: number; codigo: string; nome: string; unidade: string; tipoValor: 'NUMERO' | 'TEXTO'; minimo: string; maximo: string; obrigatorio: boolean }
interface Form { id?: number; codigo: string; nome: string; categoria: CategoriaMaquina | ''; parametros: ParamForm[] }

const paramVazio = (): ParamForm => ({ codigo: '', nome: '', unidade: '', tipoValor: 'NUMERO', minimo: '', maximo: '', obrigatorio: false })

const resumoFaixa = (p: Parametro) =>
  p.tipoValor === 'TEXTO' ? 'texto'
    : p.minimo != null || p.maximo != null ? `${p.minimo ?? '…'} a ${p.maximo ?? '…'}${p.unidade ? ` ${p.unidade}` : ''}`
      : p.unidade ?? 'número'

/**
 * Tipos de máquina e os parâmetros técnicos de cada um. Ex.: o tipo "Forno de tarugos" tem
 * "Temperatura máxima (°C)"; a "Prensa de extrusão" tem "Força (tf)" e "Diâmetro do recipiente (mm)".
 * Nada de temperatura ou força fixa no sistema: quem define os limites é a engenharia da fábrica.
 */
export default function TiposMaquinaAba() {
  const qc = useQueryClient()
  const { podeCriar, podeEditar, podeExcluir } = usePermissao()
  const [form, setForm] = useState<Form | null>(null)

  const { data: tipos = [], isLoading } = useQuery<TipoMaquina[]>({
    queryKey: ['ind-tipos-maquina'],
    queryFn: () => api.get('/industrial/tipos-maquina').then(r => r.data),
  })

  const salvar = useMutation({
    mutationFn: (f: Form) => {
      const payload = {
        codigo: f.codigo, nome: f.nome, categoria: f.categoria,
        parametros: f.parametros.map(p => ({
          id: p.id, codigo: p.codigo, nome: p.nome, unidade: p.unidade || null, tipoValor: p.tipoValor,
          minimo: numeroOuNulo(p.minimo), maximo: numeroOuNulo(p.maximo), obrigatorio: p.obrigatorio,
        })),
      }
      return f.id ? api.put(`/industrial/tipos-maquina/${f.id}`, payload) : api.post('/industrial/tipos-maquina', payload)
    },
    onSuccess: () => {
      toast.success('Tipo de máquina salvo.')
      qc.invalidateQueries({ queryKey: ['ind-tipos-maquina'] })
      setForm(null)
    },
  })

  const ativar = useMutation({
    mutationFn: ({ id, ativo }: { id: number; ativo: boolean }) => api.post(`/industrial/tipos-maquina/${id}/${ativo ? 'reativar' : 'inativar'}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ind-tipos-maquina'] }),
  })

  function abrir(t: TipoMaquina | null) {
    setForm(t ? {
      id: t.id, codigo: t.codigo, nome: t.nome, categoria: t.categoria,
      parametros: t.parametros.map(p => ({
        id: p.id, codigo: p.codigo, nome: p.nome, unidade: p.unidade ?? '', tipoValor: p.tipoValor,
        minimo: paraTexto(p.minimo), maximo: paraTexto(p.maximo), obrigatorio: p.obrigatorio,
      })),
    } : { codigo: '', nome: '', categoria: '', parametros: [] })
  }

  function tentarSalvar() {
    if (!form) return
    if (!form.codigo.trim() || !form.nome.trim() || !form.categoria) { toast.error('Preencha código, nome e categoria.'); return }
    if (form.parametros.some(p => !p.codigo.trim() || !p.nome.trim())) { toast.error('Todo parâmetro precisa de código e nome.'); return }
    salvar.mutate(form)
  }

  const setParam = (i: number, mudanca: Partial<ParamForm>) =>
    form && setForm({ ...form, parametros: form.parametros.map((p, j) => (j === i ? { ...p, ...mudanca } : p)) })

  return (
    <div>
      <div className="flex justify-between items-center gap-3 mb-4">
        <p className="text-sm text-gray-500">Cada tipo define os parâmetros técnicos que as máquinas dele informam.</p>
        {podeCriar(MODULO) && <Button onClick={() => abrir(null)}>Novo tipo</Button>}
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Código</TH><TH>Nome</TH><TH>Categoria</TH><TH>Parâmetros</TH><TH>Situação</TH><TH /></tr></THead>
            <TBody>
              {tipos.map(t => (
                <TR key={t.id} className={t.ativo ? '' : 'opacity-50'}>
                  <TD className="font-mono font-semibold">{t.codigo}</TD>
                  <TD className="font-medium">{t.nome}</TD>
                  <TD>{CATEGORIAS[t.categoria]}</TD>
                  <TD className="text-xs text-gray-500">
                    {t.parametros.length === 0 ? '—' : t.parametros.map(p => `${p.nome} (${resumoFaixa(p)})`).join(' · ')}
                  </TD>
                  <TD><StatusBadge tone={t.ativo ? 'success' : 'neutral'}>{t.ativo ? 'Ativo' : 'Inativo'}</StatusBadge></TD>
                  <TD>
                    <div className="flex justify-end gap-2 whitespace-nowrap">
                      {t.ativo && podeEditar(MODULO) && <Button variant="secondary" size="sm" onClick={() => abrir(t)}>Editar</Button>}
                      {t.ativo && podeExcluir(MODULO) && (
                        <Button variant="outline-danger" size="sm" onClick={() => ativar.mutate({ id: t.id, ativo: false })}>Inativar</Button>
                      )}
                      {!t.ativo && podeEditar(MODULO) && (
                        <Button variant="success" size="sm" onClick={() => ativar.mutate({ id: t.id, ativo: true })}>
                          <RotateCcw size={14} /> Reativar
                        </Button>
                      )}
                    </div>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && tipos.length === 0 && <EmptyState>Nenhum tipo de máquina cadastrado.</EmptyState>}
      </div>

      {form && (
        <Modal title={form.id ? 'Editar tipo de máquina' : 'Novo tipo de máquina'} onClose={() => setForm(null)} maxWidth="2xl"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setForm(null)}>Cancelar</Button>
            <Button fullWidth loading={salvar.isPending} onClick={tentarSalvar}>Salvar</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Field label="Código *" htmlFor="tipo-codigo">
              <input id="tipo-codigo" value={form.codigo} maxLength={20} onChange={e => setForm({ ...form, codigo: e.target.value })} className={baseInputClass} />
            </Field>
            <Field label="Nome *" htmlFor="tipo-nome">
              <input id="tipo-nome" value={form.nome} maxLength={100} onChange={e => setForm({ ...form, nome: e.target.value })} className={baseInputClass} />
            </Field>
            <Field label="Categoria *" htmlFor="tipo-categoria">
              <select id="tipo-categoria" value={form.categoria} onChange={e => setForm({ ...form, categoria: e.target.value as CategoriaMaquina })} className={baseInputClass}>
                <option value="">Selecione...</option>
                {Object.entries(CATEGORIAS).map(([v, r]) => <option key={v} value={v}>{r}</option>)}
              </select>
            </Field>
          </div>

          <div className="mt-5">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-gray-700">Parâmetros técnicos</h3>
              <Button variant="secondary" size="sm" onClick={() => setForm({ ...form, parametros: [...form.parametros, paramVazio()] })}>
                <Plus size={14} /> Parâmetro
              </Button>
            </div>
            {form.parametros.length === 0 && (
              <p className="text-xs text-gray-400">Sem parâmetros. Ex.: para um forno, "Temperatura máxima" em °C com a faixa permitida.</p>
            )}
            <div className="space-y-2">
              {form.parametros.map((p, i) => (
                <div key={i} className="grid grid-cols-2 sm:grid-cols-12 gap-2 items-end border border-gray-100 rounded-lg p-2">
                  <Field label="Código" className="sm:col-span-2">
                    <input aria-label="Código do parâmetro" value={p.codigo} maxLength={30} onChange={e => setParam(i, { codigo: e.target.value })} className={baseInputClass} />
                  </Field>
                  <Field label="Nome" className="sm:col-span-3">
                    <input aria-label="Nome do parâmetro" value={p.nome} maxLength={100} onChange={e => setParam(i, { nome: e.target.value })} className={baseInputClass} />
                  </Field>
                  <Field label="Valor" className="sm:col-span-2">
                    <select aria-label="Tipo de valor" value={p.tipoValor} onChange={e => setParam(i, { tipoValor: e.target.value as 'NUMERO' | 'TEXTO' })} className={baseInputClass}>
                      <option value="NUMERO">Número</option>
                      <option value="TEXTO">Texto</option>
                    </select>
                  </Field>
                  <Field label="Unidade">
                    <input aria-label="Unidade" value={p.unidade} maxLength={15} placeholder="°C" onChange={e => setParam(i, { unidade: e.target.value })} className={baseInputClass} />
                  </Field>
                  <Field label="Mín.">
                    <input aria-label="Mínimo" type="number" step="any" disabled={p.tipoValor === 'TEXTO'} value={p.minimo} onChange={e => setParam(i, { minimo: e.target.value })} className={baseInputClass} />
                  </Field>
                  <Field label="Máx.">
                    <input aria-label="Máximo" type="number" step="any" disabled={p.tipoValor === 'TEXTO'} value={p.maximo} onChange={e => setParam(i, { maximo: e.target.value })} className={baseInputClass} />
                  </Field>
                  <label className="flex items-center gap-1.5 text-xs text-gray-600 pb-2">
                    <input type="checkbox" checked={p.obrigatorio} onChange={e => setParam(i, { obrigatorio: e.target.checked })} /> Obrig.
                  </label>
                  <button type="button" aria-label="Remover parâmetro" title="Remover"
                    onClick={() => setForm({ ...form, parametros: form.parametros.filter((_, j) => j !== i) })}
                    className="pb-2 text-gray-400 hover:text-danger-600 justify-self-end">
                    <Trash2 size={16} />
                  </button>
                </div>
              ))}
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
