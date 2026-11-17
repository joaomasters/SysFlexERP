import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass, FilterTabs,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import { DISPOSICOES_NC, ORIGENS_NC, type LoteProduto, type NaoConformidade } from '@/types/industrial'
import { dataBR } from '../comum'
import { MODULO_QUALIDADE } from './LotesQualidadeAba'

type Filtro = 'ABERTA' | 'ENCERRADA' | 'todas'

/** Não conformidades: abertas pela inspeção ou manualmente; o encerramento decide o destino do lote. */
export default function NaoConformidadesAba() {
  const qc = useQueryClient()
  const { podeCriar, podeEditar } = usePermissao()
  const [filtro, setFiltro] = useState<Filtro>('ABERTA')
  const [nova, setNova] = useState<{ origem: NaoConformidade['origem']; loteCodigo: string; descricao: string } | null>(null)
  const [encerrando, setEncerrando] = useState<{ nc: NaoConformidade; disposicao: string; causa: string; acao: string } | null>(null)
  const { data: ncs = [], isLoading } = useQuery<NaoConformidade[]>({
    queryKey: ['ind-ncs', filtro],
    queryFn: () => api.get('/industrial/qualidade/nao-conformidades', { params: filtro === 'todas' ? {} : { status: filtro } }).then(r => r.data),
  })
  const { data: lotes = [] } = useQuery<LoteProduto[]>({
    queryKey: ['ind-lotes-qualidade', 'todos'], enabled: !!nova, queryFn: () => api.get('/industrial/lotes').then(r => r.data),
  })
  const pag = usePaginacao(ncs, filtro)
  const recarregar = () => { qc.invalidateQueries({ queryKey: ['ind-ncs'] }); qc.invalidateQueries({ queryKey: ['ind-lotes-qualidade'] }) }

  const abrir = useMutation({
    mutationFn: () => {
      const lote = lotes.find(l => l.codigo.toLowerCase() === nova!.loteCodigo.trim().toLowerCase())
      return api.post('/industrial/qualidade/nao-conformidades', { origem: nova!.origem, loteId: lote?.id ?? null, descricao: nova!.descricao })
        .then(r => r.data as NaoConformidade)
    },
    onSuccess: n => { toast.success(`${n.numero} aberta${n.loteCodigo ? ` e lote ${n.loteCodigo} bloqueado` : ''}.`); recarregar(); setNova(null) },
  })
  const encerrar = useMutation({
    mutationFn: () => api.post(`/industrial/qualidade/nao-conformidades/${encerrando!.nc.id}/encerrar`, {
      disposicao: encerrando!.nc.loteId ? encerrando!.disposicao : null, causa: encerrando!.causa, acaoCorretiva: encerrando!.acao, versao: encerrando!.nc.versao,
    }),
    onSuccess: () => { toast.success('Não conformidade encerrada.'); recarregar(); setEncerrando(null) },
  })

  function tentarAbrir() {
    if (!nova!.descricao.trim()) return toast.error('Descreva a não conformidade.')
    if (nova!.loteCodigo.trim() && !lotes.some(l => l.codigo.toLowerCase() === nova!.loteCodigo.trim().toLowerCase())) {
      return toast.error('Lote não encontrado.')
    }
    abrir.mutate()
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex flex-wrap gap-2">
          <FilterTabs options={[{ value: 'ABERTA', label: 'Abertas' }, { value: 'ENCERRADA', label: 'Encerradas' }, { value: 'todas', label: 'Todas' }]}
            value={filtro} onChange={v => setFiltro(v as Filtro)} />
        </div>
        {podeCriar(MODULO_QUALIDADE) && <Button onClick={() => setNova({ origem: 'INTERNA', loteCodigo: '', descricao: '' })}>Nova não conformidade</Button>}
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>NC</TH><TH>Origem</TH><TH>Lote · OP</TH><TH>Descrição</TH><TH>Situação</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(n => (
                <TR key={n.id}>
                  <TD className="font-mono font-semibold">{n.numero}<div className="text-xs text-gray-500 font-sans">{dataBR(n.createdAt)}</div></TD>
                  <TD className="text-sm">{ORIGENS_NC[n.origem]}</TD>
                  <TD className="text-sm font-mono">{n.loteCodigo ?? '—'}{n.ordemNumero && <div className="text-xs text-gray-500">{n.ordemNumero}</div>}</TD>
                  <TD className="text-sm max-w-md">{n.descricao}
                    {n.status === 'ENCERRADA' && <div className="text-xs text-gray-500 mt-1">Causa: {n.causa} · Ação: {n.acaoCorretiva}{n.disposicao && n.disposicao !== 'SEM_LOTE' ? ` · ${DISPOSICOES_NC[n.disposicao].split(' (')[0]}` : ''}</div>}</TD>
                  <TD><StatusBadge tone={n.status === 'ABERTA' ? 'danger' : 'success'}>{n.status === 'ABERTA' ? 'Aberta' : 'Encerrada'}</StatusBadge></TD>
                  <TD align="right">{n.status === 'ABERTA' && podeEditar(MODULO_QUALIDADE) && (
                    <Button size="sm" variant="secondary" onClick={() => setEncerrando({ nc: n, disposicao: '', causa: '', acao: '' })}>Encerrar</Button>
                  )}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && ncs.length === 0 && <EmptyState>Nenhuma não conformidade {filtro === 'ABERTA' ? 'aberta' : ''}.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="não conformidades" />}
      </div>

      {nova && (
        <Modal title="Nova não conformidade" onClose={() => setNova(null)} maxWidth="lg"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setNova(null)}>Cancelar</Button>
            <Button fullWidth loading={abrir.isPending} onClick={tentarAbrir}>Abrir</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label="Origem *" htmlFor="nc-origem">
              <select id="nc-origem" value={nova.origem} onChange={e => setNova({ ...nova, origem: e.target.value as NaoConformidade['origem'] })} className={baseInputClass}>
                {Object.entries(ORIGENS_NC).filter(([v]) => v !== 'INSPECAO').map(([v, r]) => <option key={v} value={v}>{r}</option>)}
              </select>
            </Field>
            <Field label="Lote" htmlFor="nc-lote" hint="Com lote, ele fica bloqueado até o encerramento">
              <input id="nc-lote" value={nova.loteCodigo} placeholder="Ex.: OP000001-L01" onChange={e => setNova({ ...nova, loteCodigo: e.target.value })} className={baseInputClass} />
            </Field>
          </div>
          <Field label="Descrição *" htmlFor="nc-desc">
            <textarea id="nc-desc" rows={4} maxLength={2000} value={nova.descricao} onChange={e => setNova({ ...nova, descricao: e.target.value })} className={baseInputClass} />
          </Field>
        </Modal>
      )}
      {encerrando && (
        <Modal title={`Encerrar ${encerrando.nc.numero}`} onClose={() => setEncerrando(null)} maxWidth="lg"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setEncerrando(null)}>Voltar</Button>
            <Button fullWidth loading={encerrar.isPending} onClick={() => {
              if (encerrando.nc.loteId && !encerrando.disposicao) return toast.error('Escolha a disposição do lote.')
              if (!encerrando.causa.trim() || !encerrando.acao.trim()) return toast.error('Informe a causa e a ação corretiva.')
              if (encerrando.disposicao === 'SUCATEAR' && !window.confirm(`Sucatear o saldo do lote ${encerrando.nc.loteCodigo}? O estoque é baixado como descarte.`)) return
              encerrar.mutate()
            }}>Encerrar</Button>
          </>}>
          <p className="text-sm text-gray-700">{encerrando.nc.descricao}</p>
          {encerrando.nc.loteId && (
            <div className="grid gap-2">
              <div className="text-sm font-semibold text-gray-700">Disposição do lote {encerrando.nc.loteCodigo} *</div>
              {Object.entries(DISPOSICOES_NC).map(([v, r]) => (
                <label key={v} className={`flex items-center gap-2 rounded-lg border-2 p-2 cursor-pointer ${encerrando.disposicao === v ? 'border-primary-500 bg-primary-50' : 'border-gray-200'}`}>
                  <input type="radio" name="disposicao" checked={encerrando.disposicao === v} onChange={() => setEncerrando({ ...encerrando, disposicao: v })} /> {r}
                </label>
              ))}
            </div>
          )}
          <Field label="Causa *" htmlFor="nc-causa"><textarea id="nc-causa" rows={2} maxLength={2000} value={encerrando.causa} onChange={e => setEncerrando({ ...encerrando, causa: e.target.value })} className={baseInputClass} /></Field>
          <Field label="Ação corretiva *" htmlFor="nc-acao"><textarea id="nc-acao" rows={2} maxLength={2000} value={encerrando.acao} onChange={e => setEncerrando({ ...encerrando, acao: e.target.value })} className={baseInputClass} /></Field>
        </Modal>
      )}
    </div>
  )
}
