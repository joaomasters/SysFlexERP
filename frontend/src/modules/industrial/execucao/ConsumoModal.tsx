import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { Modal, Button, Field, baseInputClass, LoadingState } from '@/shared/components/ui'
import { novaChave, type ExecucaoOP } from '@/types/industrial'
import { num } from '../comum'

/**
 * Registro de consumo de material da OP (baixa no estoque). Mostra previsto × consumido e sugere o
 * proporcional ao que já foi produzido. A chave fica fixa enquanto o modal está aberto: se a rede cair e o
 * operador tocar de novo, o servidor reconhece e não baixa duas vezes.
 */
export default function ConsumoModal({ ordemId, apontamentoId, onClose }: { ordemId: number; apontamentoId?: number; onClose: () => void }) {
  const qc = useQueryClient()
  const { data: exec } = useQuery<ExecucaoOP>({
    queryKey: ['ind-execucao', ordemId], queryFn: () => api.get(`/industrial/ordens/${ordemId}/execucao`).then(r => r.data),
  })
  const [produtoId, setProdutoId] = useState<number>(0)
  const [quantidade, setQuantidade] = useState('')
  const [lote, setLote] = useState('')
  const [chave, setChave] = useState(novaChave)

  const material = exec?.materiais.find(m => m.produtoId === produtoId)

  const registrar = useMutation({
    mutationFn: () => api.post(`/industrial/ordens/${ordemId}/consumos`, {
      chave, produtoId, quantidade: Number(quantidade), loteMaterial: lote || null, apontamentoId: apontamentoId ?? null,
    }),
    onSuccess: () => {
      toast.success(`Consumo de ${num(Number(quantidade), 4)} ${material?.unidade ?? ''} registrado.`)
      qc.invalidateQueries({ queryKey: ['ind-execucao', ordemId] })
      setChave(novaChave())
      setQuantidade('')
      setLote('')
    },
  })

  function escolher(id: number) {
    setProdutoId(id)
    const m = exec?.materiais.find(x => x.produtoId === id)
    setQuantidade(m && m.sugerido > 0 ? String(m.sugerido) : '')
  }

  return (
    <Modal title={`Consumo de material · ${exec?.numero ?? ''}`} onClose={onClose} maxWidth="xl"
      footer={<>
        <Button variant="secondary" fullWidth onClick={onClose}>Fechar</Button>
        <Button fullWidth loading={registrar.isPending}
          onClick={() => {
            if (!produtoId) return toast.error('Escolha o material.')
            if (!(Number(quantidade) > 0)) return toast.error('Informe a quantidade.')
            registrar.mutate()
          }}>
          Registrar consumo
        </Button>
      </>}>
      {!exec ? <LoadingState /> : (
        <div className="space-y-4">
          <div className="grid gap-2">
            {exec.materiais.map(m => {
              const acima = m.consumido > m.previsto
              return (
                <button key={m.produtoId} type="button" onClick={() => escolher(m.produtoId)}
                  className={`text-left rounded-xl border-2 p-3 transition-colors ${produtoId === m.produtoId ? 'border-primary-500 bg-primary-50' : 'border-gray-200 bg-white'}`}>
                  <div className="font-semibold text-gray-900">{m.nome}</div>
                  <div className="text-sm text-gray-600 tabular-nums">
                    Previsto {num(m.previsto, 4)} · consumido <span className={acima ? 'text-warning-700 font-semibold' : ''}>{num(m.consumido, 4)}</span>{' '}
                    · em estoque {num(m.saldoEstoque, 4)} {m.unidade}
                  </div>
                </button>
              )
            })}
          </div>
          {material && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Field label={`Quantidade (${material.unidade}) *`} htmlFor="consumo-qtd"
                hint={material.sugerido > 0 ? `Sugestão: ${num(material.sugerido, 4)}, proporcional ao produzido` : undefined}>
                <input id="consumo-qtd" type="number" inputMode="decimal" step="any" min="0" value={quantidade}
                  onChange={e => setQuantidade(e.target.value)} className={`${baseInputClass} text-lg py-3`} />
              </Field>
              <Field label="Lote / corrida do material" htmlFor="consumo-lote" hint="Rastreabilidade: o lote do fornecedor">
                <input id="consumo-lote" value={lote} maxLength={40} onChange={e => setLote(e.target.value)} className={`${baseInputClass} text-lg py-3`} />
              </Field>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}
