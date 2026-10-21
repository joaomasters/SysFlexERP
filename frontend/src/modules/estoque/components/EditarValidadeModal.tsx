import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { formatQuantidade } from '@/shared/utils/mask'
import { Modal, Button, Field, baseInputClass } from '@/shared/components/ui'
import type { LoteValidade } from '@/types/validade'
import {
  formatarDataValidade, limiteValidadeISO, validadeAcimaDoLimite, MAX_ANOS_VALIDADE,
} from '../validadeUtils'

interface Props {
  lote: LoteValidade
  onClose: () => void
}

/**
 * Correção da validade de um lote já cadastrado (ex: data digitada errada no
 * recebimento). A regra é a mesma da entrada: no máximo 3 anos a partir do dia
 * em que o lote entrou — o backend confere de novo, esta checagem só evita a ida e volta.
 */
export default function EditarValidadeModal({ lote, onClose }: Props) {
  const qc = useQueryClient()
  const [data, setData] = useState(lote.dataValidade)
  const [tentouSalvar, setTentouSalvar] = useState(false)

  const limite = limiteValidadeISO(lote.dataEntrada)
  const atualForaDoLimite = validadeAcimaDoLimite(lote.dataValidade, limite)

  const erro = !data
    ? 'Informe a nova data de validade.'
    : validadeAcimaDoLimite(data, limite)
      ? `A validade não pode passar de ${formatarDataValidade(limite)} (${MAX_ANOS_VALIDADE} anos da entrada do lote).`
      : ''

  const salvar = useMutation({
    mutationFn: () => api.put(`/estoque/validade/lotes/${lote.loteId}`, { dataValidade: data }),
    onSuccess: () => {
      toast.success('Validade atualizada.')
      qc.invalidateQueries({ queryKey: ['validade-lotes'] })
      qc.invalidateQueries({ queryKey: ['validade-alertas'] })
      onClose()
    },
  })

  function confirmar() {
    setTentouSalvar(true)
    if (erro) return
    if (data === lote.dataValidade) {   // nada mudou: não precisa chamar a API
      onClose()
      return
    }
    salvar.mutate()
  }

  return (
    <Modal
      title="Editar Validade"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
          <Button variant="primary" fullWidth loading={salvar.isPending} onClick={confirmar}>Salvar</Button>
        </>
      }
    >
      <div className="bg-gray-50 border border-gray-100 rounded-lg p-3 text-sm space-y-1">
        <p className="font-medium text-gray-900">{lote.produtoNome}</p>
        <p className="text-xs text-gray-500">
          Saldo do lote: <span className="tabular-nums">{formatQuantidade(lote.quantidadeAtual, lote.unidadeMedida)} {lote.unidadeMedida}</span>
          {lote.documentoRef && <> · Origem: {lote.documentoRef}</>}
        </p>
        <p className="text-xs text-gray-500">
          Validade atual:{' '}
          <span className={atualForaDoLimite ? 'text-danger-600 font-medium' : ''}>
            {formatarDataValidade(lote.dataValidade)}
          </span>
          {atualForaDoLimite && ' (fora do limite — corrija)'}
        </p>
      </div>

      <Field
        label="Nova validade *"
        error={tentouSalvar && erro ? erro : undefined}
        hint={`Máximo: ${formatarDataValidade(limite)} (${MAX_ANOS_VALIDADE} anos a partir da entrada do lote).`}
      >
        <input
          type="date"
          autoFocus
          value={data}
          max={limite}
          onChange={e => setData(e.target.value)}
          className={`${baseInputClass} ${(tentouSalvar || validadeAcimaDoLimite(data, limite)) && erro ? '!border-danger-500' : ''}`}
        />
      </Field>
    </Modal>
  )
}