import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { Modal, Button, Field, baseInputClass } from '@/shared/components/ui'
import { TIPOS_PERDA, novaChave, type TipoPerda } from '@/types/industrial'

/** Registro de perda metálica (kg) da OP: topo de tarugo, ponta de perfil, retalho, sucata. */
export default function PerdaModal({ ordemId, apontamentoId, onClose }: { ordemId: number; apontamentoId?: number; onClose: () => void }) {
  const qc = useQueryClient()
  const [chave, setChave] = useState(novaChave)
  const [tipo, setTipo] = useState<TipoPerda | ''>('')
  const [kg, setKg] = useState('')
  const [observacao, setObservacao] = useState('')
  const registrar = useMutation({
    mutationFn: () => api.post('/industrial/perdas', { chave, ordemId, apontamentoId: apontamentoId ?? null, tipo, quantidadeKg: Number(kg), observacao: observacao || null }),
    onSuccess: () => {
      toast.success(`Perda de ${Number(kg).toLocaleString('pt-BR')} kg registrada.`)
      qc.invalidateQueries({ queryKey: ['ind-metalurgia', ordemId] })
      setChave(novaChave())
      setKg('')
      setObservacao('')
    },
  })
  return (
    <Modal title="Perda metálica" onClose={onClose} maxWidth="lg"
      footer={<>
        <Button variant="secondary" fullWidth onClick={onClose}>Fechar</Button>
        <Button fullWidth loading={registrar.isPending}
          onClick={() => (!tipo ? toast.error('Escolha o tipo da perda.') : !(Number(kg) > 0) ? toast.error('Informe o peso.') : registrar.mutate())}>
          Registrar perda
        </Button>
      </>}>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {Object.entries(TIPOS_PERDA).map(([v, r]) => (
          <button key={v} type="button" onClick={() => setTipo(v as TipoPerda)}
            className={`min-h-[52px] rounded-xl border-2 p-3 text-left font-semibold ${tipo === v ? 'border-warning-500 bg-warning-50' : 'border-gray-200'}`}>{r}</button>
        ))}
      </div>
      <Field label="Peso (kg) *" htmlFor="perda-kg">
        <input id="perda-kg" type="number" inputMode="decimal" step="any" min="0" value={kg} onChange={e => setKg(e.target.value)} className={`${baseInputClass} text-xl py-3`} />
      </Field>
      <Field label="Observação" htmlFor="perda-obs">
        <input id="perda-obs" value={observacao} maxLength={500} onChange={e => setObservacao(e.target.value)} className={baseInputClass} />
      </Field>
      <p className="text-xs text-gray-500">A perda entra no rendimento metálico da OP. Ela não movimenta o estoque.</p>
    </Modal>
  )
}
