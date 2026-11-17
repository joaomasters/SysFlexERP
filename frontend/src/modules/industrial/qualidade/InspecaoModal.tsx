import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { Modal, Button, Field, baseInputClass, LoadingState } from '@/shared/components/ui'
import { dentroDaFaixa, faixaTexto, novaChave, type InspecaoLote, type LoteProduto, type PlanoInspecao } from '@/types/industrial'

/** Inspeção do lote em quarentena contra o plano ativo do produto. Aprovada libera; reprovada bloqueia e abre NC. */
export default function InspecaoModal({ lote, onClose }: { lote: LoteProduto; onClose: () => void }) {
  const qc = useQueryClient()
  const [chave] = useState(novaChave)
  const [medidas, setMedidas] = useState<Record<string, string>>({})
  const [atributos, setAtributos] = useState<Record<string, boolean | undefined>>({})
  const [amostra, setAmostra] = useState('')
  const [observacao, setObservacao] = useState('')
  const { data: planos } = useQuery<PlanoInspecao[]>({ queryKey: ['ind-planos'], queryFn: () => api.get('/industrial/qualidade/planos').then(r => r.data) })
  const plano = planos?.find(p => p.ativo && p.produtoId === lote.produtoId)

  const registrar = useMutation({
    mutationFn: () => api.post('/industrial/qualidade/inspecoes', {
      chave, loteId: lote.id, amostra: amostra ? Number(amostra) : null, observacao: observacao || null,
      valores: plano!.caracteristicas.map(c => c.tipo === 'MEDIDA'
        ? { codigo: c.codigo, valorNumero: medidas[c.codigo] ? Number(medidas[c.codigo]) : null }
        : { codigo: c.codigo, conforme: atributos[c.codigo] ?? null })
        .filter(v => ('valorNumero' in v ? v.valorNumero != null : v.conforme != null)),
    }).then(r => r.data as InspecaoLote),
    onSuccess: i => {
      if (i.resultado === 'APROVADO') toast.success(`Lote ${lote.codigo} aprovado e liberado.`)
      else toast.error(`Lote ${lote.codigo} reprovado e bloqueado. Aberta a ${i.naoConformidadeNumero}.`)
      for (const k of ['ind-lotes-qualidade', 'ind-ncs']) qc.invalidateQueries({ queryKey: [k] })
      onClose()
    },
  })

  return (
    <Modal title={`Inspeção do lote ${lote.codigo}`} onClose={onClose} maxWidth="lg"
      footer={<>
        <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
        <Button fullWidth loading={registrar.isPending} disabled={!plano} onClick={() => registrar.mutate()}>Registrar inspeção</Button>
      </>}>
      {!planos ? <LoadingState /> : !plano ? <p className="text-sm text-gray-600">O produto não tem plano de inspeção ativo.</p> : (
        <div className="space-y-3">
          <p className="text-sm text-gray-600">{lote.produtoNome} · plano <span className="font-mono">{plano.codigo}</span> rev. {plano.revisao}</p>
          {plano.caracteristicas.map(c => c.tipo === 'MEDIDA' ? (
            <Field key={c.codigo} label={`${c.nome}${c.unidade ? ` (${c.unidade})` : ''}${c.obrigatorio ? ' *' : ''}`} htmlFor={`insp-${c.codigo}`}
              hint={`Faixa: ${faixaTexto(c.minimo, c.maximo, c.unidade)}`}>
              <input id={`insp-${c.codigo}`} type="number" step="any" value={medidas[c.codigo] ?? ''}
                onChange={e => setMedidas({ ...medidas, [c.codigo]: e.target.value })}
                className={`${baseInputClass} ${medidas[c.codigo] && !dentroDaFaixa(Number(medidas[c.codigo]), c.minimo, c.maximo) ? 'border-danger-500 bg-danger-50' : ''}`} />
            </Field>
          ) : (
            <div key={c.codigo}>
              <div className="text-sm font-medium text-gray-700 mb-1">{c.nome}{c.obrigatorio ? ' *' : ''}</div>
              <div className="flex gap-2">
                {[true, false].map(v => (
                  <button key={String(v)} type="button" onClick={() => setAtributos({ ...atributos, [c.codigo]: v })}
                    className={`flex-1 rounded-lg border-2 py-2 font-semibold ${atributos[c.codigo] === v ? (v ? 'border-success-500 bg-success-50' : 'border-danger-500 bg-danger-50') : 'border-gray-200'}`}>
                    {v ? 'Conforme' : 'Não conforme'}
                  </button>
                ))}
              </div>
            </div>
          ))}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Field label="Amostra (peças)" htmlFor="insp-amostra"><input id="insp-amostra" type="number" min="1" value={amostra} onChange={e => setAmostra(e.target.value)} className={baseInputClass} /></Field>
            <div className="sm:col-span-2">
              <Field label="Observação" htmlFor="insp-obs"><input id="insp-obs" value={observacao} maxLength={1000} onChange={e => setObservacao(e.target.value)} className={baseInputClass} /></Field>
            </div>
          </div>
        </div>
      )}
    </Modal>
  )
}
