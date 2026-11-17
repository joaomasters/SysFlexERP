import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { AlertTriangle } from 'lucide-react'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass, BuscaProduto, FilterTabs,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import { hojeISO } from '@/shared/utils/data'
import { STATUS_LOTE_MP, faixaTexto, type Liga, type LoteMp, type StatusLoteMp } from '@/types/industrial'
import { dataBR, num, useProdutosIndustriais } from '../comum'
import { MODULO_EXTRUSAO } from './LigasAba'

interface Opcao { id: number; nome: string }
interface Recursos { tiposMaquina: Opcao[]; fornecedores: Opcao[] }

type Filtro = 'todos' | StatusLoteMp
const FILTROS: { value: Filtro; label: string }[] = [
  { value: 'QUARENTENA', label: 'Em quarentena' }, { value: 'LIBERADO', label: 'Liberados' },
  { value: 'BLOQUEADO', label: 'Bloqueados' }, { value: 'todos', label: 'Todos' },
]

interface Form {
  numero: string; produtoId: number; ligaId: string; fornecedorId: string; certificado: string; dataRecebimento: string
  quantidade: string; diametro: string; comprimento: string; observacao: string; composicao: Record<string, string>
}

const numero = (v: string) => (v.trim() === '' ? null : Number(v))

/** Lotes de matéria-prima: recebidos em quarentena, liberados só com a composição dentro da liga. */
export default function LotesAba() {
  const qc = useQueryClient()
  const { podeCriar, podeEditar } = usePermissao()
  const [filtro, setFiltro] = useState<Filtro>('QUARENTENA')
  const [editando, setEditando] = useState<{ lote: LoteMp | null; form: Form } | null>(null)
  const [status, setStatus] = useState<{ lote: LoteMp; para: StatusLoteMp; motivo: string } | null>(null)
  const { data: produtos = [] } = useProdutosIndustriais()
  const { data: ligas = [] } = useQuery<Liga[]>({ queryKey: ['ind-ligas'], queryFn: () => api.get('/industrial/extrusao/ligas').then(r => r.data) })
  const { data: recursos } = useQuery<Recursos>({ queryKey: ['ind-ext-recursos'], queryFn: () => api.get('/industrial/extrusao/recursos').then(r => r.data) })
  const { data: lotes = [], isLoading } = useQuery<LoteMp[]>({
    queryKey: ['ind-lotes-mp', filtro],
    queryFn: () => api.get('/industrial/extrusao/lotes', { params: filtro === 'todos' ? {} : { status: filtro } }).then(r => r.data),
  })
  const pag = usePaginacao(lotes, filtro)
  const recarregar = () => qc.invalidateQueries({ queryKey: ['ind-lotes-mp'] })

  const ligaSel = ligas.find(l => String(l.id) === editando?.form.ligaId)

  const salvar = useMutation({
    mutationFn: () => {
      const f = editando!.form
      const corpo = {
        numero: f.numero, produtoId: f.produtoId, ligaId: Number(f.ligaId), fornecedorId: f.fornecedorId ? Number(f.fornecedorId) : null,
        certificado: f.certificado || null, dataRecebimento: f.dataRecebimento, quantidade: Number(f.quantidade),
        diametroMm: numero(f.diametro), comprimentoMm: numero(f.comprimento), observacao: f.observacao || null,
        composicao: Object.entries(f.composicao).filter(([, v]) => v.trim() !== '').map(([elemento, v]) => ({ elemento, percentual: Number(v) })),
        versao: editando!.lote?.versao ?? null,
      }
      return editando!.lote ? api.put(`/industrial/extrusao/lotes/${editando!.lote.id}`, corpo) : api.post('/industrial/extrusao/lotes', corpo)
    },
    onSuccess: () => { toast.success('Lote salvo em quarentena.'); recarregar(); setEditando(null) },
  })
  const mudarStatus = useMutation({
    mutationFn: () => api.post(`/industrial/extrusao/lotes/${status!.lote.id}/status`, { status: status!.para, versao: status!.lote.versao, motivo: status!.motivo || null }),
    onSuccess: () => { toast.success(`Lote ${STATUS_LOTE_MP[status!.para].rotulo.toLowerCase()}.`); recarregar(); setStatus(null) },
  })

  const abrir = (l?: LoteMp) => setEditando({
    lote: l ?? null,
    form: {
      numero: l?.numero ?? '', produtoId: l?.produtoId ?? 0, ligaId: l ? String(l.ligaId) : '', fornecedorId: l?.fornecedorId ? String(l.fornecedorId) : '',
      certificado: l?.certificado ?? '', dataRecebimento: l?.dataRecebimento ?? hojeISO(), quantidade: l ? String(l.quantidade) : '',
      diametro: l?.diametroMm != null ? String(l.diametroMm) : '', comprimento: l?.comprimentoMm != null ? String(l.comprimentoMm) : '',
      observacao: l?.observacao ?? '', composicao: Object.fromEntries((l?.composicao ?? []).map(c => [c.elemento, String(c.percentual)])),
    },
  })
  const f = editando?.form
  const mudar = (campo: Partial<Form>) => setEditando(e => e && { ...e, form: { ...e.form, ...campo } })

  function tentarSalvar() {
    if (!f!.numero.trim() || !f!.produtoId || !f!.ligaId || !(Number(f!.quantidade) > 0)) {
      toast.error('Informe número do lote, material, liga e quantidade.')
      return
    }
    salvar.mutate()
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex flex-wrap gap-2"><FilterTabs options={FILTROS} value={filtro} onChange={setFiltro} /></div>
        {podeCriar(MODULO_EXTRUSAO) && <Button onClick={() => abrir()}>Receber lote</Button>}
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Lote</TH><TH>Material</TH><TH>Liga</TH><TH>Recebido</TH><TH align="right">Saldo</TH><TH>Situação</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(l => (
                <TR key={l.id}>
                  <TD><div className="font-mono font-semibold">{l.numero}</div>{l.certificado && <div className="text-xs text-gray-500">cert. {l.certificado}</div>}</TD>
                  <TD><div>{l.produtoNome}</div>{l.fornecedorNome && <div className="text-xs text-gray-500">{l.fornecedorNome}</div>}</TD>
                  <TD className="font-mono">{l.ligaCodigo}</TD>
                  <TD className="tabular-nums text-sm">{dataBR(l.dataRecebimento)}</TD>
                  <TD align="right" className="tabular-nums">{num(l.saldo)} / {num(l.quantidade)} {l.unidade}</TD>
                  <TD>
                    <StatusBadge tone={STATUS_LOTE_MP[l.status].tom}>{STATUS_LOTE_MP[l.status].rotulo}</StatusBadge>
                    {l.desvios.length > 0 && (
                      <span className="ml-2 inline-flex items-center gap-1 text-xs text-danger-600" title={l.desvios.join('; ')}>
                        <AlertTriangle size={13} /> fora da liga
                      </span>
                    )}
                  </TD>
                  <TD align="right" className="whitespace-nowrap space-x-1">
                    {podeEditar(MODULO_EXTRUSAO) && l.status === 'QUARENTENA' && l.consumido === 0 && <Button size="sm" variant="secondary" onClick={() => abrir(l)}>Editar</Button>}
                    {podeEditar(MODULO_EXTRUSAO) && l.status === 'QUARENTENA' && (
                      <Button size="sm" variant="success" disabled={l.desvios.length > 0} onClick={() => setStatus({ lote: l, para: 'LIBERADO', motivo: '' })}>Liberar</Button>
                    )}
                    {podeEditar(MODULO_EXTRUSAO) && l.status !== 'BLOQUEADO' && (
                      <Button size="sm" variant="outline-danger" onClick={() => setStatus({ lote: l, para: 'BLOQUEADO', motivo: '' })}>Bloquear</Button>
                    )}
                    {podeEditar(MODULO_EXTRUSAO) && l.status !== 'QUARENTENA' && (
                      <Button size="sm" variant="ghost" onClick={() => setStatus({ lote: l, para: 'QUARENTENA', motivo: '' })}>Quarentena</Button>
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && lotes.length === 0 && <EmptyState>Nenhum lote {filtro === 'todos' ? 'recebido' : 'nesta situação'}.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="lotes" />}
      </div>

      {editando && f && (
        <Modal title={editando.lote ? `Lote ${editando.lote.numero}` : 'Receber lote de matéria-prima'} onClose={() => setEditando(null)} maxWidth="xl"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setEditando(null)}>Cancelar</Button>
            <Button fullWidth loading={salvar.isPending} onClick={tentarSalvar}>Salvar</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="sm:col-span-2">
              <Field label="Material *"><BuscaProduto produtos={produtos} value={f.produtoId} onSelect={p => mudar({ produtoId: p.id })} placeholder="Tarugo, lingote..." /></Field>
            </div>
            <Field label="Número do lote / corrida *" htmlFor="lote-numero"><input id="lote-numero" value={f.numero} maxLength={40} onChange={e => mudar({ numero: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Liga *" htmlFor="lote-liga">
              <select id="lote-liga" value={f.ligaId} onChange={e => mudar({ ligaId: e.target.value })} className={baseInputClass}>
                <option value="">Selecione...</option>
                {ligas.filter(l => l.ativo).map(l => <option key={l.id} value={l.id}>{l.codigo} · {l.descricao}</option>)}
              </select>
            </Field>
            <Field label="Fornecedor" htmlFor="lote-fornecedor">
              <select id="lote-fornecedor" value={f.fornecedorId} onChange={e => mudar({ fornecedorId: e.target.value })} className={baseInputClass}>
                <option value="">Não informado</option>
                {recursos?.fornecedores.map(x => <option key={x.id} value={x.id}>{x.nome}</option>)}
              </select>
            </Field>
            <Field label="Certificado" htmlFor="lote-cert"><input id="lote-cert" value={f.certificado} maxLength={60} onChange={e => mudar({ certificado: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Recebido em *" htmlFor="lote-data"><input id="lote-data" type="date" value={f.dataRecebimento} onChange={e => mudar({ dataRecebimento: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Quantidade *" htmlFor="lote-qtd" hint="Na unidade do material"><input id="lote-qtd" type="number" step="any" min="0" value={f.quantidade} onChange={e => mudar({ quantidade: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Diâmetro (mm)" htmlFor="lote-diam"><input id="lote-diam" type="number" step="any" min="0" value={f.diametro} onChange={e => mudar({ diametro: e.target.value })} className={baseInputClass} /></Field>
            <Field label="Comprimento (mm)" htmlFor="lote-comp"><input id="lote-comp" type="number" step="any" min="0" value={f.comprimento} onChange={e => mudar({ comprimento: e.target.value })} className={baseInputClass} /></Field>
          </div>
          {ligaSel && (
            <div>
              <h3 className="text-sm font-semibold text-gray-700 mb-2">Composição do certificado (%)</h3>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {ligaSel.elementos.map(e => {
                  const v = f.composicao[e.elemento] ?? ''
                  const fora = v !== '' && ((e.minimo != null && Number(v) < e.minimo) || (e.maximo != null && Number(v) > e.maximo))
                  return (
                    <Field key={e.elemento} label={e.elemento} htmlFor={`comp-${e.elemento}`} hint={faixaTexto(e.minimo, e.maximo)}>
                      <input id={`comp-${e.elemento}`} type="number" step="any" min="0" max="100" value={v}
                        onChange={ev => mudar({ composicao: { ...f.composicao, [e.elemento]: ev.target.value } })}
                        className={`${baseInputClass} ${fora ? 'border-danger-500 bg-danger-50' : ''}`} />
                    </Field>
                  )
                })}
              </div>
              <p className="text-xs text-gray-500 mt-2">O lote entra em quarentena. A liberação só passa com todos os elementos da liga dentro da faixa.</p>
            </div>
          )}
        </Modal>
      )}

      {status && (
        <Modal title={`${status.para === 'LIBERADO' ? 'Liberar' : status.para === 'BLOQUEADO' ? 'Bloquear' : 'Voltar para quarentena'} o lote ${status.lote.numero}`}
          onClose={() => setStatus(null)}
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setStatus(null)}>Voltar</Button>
            <Button fullWidth variant={status.para === 'BLOQUEADO' ? 'danger' : 'primary'} loading={mudarStatus.isPending}
              onClick={() => (status.para !== 'LIBERADO' && !status.motivo.trim() ? toast.error('Informe o motivo.') : mudarStatus.mutate())}>Confirmar</Button>
          </>}>
          {status.para === 'LIBERADO'
            ? <p className="text-sm text-gray-700">A composição confere com a liga {status.lote.ligaCodigo}. Depois de liberado, o lote pode ser consumido nas OPs.</p>
            : (
              <Field label="Motivo *" htmlFor="lote-motivo">
                <textarea id="lote-motivo" rows={3} maxLength={500} value={status.motivo} autoFocus
                  onChange={e => setStatus({ ...status, motivo: e.target.value })} className={baseInputClass} />
              </Field>
            )}
        </Modal>
      )}
    </div>
  )
}
