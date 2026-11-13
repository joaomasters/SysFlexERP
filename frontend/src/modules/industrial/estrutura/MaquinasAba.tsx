import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Search, History } from 'lucide-react'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import {
  CATEGORIAS, STATUS_MAQUINA, UNIDADES_CAPACIDADE,
  type Centro, type EventoMaquina, type Maquina, type StatusMaquina, type TipoMaquina, type Turno,
} from '@/types/industrial'
import { MODULO, numeroOuNulo, paraTexto, textoOuNulo } from './CadastroSimples'

interface Form {
  id?: number; versao?: number; codigo: string; descricao: string; tipoMaquinaId: string; centroTrabalhoId: string
  capacidade: string; unidadeCapacidade: string; custoHora: string; fabricante: string; modelo: string
  numeroSerie: string; anoFabricacao: string; observacao: string
  /** parametroId → valor digitado */
  parametros: Record<number, string>
  turnoIds: number[]
}

const vazio: Form = {
  codigo: '', descricao: '', tipoMaquinaId: '', centroTrabalhoId: '', capacidade: '', unidadeCapacidade: '',
  custoHora: '', fabricante: '', modelo: '', numeroSerie: '', anoFabricacao: '', observacao: '', parametros: {}, turnoIds: [],
}

const dataHora = (iso?: string) => (iso ? new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '')

const ROTULO_EVENTO: Record<EventoMaquina['tipo'], string> = {
  STATUS: 'Status', MANUTENCAO_PREVENTIVA: 'Manutenção preventiva', MANUTENCAO_CORRETIVA: 'Manutenção corretiva',
  HORIMETRO: 'Horímetro', OBSERVACAO: 'Observação',
}

export default function MaquinasAba() {
  const qc = useQueryClient()
  const { podeCriar, podeEditar } = usePermissao()
  const [termo, setTermo] = useState('')
  const [form, setForm] = useState<Form | null>(null)
  const [detalhe, setDetalhe] = useState<Maquina | null>(null)

  const { data: maquinas = [], isLoading } = useQuery<Maquina[]>({
    queryKey: ['ind-maquinas'], queryFn: () => api.get('/industrial/maquinas').then(r => r.data),
  })
  const { data: tipos = [] } = useQuery<TipoMaquina[]>({
    queryKey: ['ind-tipos-maquina'], queryFn: () => api.get('/industrial/tipos-maquina').then(r => r.data),
  })
  const { data: centros = [] } = useQuery<Centro[]>({
    queryKey: ['ind-centros'], queryFn: () => api.get('/industrial/centros').then(r => r.data),
  })
  const { data: turnos = [] } = useQuery<Turno[]>({
    queryKey: ['ind-turnos'], queryFn: () => api.get('/industrial/turnos').then(r => r.data),
  })

  const filtro = termo.trim().toLowerCase()
  const visiveis = maquinas.filter(m => !filtro
    || `${m.codigo} ${m.descricao} ${m.tipoMaquinaNome} ${m.centroTrabalhoNome}`.toLowerCase().includes(filtro))
  const pag = usePaginacao(visiveis, filtro)
  const tipoDoForm = tipos.find(t => String(t.id) === form?.tipoMaquinaId)

  const recarregar = () => qc.invalidateQueries({ queryKey: ['ind-maquinas'] })

  const salvar = useMutation({
    mutationFn: (f: Form) => {
      const tipo = tipos.find(t => String(t.id) === f.tipoMaquinaId)
      const payload = {
        codigo: f.codigo, descricao: f.descricao, tipoMaquinaId: Number(f.tipoMaquinaId),
        centroTrabalhoId: Number(f.centroTrabalhoId), capacidade: numeroOuNulo(f.capacidade),
        unidadeCapacidade: textoOuNulo(f.unidadeCapacidade), custoHora: numeroOuNulo(f.custoHora),
        fabricante: textoOuNulo(f.fabricante), modelo: textoOuNulo(f.modelo), numeroSerie: textoOuNulo(f.numeroSerie),
        anoFabricacao: numeroOuNulo(f.anoFabricacao), observacao: textoOuNulo(f.observacao), versao: f.versao,
        turnoIds: f.turnoIds,
        parametros: (tipo?.parametros ?? [])
          .filter(p => p.id != null && (f.parametros[p.id] ?? '').trim() !== '')
          .map(p => p.tipoValor === 'NUMERO'
            ? { parametroId: p.id, valorNumero: Number(f.parametros[p.id!]) }
            : { parametroId: p.id, valorTexto: f.parametros[p.id!].trim() }),
      }
      return f.id ? api.put(`/industrial/maquinas/${f.id}`, payload) : api.post('/industrial/maquinas', payload)
    },
    onSuccess: () => {
      toast.success('Máquina salva.')
      recarregar()
      setForm(null)
    },
  })

  function abrir(m: Maquina | null) {
    setForm(m ? {
      id: m.id, versao: m.versao, codigo: m.codigo, descricao: m.descricao, tipoMaquinaId: String(m.tipoMaquinaId),
      centroTrabalhoId: String(m.centroTrabalhoId), capacidade: paraTexto(m.capacidade),
      unidadeCapacidade: m.unidadeCapacidade ?? '', custoHora: paraTexto(m.custoHora), fabricante: m.fabricante ?? '',
      modelo: m.modelo ?? '', numeroSerie: m.numeroSerie ?? '', anoFabricacao: paraTexto(m.anoFabricacao),
      observacao: m.observacao ?? '', turnoIds: m.turnoIds,
      parametros: Object.fromEntries(m.parametros.map(p => [p.parametroId, paraTexto(p.valorNumero ?? p.valorTexto)])),
    } : { ...vazio })
  }

  function tentarSalvar() {
    if (!form) return
    if (!form.codigo.trim() || !form.descricao.trim() || !form.tipoMaquinaId || !form.centroTrabalhoId) {
      toast.error('Preencha código, descrição, tipo e centro de trabalho.')
      return
    }
    const faltando = tipoDoForm?.parametros.find(p => p.obrigatorio && !(form.parametros[p.id!] ?? '').trim())
    if (faltando) { toast.error(`Informe ${faltando.nome}.`); return }
    salvar.mutate(form)
  }

  const set = (m: Partial<Form>) => form && setForm({ ...form, ...m })
  const input = (campo: keyof Form, rotulo: string, extra: Record<string, unknown> = {}) => (
    <Field label={rotulo} htmlFor={`maq-${campo}`}>
      <input id={`maq-${campo}`} value={form![campo] as string} onChange={e => set({ [campo]: e.target.value } as Partial<Form>)}
        className={baseInputClass} {...extra} />
    </Field>
  )

  return (
    <div>
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <div className="relative flex-1 min-w-[12rem] max-w-sm">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input value={termo} onChange={e => setTermo(e.target.value)} placeholder="Buscar máquina..." aria-label="Buscar máquina"
            className={`${baseInputClass} pl-9`} />
        </div>
        {podeCriar(MODULO) && <Button onClick={() => abrir(null)}>Nova máquina</Button>}
      </div>

      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Código</TH><TH>Máquina</TH><TH>Tipo</TH><TH>Onde</TH><TH>Capacidade</TH><TH>Horímetro</TH><TH>Status</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(m => (
                <TR key={m.id} className={m.status === 'INATIVA' ? 'opacity-50' : ''}>
                  <TD className="font-mono font-semibold">{m.codigo}</TD>
                  <TD className="font-medium">{m.descricao}</TD>
                  <TD className="text-gray-600">{m.tipoMaquinaNome}<div className="text-xs text-gray-400">{CATEGORIAS[m.categoria]}</div></TD>
                  <TD className="text-gray-600">{m.centroTrabalhoNome}<div className="text-xs text-gray-400">{m.setorNome} · {m.unidadeNome}</div></TD>
                  <TD className="tabular-nums text-gray-600">
                    {m.capacidade != null ? `${Number(m.capacidade).toLocaleString('pt-BR')} ${UNIDADES_CAPACIDADE[m.unidadeCapacidade!] ?? ''}` : '—'}
                  </TD>
                  <TD className="tabular-nums text-gray-600">{Number(m.horimetro).toLocaleString('pt-BR')} h</TD>
                  <TD><StatusBadge tone={STATUS_MAQUINA[m.status].tom}>{STATUS_MAQUINA[m.status].rotulo}</StatusBadge></TD>
                  <TD>
                    <div className="flex justify-end gap-2 whitespace-nowrap">
                      <Button variant="secondary" size="sm" onClick={() => setDetalhe(m)}><History size={14} /> Operação</Button>
                      {podeEditar(MODULO) && m.status !== 'INATIVA' && (
                        <Button variant="secondary" size="sm" onClick={() => abrir(m)}>Editar</Button>
                      )}
                    </div>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && visiveis.length === 0 && (
          <EmptyState>{maquinas.length ? 'Nenhuma máquina encontrada.' : 'Nenhuma máquina cadastrada. Cadastre antes o tipo de máquina e o centro de trabalho.'}</EmptyState>
        )}
        {!isLoading && <Paginacao paginacao={pag} rotulo="máquinas" />}
      </div>

      {form && (
        <Modal title={form.id ? `Editar máquina ${form.codigo}` : 'Nova máquina'} onClose={() => setForm(null)} maxWidth="2xl"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setForm(null)}>Cancelar</Button>
            <Button fullWidth loading={salvar.isPending} onClick={tentarSalvar}>Salvar</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {input('codigo', 'Código *', { maxLength: 20 })}
            <div className="sm:col-span-2">{input('descricao', 'Descrição *', { maxLength: 150 })}</div>
            <Field label="Tipo de máquina *" htmlFor="maq-tipo">
              <select id="maq-tipo" value={form.tipoMaquinaId} onChange={e => set({ tipoMaquinaId: e.target.value, parametros: {} })} className={baseInputClass}>
                <option value="">Selecione...</option>
                {tipos.filter(t => t.ativo || String(t.id) === form.tipoMaquinaId).map(t => <option key={t.id} value={t.id}>{t.nome}</option>)}
              </select>
            </Field>
            <div className="sm:col-span-2">
              <Field label="Centro de trabalho *" htmlFor="maq-centro">
                <select id="maq-centro" value={form.centroTrabalhoId} onChange={e => set({ centroTrabalhoId: e.target.value })} className={baseInputClass}>
                  <option value="">Selecione...</option>
                  {centros.filter(c => c.ativo || String(c.id) === form.centroTrabalhoId).map(c => (
                    <option key={c.id} value={c.id}>{c.nome} ({c.setorNome} · {c.unidadeNome})</option>
                  ))}
                </select>
              </Field>
            </div>
            {input('capacidade', 'Capacidade', { type: 'number', step: 'any', min: 0 })}
            <Field label="Unidade da capacidade" htmlFor="maq-unidade-cap">
              <select id="maq-unidade-cap" value={form.unidadeCapacidade} onChange={e => set({ unidadeCapacidade: e.target.value })} className={baseInputClass}>
                <option value="">—</option>
                {Object.entries(UNIDADES_CAPACIDADE).map(([v, r]) => <option key={v} value={v}>{r}</option>)}
              </select>
            </Field>
            {input('custoHora', 'Custo por hora (R$)', { type: 'number', step: '0.01', min: 0 })}
            {input('fabricante', 'Fabricante', { maxLength: 80 })}
            {input('modelo', 'Modelo', { maxLength: 80 })}
            {input('numeroSerie', 'Nº de série', { maxLength: 60 })}
            {input('anoFabricacao', 'Ano de fabricação', { type: 'number', min: 1900, max: 2100 })}
          </div>

          {tipoDoForm && tipoDoForm.parametros.length > 0 && (
            <div className="mt-5">
              <h3 className="text-sm font-semibold text-gray-700 mb-2">Limitações técnicas ({tipoDoForm.nome})</h3>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                {tipoDoForm.parametros.map(p => (
                  <Field key={p.id} htmlFor={`param-${p.id}`}
                    label={`${p.nome}${p.unidade ? ` (${p.unidade})` : ''}${p.obrigatorio ? ' *' : ''}`}
                    hint={p.tipoValor === 'NUMERO' && (p.minimo != null || p.maximo != null) ? `De ${p.minimo ?? '…'} a ${p.maximo ?? '…'}` : undefined}>
                    <input id={`param-${p.id}`} value={form.parametros[p.id!] ?? ''}
                      type={p.tipoValor === 'NUMERO' ? 'number' : 'text'} step="any"
                      onChange={e => set({ parametros: { ...form.parametros, [p.id!]: e.target.value } })} className={baseInputClass} />
                  </Field>
                ))}
              </div>
            </div>
          )}

          <div className="mt-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-2">Turnos em que trabalha</h3>
            {turnos.filter(t => t.ativo).length === 0 && <p className="text-xs text-gray-400">Nenhum turno cadastrado.</p>}
            <div className="flex flex-wrap gap-3">
              {turnos.filter(t => t.ativo || form.turnoIds.includes(t.id)).map(t => (
                <label key={t.id} className="flex items-center gap-1.5 text-sm text-gray-700">
                  <input type="checkbox" checked={form.turnoIds.includes(t.id)}
                    onChange={e => set({ turnoIds: e.target.checked ? [...form.turnoIds, t.id] : form.turnoIds.filter(x => x !== t.id) })} />
                  {t.nome} <span className="text-xs text-gray-400">{t.horaInicio.slice(0, 5)}–{t.horaFim.slice(0, 5)}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="mt-4">
            <Field label="Observação" htmlFor="maq-obs">
              <textarea id="maq-obs" rows={2} value={form.observacao} onChange={e => set({ observacao: e.target.value })} className={baseInputClass} />
            </Field>
          </div>
        </Modal>
      )}

      {detalhe && (
        <OperacaoMaquina maquina={maquinas.find(m => m.id === detalhe.id) ?? detalhe} onClose={() => setDetalhe(null)}
          onAlterada={recarregar} />
      )}
    </div>
  )
}

/** Status, horímetro, manutenção e histórico de uma máquina. */
function OperacaoMaquina({ maquina, onClose, onAlterada }: { maquina: Maquina; onClose: () => void; onAlterada: () => void }) {
  const qc = useQueryClient()
  const { podeEditar } = usePermissao()
  const editavel = podeEditar(MODULO)
  const [status, setStatus] = useState<StatusMaquina | ''>('')
  const [motivo, setMotivo] = useState('')
  const [horimetro, setHorimetro] = useState('')
  const [manut, setManut] = useState({ tipo: 'PREVENTIVA', descricao: '', inicio: '', fim: '', custo: '' })

  const { data: eventos = [], isLoading } = useQuery<EventoMaquina[]>({
    queryKey: ['ind-maquina-historico', maquina.id],
    queryFn: () => api.get(`/industrial/maquinas/${maquina.id}/historico`).then(r => r.data),
  })
  const depois = (msg: string) => {
    toast.success(msg)
    onAlterada()
    qc.invalidateQueries({ queryKey: ['ind-maquina-historico', maquina.id] })
  }

  const mudarStatus = useMutation({
    mutationFn: () => api.post(`/industrial/maquinas/${maquina.id}/status`, { status, motivo: motivo || null, versao: maquina.versao }),
    onSuccess: () => { setStatus(''); setMotivo(''); depois('Status alterado.') },
  })
  const lerHorimetro = useMutation({
    mutationFn: () => api.post(`/industrial/maquinas/${maquina.id}/horimetro`, { valor: Number(horimetro), versao: maquina.versao }),
    onSuccess: () => { setHorimetro(''); depois('Horímetro registrado.') },
  })
  const registrarManut = useMutation({
    mutationFn: () => api.post(`/industrial/maquinas/${maquina.id}/manutencoes`, {
      tipo: manut.tipo, descricao: manut.descricao, inicio: manut.inicio || null, fim: manut.fim || null,
      custo: numeroOuNulo(manut.custo),
    }),
    onSuccess: () => { setManut({ tipo: 'PREVENTIVA', descricao: '', inicio: '', fim: '', custo: '' }); depois('Manutenção registrada.') },
  })

  return (
    <Modal title={`${maquina.codigo} · ${maquina.descricao}`} onClose={onClose} maxWidth="2xl">
      <div className="flex flex-wrap items-center gap-3 text-sm mb-4">
        <StatusBadge tone={STATUS_MAQUINA[maquina.status].tom}>{STATUS_MAQUINA[maquina.status].rotulo}</StatusBadge>
        <span className="text-gray-500">Horímetro: <b className="tabular-nums">{Number(maquina.horimetro).toLocaleString('pt-BR')} h</b></span>
        {maquina.parametros.map(p => (
          <span key={p.parametroId} className="text-gray-500">{p.nome}: <b>{p.valorNumero ?? p.valorTexto}{p.unidade ? ` ${p.unidade}` : ''}</b></span>
        ))}
      </div>

      {editavel && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-5">
          <div className="border border-gray-100 rounded-xl p-3 space-y-2">
            <h3 className="text-sm font-semibold text-gray-700">Mudar status</h3>
            <select aria-label="Novo status" value={status} onChange={e => setStatus(e.target.value as StatusMaquina)} className={baseInputClass}>
              <option value="">Novo status...</option>
              {Object.entries(STATUS_MAQUINA).filter(([v]) => v !== maquina.status).map(([v, s]) => <option key={v} value={v}>{s.rotulo}</option>)}
            </select>
            <input aria-label="Motivo" value={motivo} maxLength={500} placeholder="Motivo (ex.: troca de resistência)"
              onChange={e => setMotivo(e.target.value)} className={baseInputClass} />
            <Button size="sm" disabled={!status} loading={mudarStatus.isPending} onClick={() => mudarStatus.mutate()}>Aplicar</Button>

            <h3 className="text-sm font-semibold text-gray-700 pt-2">Leitura do horímetro</h3>
            <div className="flex gap-2">
              <input aria-label="Leitura do horímetro" type="number" step="0.1" min={0} value={horimetro}
                placeholder={`Atual: ${maquina.horimetro} h`} onChange={e => setHorimetro(e.target.value)} className={baseInputClass} />
              <Button size="sm" variant="secondary" disabled={!horimetro} loading={lerHorimetro.isPending} onClick={() => lerHorimetro.mutate()}>Registrar</Button>
            </div>
          </div>

          <div className="border border-gray-100 rounded-xl p-3 space-y-2">
            <h3 className="text-sm font-semibold text-gray-700">Registrar manutenção</h3>
            <select aria-label="Tipo de manutenção" value={manut.tipo} onChange={e => setManut({ ...manut, tipo: e.target.value })} className={baseInputClass}>
              <option value="PREVENTIVA">Preventiva</option>
              <option value="CORRETIVA">Corretiva</option>
            </select>
            <textarea aria-label="O que foi feito" rows={2} value={manut.descricao} placeholder="O que foi feito"
              onChange={e => setManut({ ...manut, descricao: e.target.value })} className={baseInputClass} />
            <div className="grid grid-cols-2 gap-2">
              <Field label="Início"><input aria-label="Início" type="datetime-local" value={manut.inicio} onChange={e => setManut({ ...manut, inicio: e.target.value })} className={baseInputClass} /></Field>
              <Field label="Fim"><input aria-label="Fim" type="datetime-local" value={manut.fim} onChange={e => setManut({ ...manut, fim: e.target.value })} className={baseInputClass} /></Field>
            </div>
            <input aria-label="Custo da manutenção" type="number" step="0.01" min={0} value={manut.custo} placeholder="Custo (R$)"
              onChange={e => setManut({ ...manut, custo: e.target.value })} className={baseInputClass} />
            <Button size="sm" variant="secondary" disabled={!manut.descricao.trim()} loading={registrarManut.isPending}
              onClick={() => registrarManut.mutate()}>Registrar manutenção</Button>
          </div>
        </div>
      )}

      <h3 className="text-sm font-semibold text-gray-700 mb-2">Histórico</h3>
      {isLoading ? <LoadingState /> : eventos.length === 0 ? <p className="text-sm text-gray-400">Sem eventos ainda.</p> : (
        <ol className="space-y-2">
          {eventos.map(e => (
            <li key={e.id} className="border-l-2 border-gray-200 pl-3 text-sm">
              <div className="flex flex-wrap gap-x-2 text-xs text-gray-400">
                <span>{dataHora(e.createdAt)}</span><span>· {ROTULO_EVENTO[e.tipo]}</span>
                {e.usuarioNome && <span>· {e.usuarioNome}</span>}
              </div>
              <div className="text-gray-700">
                {e.tipo === 'STATUS' && e.statusAnterior && e.statusNovo && (
                  <b>{STATUS_MAQUINA[e.statusAnterior].rotulo} → {STATUS_MAQUINA[e.statusNovo].rotulo}: </b>
                )}
                {e.tipo === 'HORIMETRO' ? `${Number(e.horimetro).toLocaleString('pt-BR')} h` : e.descricao}
                {e.custo != null && <span className="text-gray-500"> · R$ {Number(e.custo).toLocaleString('pt-BR', { minimumFractionDigits: 2 })}</span>}
                {(e.inicio || e.fim) && <span className="text-gray-500"> · {dataHora(e.inicio)}{e.fim ? ` até ${dataHora(e.fim)}` : ''}</span>}
              </div>
            </li>
          ))}
        </ol>
      )}
    </Modal>
  )
}
