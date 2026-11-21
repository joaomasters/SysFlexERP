import { useState, type ReactNode } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Search, RotateCcw } from 'lucide-react'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass, ConfirmDialog,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { usePaginacao } from '@/shared/hooks/usePaginacao'

export const MODULO = 'IND_ESTRUTURA'

/** Campo do formulário. `opcoes` vira um select; `dias` vira os 7 botões de dia da semana. */
export interface Campo {
  nome: string
  rotulo: string
  tipo?: 'texto' | 'numero' | 'hora' | 'dias' | 'area'
  opcoes?: { valor: string | number; rotulo: string }[]
  obrigatorio?: boolean
  max?: number
  dica?: string
  /** Ocupa a linha inteira no formulário */
  largo?: boolean
}

export interface Coluna<T> { titulo: string; render: (item: T) => ReactNode; className?: string }

type Form = Record<string, string>

interface Props<T extends { id: number }> {
  /** Ex.: "unidade" — usado nos títulos ("Nova unidade") e nas mensagens */
  nome: string
  feminino?: boolean
  endpoint: string
  queryKey: string
  colunas: Coluna<T>[]
  campos: Campo[]
  paraForm: (item: T | null) => Form
  paraPayload: (form: Form) => Record<string, unknown>
  busca: (item: T) => string
  /** Itens com ativo=false aparecem esmaecidos e com Reativar. Sem `ativo`, não há inativar. */
  inativavel?: boolean
  acoesExtras?: (item: T) => ReactNode
  textoVazio?: string
}

const DIAS = ['S', 'T', 'Q', 'Q', 'S', 'S', 'D']
const NOMES_DIAS = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo']

/**
 * Cadastro simples da estrutura industrial: lista com busca e paginação, modal de criar/editar
 * e inativar/reativar. A validação de verdade fica no backend; aqui só os obrigatórios.
 */
export default function CadastroSimples<T extends { id: number; ativo?: boolean }>(props: Props<T>) {
  const { nome, feminino, endpoint, queryKey, colunas, campos, paraForm, paraPayload, busca, inativavel = true } = props
  const qc = useQueryClient()
  const { podeCriar, podeEditar, podeExcluir } = usePermissao()
  const [termo, setTermo] = useState('')
  const [form, setForm] = useState<(Form & { __id?: string }) | null>(null)
  const [tentou, setTentou] = useState(false)
  const [inativando, setInativando] = useState<T | null>(null)
  const novo = feminino ? 'Nova' : 'Novo'

  const { data: itens = [], isLoading } = useQuery<T[]>({
    queryKey: [queryKey],
    queryFn: () => api.get(endpoint).then(r => r.data),
  })
  const filtro = termo.trim().toLowerCase()
  const visiveis = itens.filter(i => !filtro || busca(i).toLowerCase().includes(filtro))
  const pag = usePaginacao(visiveis, filtro)

  const recarregar = () => qc.invalidateQueries({ queryKey: [queryKey] })

  const salvar = useMutation({
    mutationFn: (f: Form & { __id?: string }) =>
      f.__id ? api.put(`${endpoint}/${f.__id}`, paraPayload(f)) : api.post(endpoint, paraPayload(f)),
    onSuccess: (_, f) => {
      toast.success(f.__id ? 'Alterações salvas.' : `${nome[0].toUpperCase()}${nome.slice(1)} cadastrad${feminino ? 'a' : 'o'}.`)
      recarregar()
      setForm(null)
    },
  })

  const ativar = useMutation({
    mutationFn: ({ id, ativo }: { id: number; ativo: boolean }) => api.post(`${endpoint}/${id}/${ativo ? 'reativar' : 'inativar'}`),
    onSuccess: (_, v) => {
      toast.success(v.ativo ? 'Reativado.' : 'Inativado.')
      recarregar()
      setInativando(null)
    },
  })

  const faltando = (f: Form) => campos.filter(c => c.obrigatorio && !String(f[c.nome] ?? '').trim()).map(c => c.nome)

  function tentarSalvar() {
    if (!form) return
    setTentou(true)
    if (faltando(form).length) { toast.error('Preencha os campos obrigatórios.'); return }
    salvar.mutate(form)
  }

  const abrir = (item: T | null) => {
    setTentou(false)
    setForm({ ...paraForm(item), ...(item ? { __id: String(item.id) } : {}) })
  }

  return (
    <div>
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <div className="relative flex-1 min-w-[12rem] max-w-sm">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input value={termo} onChange={e => setTermo(e.target.value)} placeholder="Buscar..."
            aria-label={`Buscar ${nome}`} className={`${baseInputClass} pl-9`} />
        </div>
        {podeCriar(MODULO) && <Button onClick={() => abrir(null)}>{novo} {nome}</Button>}
      </div>

      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead>
              <tr>
                {colunas.map(c => <TH key={c.titulo}>{c.titulo}</TH>)}
                {inativavel && <TH>Situação</TH>}
                <TH />
              </tr>
            </THead>
            <TBody>
              {pag.itens.map(item => (
                <TR key={item.id} className={item.ativo === false ? 'opacity-50' : ''}>
                  {colunas.map(c => <TD key={c.titulo} className={c.className}>{c.render(item)}</TD>)}
                  {inativavel && (
                    <TD><StatusBadge tone={item.ativo ? 'success' : 'neutral'}>{item.ativo ? 'Ativo' : 'Inativo'}</StatusBadge></TD>
                  )}
                  <TD>
                    <div className="flex items-center justify-end gap-2 whitespace-nowrap">
                      {props.acoesExtras?.(item)}
                      {podeEditar(MODULO) && item.ativo !== false && (
                        <Button variant="secondary" size="sm" onClick={() => abrir(item)}>Editar</Button>
                      )}
                      {inativavel && item.ativo && podeExcluir(MODULO) && (
                        <Button variant="outline-danger" size="sm" onClick={() => setInativando(item)}>Inativar</Button>
                      )}
                      {inativavel && !item.ativo && podeEditar(MODULO) && (
                        <Button variant="success" size="sm" onClick={() => ativar.mutate({ id: item.id, ativo: true })}>
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
        {!isLoading && visiveis.length === 0 && (
          <EmptyState>{itens.length ? 'Nada encontrado.' : props.textoVazio ?? `Nenhum(a) ${nome} cadastrado(a).`}</EmptyState>
        )}
        {!isLoading && <Paginacao paginacao={pag} rotulo={nome} />}
      </div>

      {form && (
        <Modal title={form.__id ? `Editar ${nome}` : `${novo} ${nome}`} onClose={() => setForm(null)} maxWidth="lg"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setForm(null)}>Cancelar</Button>
            <Button fullWidth loading={salvar.isPending} onClick={tentarSalvar}>Salvar</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {campos.map(c => {
              const erro = tentou && faltando(form).includes(c.nome) ? 'Obrigatório.' : undefined
              const valor = form[c.nome] ?? ''
              const set = (v: string) => setForm({ ...form, [c.nome]: v })
              const id = `campo-${c.nome}`
              return (
                <Field key={c.nome} htmlFor={id} label={`${c.rotulo}${c.obrigatorio ? ' *' : ''}`} error={erro}
                  hint={c.dica} className={c.largo || c.tipo === 'area' || c.tipo === 'dias' ? 'sm:col-span-2' : ''}>
                  {c.opcoes ? (
                    <select id={id} value={valor} onChange={e => set(e.target.value)} className={baseInputClass}>
                      <option value="">Selecione...</option>
                      {c.opcoes.map(o => <option key={o.valor} value={o.valor}>{o.rotulo}</option>)}
                    </select>
                  ) : c.tipo === 'dias' ? (
                    <div className="flex gap-1.5" role="group" aria-label={c.rotulo}>
                      {DIAS.map((d, i) => {
                        const ligado = (valor || '1111100')[i] === '1'
                        return (
                          <button key={i} type="button" title={NOMES_DIAS[i]} aria-pressed={ligado}
                            onClick={() => {
                              const atual = (valor || '1111100').split('')
                              atual[i] = ligado ? '0' : '1'
                              set(atual.join(''))
                            }}
                            className={`w-9 h-9 rounded-full text-sm font-semibold ${ligado ? 'bg-primary-600 text-white' : 'border border-gray-300 text-gray-500'}`}>
                            {d}
                          </button>
                        )
                      })}
                    </div>
                  ) : c.tipo === 'area' ? (
                    <textarea id={id} value={valor} rows={2} onChange={e => set(e.target.value)} className={baseInputClass} />
                  ) : (
                    <input id={id} value={valor} maxLength={c.max}
                      type={c.tipo === 'numero' ? 'number' : c.tipo === 'hora' ? 'time' : 'text'}
                      step={c.tipo === 'numero' ? 'any' : undefined}
                      onChange={e => set(e.target.value)}
                      className={`${baseInputClass} ${erro ? '!border-danger-500' : ''}`} />
                  )}
                </Field>
              )
            })}
          </div>
        </Modal>
      )}

      {inativando && (
        <ConfirmDialog title={`Inativar ${nome}?`} confirmLabel="Inativar" loading={ativar.isPending}
          message="Ele deixa de aparecer nas opções dos novos cadastros. O histórico continua guardado e dá para reativar depois."
          onConfirm={() => ativar.mutate({ id: inativando.id, ativo: false })} onCancel={() => setInativando(null)} />
      )}
    </div>
  )
}

/** Número digitado ("" = não informado) para o JSON. */
export const numeroOuNulo = (v: string | undefined) => (v == null || v.trim() === '' ? null : Number(v))
export const textoOuNulo = (v: string | undefined) => (v == null || v.trim() === '' ? null : v.trim())
export const paraTexto = (v: unknown) => (v == null ? '' : String(v))
