import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { Button, Card, Field, Modal, StatusBadge, baseInputClass, Table, THead, TH, TBody, TR, TD } from '@/shared/components/ui'

/*
 Números da série que nunca viraram nota (recusados, não transmitidos ou pulados) e o pedido de
 inutilização à SEFAZ. Resultado desconhecido não entra: precisa ser consultado antes.
*/

interface Lacuna { modelo: number; serie: number; numeroInicial: number; numeroFinal: number; motivo: string; inutilizavel: boolean }
interface Inutilizacao {
  id: number; modelo: number; serie: number; numeroInicial: number; numeroFinal: number; status: string
  protocolo: string | null; mensagem: string | null; createdAt: string
}

const faixa = (a: number, b: number) => (a === b ? `${a}` : `${a} a ${b}`)
const TOM: Record<string, 'success' | 'danger' | 'warning'> = { HOMOLOGADA: 'success', REJEITADA: 'danger', DESCONHECIDO: 'warning' }
const ROTULO: Record<string, string> = { HOMOLOGADA: 'Homologada', REJEITADA: 'Rejeitada', DESCONHECIDO: 'Sem resposta' }

export default function NumeracaoInutilizar() {
  const qc = useQueryClient()
  const { podeEditar } = usePermissao()
  const [pedido, setPedido] = useState<Lacuna | null>(null)
  const [justificativa, setJustificativa] = useState('')

  const { data: lacunas = [] } = useQuery<Lacuna[]>({
    queryKey: ['fiscal-lacunas'],
    queryFn: () => api.get('/fiscal/inutilizacoes/lacunas', { params: { modelo: 65 } }).then(r => r.data),
  })
  const { data: historico = [] } = useQuery<Inutilizacao[]>({
    queryKey: ['fiscal-inutilizacoes'],
    queryFn: () => api.get('/fiscal/inutilizacoes').then(r => r.data),
  })

  const inutilizar = useMutation({
    mutationFn: (l: Lacuna) => api.post<Inutilizacao>('/fiscal/inutilizacoes', {
      modelo: l.modelo, numeroInicial: l.numeroInicial, numeroFinal: l.numeroFinal, justificativa,
    }).then(r => r.data),
    onSuccess: r => {
      if (r.status === 'HOMOLOGADA') toast.success(`Números ${faixa(r.numeroInicial, r.numeroFinal)} inutilizados.`)
      else toast.error(r.mensagem ?? 'A SEFAZ não confirmou a inutilização.')
      setPedido(null)
      setJustificativa('')
      qc.invalidateQueries({ queryKey: ['fiscal-lacunas'] })
      qc.invalidateQueries({ queryKey: ['fiscal-inutilizacoes'] })
      qc.invalidateQueries({ queryKey: ['fiscal-documentos'] })
    },
  })

  return (
    <Card padding="md" className="space-y-3">
      <div>
        <h2 className="text-sm font-semibold text-gray-800">Numeração da NFC-e a inutilizar</h2>
        <p className="text-xs text-gray-500">
          Números da série que nunca viraram nota autorizada. Eles precisam ser inutilizados na SEFAZ para a sequência fechar.
        </p>
      </div>
      {lacunas.length === 0 ? (
        <p className="text-sm text-gray-600">Nenhum número pendente: a sequência está fechada.</p>
      ) : (
        <Table>
          <THead><TH>Série</TH><TH>Números</TH><TH>Motivo</TH><TH /></THead>
          <TBody>
            {lacunas.map(l => (
              <TR key={`${l.serie}-${l.numeroInicial}`}>
                <TD>{l.serie}</TD>
                <TD className="tabular-nums">{faixa(l.numeroInicial, l.numeroFinal)}</TD>
                <TD>{l.motivo}{!l.inutilizavel && <span className="ml-2 text-xs text-warning-700">consulte a situação antes</span>}</TD>
                <TD align="right">
                  {l.inutilizavel && podeEditar('FISCAL') && (
                    <Button size="sm" variant="secondary" onClick={() => setPedido(l)}>Inutilizar</Button>
                  )}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      )}
      {historico.length > 0 && (
        <details className="text-sm">
          <summary className="cursor-pointer text-gray-600">Pedidos de inutilização ({historico.length})</summary>
          <ul className="mt-2 divide-y divide-gray-100 rounded-lg border border-gray-100">
            {historico.map(h => (
              <li key={h.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-1.5">
                <span className="tabular-nums">Série {h.serie}, nº {faixa(h.numeroInicial, h.numeroFinal)}</span>
                <span className="flex items-center gap-2 text-xs text-gray-500">
                  {h.protocolo && <>protocolo {h.protocolo}</>}
                  <StatusBadge tone={TOM[h.status] ?? 'warning'}>{ROTULO[h.status] ?? h.status}</StatusBadge>
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}
      {pedido && (
        <Modal title={`Inutilizar ${faixa(pedido.numeroInicial, pedido.numeroFinal)}`} onClose={() => setPedido(null)}
               footer={
                 <div className="flex justify-end gap-2">
                   <Button variant="secondary" onClick={() => setPedido(null)}>Cancelar</Button>
                   <Button variant="danger" disabled={justificativa.trim().length < 15} loading={inutilizar.isPending}
                           onClick={() => inutilizar.mutate(pedido)}>Pedir inutilização</Button>
                 </div>
               }>
          <Field label="Justificativa" htmlFor="inutilizar-justificativa" hint="De 15 a 255 caracteres; vai para a SEFAZ.">
            <textarea id="inutilizar-justificativa" value={justificativa} maxLength={255} rows={3}
                      onChange={e => setJustificativa(e.target.value)} className={baseInputClass} />
          </Field>
        </Modal>
      )}
    </Card>
  )
}
