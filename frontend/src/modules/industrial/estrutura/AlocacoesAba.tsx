import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import { hojeISO } from '@/shared/utils/data'
import type { Alocacao, Centro, Operador, Turno } from '@/types/industrial'
import { MODULO, textoOuNulo } from './CadastroSimples'

const data = (iso?: string) => (iso ? iso.split('-').reverse().join('/') : '')

/** Quem trabalha em qual centro, em qual turno. Alocação não se apaga: é encerrada com data final. */
export default function AlocacoesAba() {
  const qc = useQueryClient()
  const { podeCriar, podeEditar } = usePermissao()
  const [soVigentes, setSoVigentes] = useState(true)
  const [form, setForm] = useState<{ usuarioId: string; centroTrabalhoId: string; turnoId: string; funcao: string; vigenteDe: string; vigenteAte: string } | null>(null)
  const [encerrando, setEncerrando] = useState<{ alocacao: Alocacao; ate: string } | null>(null)

  const { data: alocacoes = [], isLoading } = useQuery<Alocacao[]>({
    queryKey: ['ind-alocacoes'], queryFn: () => api.get('/industrial/alocacoes').then(r => r.data),
  })
  const { data: operadores = [] } = useQuery<Operador[]>({
    queryKey: ['ind-operadores'], queryFn: () => api.get('/industrial/operadores').then(r => r.data), enabled: !!form,
  })
  const { data: centros = [] } = useQuery<Centro[]>({
    queryKey: ['ind-centros'], queryFn: () => api.get('/industrial/centros').then(r => r.data),
  })
  const { data: turnos = [] } = useQuery<Turno[]>({
    queryKey: ['ind-turnos'], queryFn: () => api.get('/industrial/turnos').then(r => r.data),
  })

  const visiveis = alocacoes.filter(a => !soVigentes || a.vigente)
  const pag = usePaginacao(visiveis, String(soVigentes))
  const recarregar = () => qc.invalidateQueries({ queryKey: ['ind-alocacoes'] })

  const criar = useMutation({
    mutationFn: () => api.post('/industrial/alocacoes', {
      usuarioId: Number(form!.usuarioId), centroTrabalhoId: Number(form!.centroTrabalhoId), turnoId: Number(form!.turnoId),
      funcao: textoOuNulo(form!.funcao), vigenteDe: form!.vigenteDe, vigenteAte: form!.vigenteAte || null,
    }),
    onSuccess: () => { toast.success('Operador alocado.'); recarregar(); setForm(null) },
  })
  const encerrar = useMutation({
    mutationFn: () => api.post(`/industrial/alocacoes/${encerrando!.alocacao.id}/encerrar`, { vigenteAte: encerrando!.ate }),
    onSuccess: () => { toast.success('Alocação encerrada.'); recarregar(); setEncerrando(null) },
  })

  function tentarCriar() {
    if (!form?.usuarioId || !form.centroTrabalhoId || !form.turnoId || !form.vigenteDe) {
      toast.error('Escolha operador, centro, turno e o início.')
      return
    }
    criar.mutate()
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <label className="flex items-center gap-2 text-sm text-gray-600">
          <input type="checkbox" checked={soVigentes} onChange={e => setSoVigentes(e.target.checked)} /> Só as vigentes hoje
        </label>
        {podeCriar(MODULO) && (
          <Button onClick={() => setForm({ usuarioId: '', centroTrabalhoId: '', turnoId: '', funcao: '', vigenteDe: hojeISO(), vigenteAte: '' })}>
            Alocar operador
          </Button>
        )}
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Operador</TH><TH>Função</TH><TH>Centro de trabalho</TH><TH>Turno</TH><TH>Vigência</TH><TH>Situação</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(a => (
                <TR key={a.id}>
                  <TD className="font-medium">{a.usuarioNome ?? `Usuário ${a.usuarioId}`}</TD>
                  <TD className="text-gray-600">{a.funcao ?? '—'}</TD>
                  <TD>{a.centroTrabalhoNome}</TD>
                  <TD>{a.turnoNome}</TD>
                  <TD className="tabular-nums text-gray-600">{data(a.vigenteDe)} {a.vigenteAte ? `a ${data(a.vigenteAte)}` : 'em diante'}</TD>
                  <TD><StatusBadge tone={a.vigente ? 'success' : 'neutral'}>{a.vigente ? 'Vigente' : 'Fora da vigência'}</StatusBadge></TD>
                  <TD>
                    {podeEditar(MODULO) && (!a.vigenteAte || a.vigenteAte >= hojeISO()) && (
                      <Button variant="secondary" size="sm" onClick={() => setEncerrando({ alocacao: a, ate: hojeISO() })}>Encerrar</Button>
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && visiveis.length === 0 && <EmptyState>Nenhuma alocação {soVigentes ? 'vigente' : 'cadastrada'}.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="alocações" />}
      </div>

      {form && (
        <Modal title="Alocar operador" onClose={() => setForm(null)} maxWidth="lg"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setForm(null)}>Cancelar</Button>
            <Button fullWidth loading={criar.isPending} onClick={tentarCriar}>Alocar</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label="Operador *" htmlFor="aloc-operador" hint="Usuários ativos do sistema">
              <select id="aloc-operador" value={form.usuarioId} onChange={e => setForm({ ...form, usuarioId: e.target.value })} className={baseInputClass}>
                <option value="">Selecione...</option>
                {operadores.map(o => <option key={o.id} value={o.id}>{o.nome}</option>)}
              </select>
            </Field>
            <Field label="Função" htmlFor="aloc-funcao">
              <input id="aloc-funcao" value={form.funcao} maxLength={60} placeholder="Ex.: operador de prensa"
                onChange={e => setForm({ ...form, funcao: e.target.value })} className={baseInputClass} />
            </Field>
            <Field label="Centro de trabalho *" htmlFor="aloc-centro">
              <select id="aloc-centro" value={form.centroTrabalhoId} onChange={e => setForm({ ...form, centroTrabalhoId: e.target.value })} className={baseInputClass}>
                <option value="">Selecione...</option>
                {centros.filter(c => c.ativo).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </Field>
            <Field label="Turno *" htmlFor="aloc-turno">
              <select id="aloc-turno" value={form.turnoId} onChange={e => setForm({ ...form, turnoId: e.target.value })} className={baseInputClass}>
                <option value="">Selecione...</option>
                {turnos.filter(t => t.ativo).map(t => <option key={t.id} value={t.id}>{t.nome} ({t.horaInicio.slice(0, 5)}–{t.horaFim.slice(0, 5)})</option>)}
              </select>
            </Field>
            <Field label="A partir de *" htmlFor="aloc-de">
              <input id="aloc-de" type="date" value={form.vigenteDe} onChange={e => setForm({ ...form, vigenteDe: e.target.value })} className={baseInputClass} />
            </Field>
            <Field label="Até (opcional)" htmlFor="aloc-ate">
              <input id="aloc-ate" type="date" value={form.vigenteAte} min={form.vigenteDe} onChange={e => setForm({ ...form, vigenteAte: e.target.value })} className={baseInputClass} />
            </Field>
          </div>
        </Modal>
      )}

      {encerrando && (
        <Modal title={`Encerrar alocação de ${encerrando.alocacao.usuarioNome ?? 'operador'}`} onClose={() => setEncerrando(null)}
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setEncerrando(null)}>Cancelar</Button>
            <Button fullWidth loading={encerrar.isPending} onClick={() => encerrar.mutate()}>Encerrar</Button>
          </>}>
          <Field label="Último dia no centro" htmlFor="aloc-encerrar">
            <input id="aloc-encerrar" type="date" value={encerrando.ate} min={encerrando.alocacao.vigenteDe}
              onChange={e => setEncerrando({ ...encerrando, ate: e.target.value })} className={baseInputClass} />
          </Field>
        </Modal>
      )}
    </div>
  )
}
