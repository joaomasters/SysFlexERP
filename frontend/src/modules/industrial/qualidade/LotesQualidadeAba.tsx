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
import { SITUACAO_LOTE, type LoteProduto, type SituacaoLote } from '@/types/industrial'
import { dataBR, num } from '../comum'
import EtiquetaModal from './EtiquetaModal'
import InspecaoModal from './InspecaoModal'

export const MODULO_QUALIDADE = 'IND_QUALIDADE'

type Filtro = 'todos' | SituacaoLote
const FILTROS: { value: Filtro; label: string }[] = [
  { value: 'QUARENTENA', label: 'Em quarentena' }, { value: 'LIBERADO', label: 'Liberados' },
  { value: 'BLOQUEADO', label: 'Bloqueados' }, { value: 'todos', label: 'Todos' },
]

/** Lotes de produto: situação de qualidade, saldo, divisão, união e etiqueta com QR. */
export default function LotesQualidadeAba({ onRastrear }: { onRastrear: (codigo: string) => void }) {
  const qc = useQueryClient()
  const { podeCriar, podeEditar } = usePermissao()
  const [filtro, setFiltro] = useState<Filtro>('QUARENTENA')
  const [busca, setBusca] = useState('')
  const [etiqueta, setEtiqueta] = useState<number | null>(null)
  const [inspecionando, setInspecionando] = useState<LoteProduto | null>(null)
  const [situacao, setSituacao] = useState<{ lote: LoteProduto; para: SituacaoLote; motivo: string } | null>(null)
  const [dividindo, setDividindo] = useState<{ lote: LoteProduto; partes: string } | null>(null)
  const [selecionados, setSelecionados] = useState<Record<number, string>>({})

  const { data: lotes = [], isLoading } = useQuery<LoteProduto[]>({
    queryKey: ['ind-lotes-qualidade', filtro],
    queryFn: () => api.get('/industrial/lotes', { params: filtro === 'todos' ? {} : { situacao: filtro } }).then(r => r.data),
  })
  const t = busca.trim().toLowerCase()
  const visiveis = lotes.filter(l => !t || `${l.codigo} ${l.produtoNome} ${l.ordemNumero ?? ''}`.toLowerCase().includes(t))
  const pag = usePaginacao(visiveis, `${filtro}|${busca}`)
  const recarregar = () => qc.invalidateQueries({ queryKey: ['ind-lotes-qualidade'] })

  const mudar = useMutation({
    mutationFn: () => api.post(`/industrial/lotes/${situacao!.lote.id}/situacao`, { situacao: situacao!.para, versao: situacao!.lote.versao, motivo: situacao!.motivo || null }),
    onSuccess: () => { toast.success('Situação do lote alterada.'); recarregar(); setSituacao(null) },
  })
  const dividir = useMutation({
    mutationFn: () => api.post(`/industrial/lotes/${dividindo!.lote.id}/dividir`, {
      quantidades: dividindo!.partes.split(/[;\s]+/).filter(Boolean).map(x => Number(x.replace(',', '.'))),
    }).then(r => r.data as LoteProduto[]),
    onSuccess: f => { toast.success(`Criados ${f.map(x => x.codigo).join(', ')}.`); recarregar(); setDividindo(null) },
  })
  const unir = useMutation({
    mutationFn: () => api.post('/industrial/lotes/unir', {
      partes: Object.entries(selecionados).map(([id, q]) => ({ loteId: Number(id), quantidade: Number(q.replace(',', '.')) })),
    }).then(r => r.data as LoteProduto),
    onSuccess: l => { toast.success(`Lote ${l.codigo} criado pela união.`); recarregar(); setSelecionados({}) },
  })

  const nSel = Object.keys(selecionados).length

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex flex-wrap gap-2"><FilterTabs options={FILTROS} value={filtro} onChange={setFiltro} /></div>
        <div className="flex gap-2 w-full sm:w-auto">
          <input type="search" value={busca} onChange={e => setBusca(e.target.value)} placeholder="Lote, produto ou OP..."
            aria-label="Buscar lotes" className={`${baseInputClass} sm:w-56`} />
          {podeCriar(MODULO_QUALIDADE) && nSel >= 2 && <Button loading={unir.isPending} onClick={() => unir.mutate()}>Unir {nSel} lotes</Button>}
        </div>
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH /><TH>Lote</TH><TH>Produto</TH><TH align="right">Saldo / disponível</TH><TH>Situação</TH><TH>Criado</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(l => (
                <TR key={l.id}>
                  <TD className="w-8">
                    {podeCriar(MODULO_QUALIDADE) && l.situacao !== 'ESTORNADO' && l.disponivel > 0 && (
                      <input type="checkbox" aria-label={`Selecionar ${l.codigo} para união`} checked={l.id in selecionados}
                        onChange={e => setSelecionados(s => {
                          const n = { ...s }
                          if (e.target.checked) n[l.id] = String(l.disponivel)
                          else delete n[l.id]
                          return n
                        })} />
                    )}
                  </TD>
                  <TD><button type="button" className="font-mono font-semibold text-primary-700 hover:underline" onClick={() => onRastrear(l.codigo)}>{l.codigo}</button>
                    {l.ordemNumero && <div className="text-xs text-gray-500">{l.ordemNumero}{l.origem !== 'PRODUCAO' ? ` · ${l.origem === 'DIVISAO' ? 'divisão' : 'união'}` : ''}</div>}</TD>
                  <TD>{l.produtoNome}</TD>
                  <TD align="right" className="tabular-nums">{num(l.saldo)} / {num(l.disponivel)} {l.unidade}{l.reservado > 0 && <div className="text-xs text-gray-500">reservado {num(l.reservado)}</div>}</TD>
                  <TD><StatusBadge tone={SITUACAO_LOTE[l.situacao].tom}>{SITUACAO_LOTE[l.situacao].rotulo}</StatusBadge>
                    {l.motivoSituacao && <div className="text-xs text-gray-500 max-w-[220px] truncate" title={l.motivoSituacao}>{l.motivoSituacao}</div>}</TD>
                  <TD className="text-sm tabular-nums">{dataBR(l.createdAt)}</TD>
                  <TD align="right" className="whitespace-nowrap space-x-1">
                    {l.situacao === 'QUARENTENA' && l.temPlano && podeCriar(MODULO_QUALIDADE) && <Button size="sm" onClick={() => setInspecionando(l)}>Inspecionar</Button>}
                    {l.situacao === 'QUARENTENA' && !l.temPlano && podeEditar(MODULO_QUALIDADE) && <Button size="sm" variant="success" onClick={() => setSituacao({ lote: l, para: 'LIBERADO', motivo: '' })}>Liberar</Button>}
                    {['QUARENTENA', 'LIBERADO'].includes(l.situacao) && podeEditar(MODULO_QUALIDADE) && <Button size="sm" variant="outline-danger" onClick={() => setSituacao({ lote: l, para: 'BLOQUEADO', motivo: '' })}>Bloquear</Button>}
                    {['BLOQUEADO', 'LIBERADO'].includes(l.situacao) && podeEditar(MODULO_QUALIDADE) && <Button size="sm" variant="ghost" onClick={() => setSituacao({ lote: l, para: 'QUARENTENA', motivo: '' })}>Quarentena</Button>}
                    {l.situacao !== 'ESTORNADO' && l.disponivel > 0 && podeCriar(MODULO_QUALIDADE) && <Button size="sm" variant="ghost" onClick={() => setDividindo({ lote: l, partes: '' })}>Dividir</Button>}
                    <Button size="sm" variant="secondary" onClick={() => setEtiqueta(l.id)}>Etiqueta</Button>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && visiveis.length === 0 && <EmptyState>Nenhum lote {filtro === 'todos' ? '' : 'nesta situação'}.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="lotes" />}
      </div>
      {nSel === 1 && <p className="text-xs text-gray-500 mt-2">Selecione ao menos dois lotes do mesmo produto e na mesma situação para unir.</p>}

      {etiqueta != null && <EtiquetaModal loteId={etiqueta} onClose={() => setEtiqueta(null)} />}
      {inspecionando && <InspecaoModal lote={inspecionando} onClose={() => setInspecionando(null)} />}
      {situacao && (
        <Modal title={`${situacao.para === 'LIBERADO' ? 'Liberar' : situacao.para === 'BLOQUEADO' ? 'Bloquear' : 'Voltar para quarentena'} o lote ${situacao.lote.codigo}`}
          onClose={() => setSituacao(null)}
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setSituacao(null)}>Voltar</Button>
            <Button fullWidth variant={situacao.para === 'BLOQUEADO' ? 'danger' : 'primary'} loading={mudar.isPending}
              onClick={() => (situacao.para !== 'LIBERADO' && !situacao.motivo.trim() ? toast.error('Informe o motivo.') : mudar.mutate())}>Confirmar</Button>
          </>}>
          {situacao.para === 'LIBERADO' ? <p className="text-sm text-gray-700">O produto não tem plano de inspeção; o lote pode ser liberado direto.</p> : (
            <Field label="Motivo *" htmlFor="lote-situacao-motivo">
              <textarea id="lote-situacao-motivo" rows={3} maxLength={500} value={situacao.motivo} autoFocus
                onChange={e => setSituacao({ ...situacao, motivo: e.target.value })} className={baseInputClass} />
            </Field>
          )}
        </Modal>
      )}
      {dividindo && (
        <Modal title={`Dividir o lote ${dividindo.lote.codigo}`} onClose={() => setDividindo(null)}
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setDividindo(null)}>Voltar</Button>
            <Button fullWidth loading={dividir.isPending} onClick={() => (!dividindo.partes.trim() ? toast.error('Informe as quantidades.') : dividir.mutate())}>Dividir</Button>
          </>}>
          <Field label="Quantidades dos novos lotes *" htmlFor="lote-partes" hint={`Separe por espaço ou ponto e vírgula. Disponível: ${num(dividindo.lote.disponivel)} ${dividindo.lote.unidade}`}>
            <input id="lote-partes" value={dividindo.partes} placeholder="Ex.: 40 30" autoFocus
              onChange={e => setDividindo({ ...dividindo, partes: e.target.value })} className={baseInputClass} />
          </Field>
          <p className="text-xs text-gray-500">Os novos lotes herdam a situação e ficam ligados a este na rastreabilidade.</p>
        </Modal>
      )}
    </div>
  )
}
