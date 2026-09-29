import { AlertTriangle } from 'lucide-react'
import Modal from './Modal'
import Button from './Button'
import type { ButtonVariant } from './Button'

interface ConfirmDialogProps {
  /** Título curto do modal, ex: "Inativar produto?" */
  title: string
  /** Texto explicando a consequência da ação. Pode citar o nome do item. */
  message: string
  /** Rótulo do botão de confirmação, ex: "Inativar", "Excluir". Padrão: "Confirmar". */
  confirmLabel?: string
  /** Variante do botão de confirmação — 'danger' para exclusão/inativação, 'warning' para algo reversível mas que pede atenção. Padrão: 'danger'. */
  confirmVariant?: Extract<ButtonVariant, 'danger' | 'warning'>
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * Modal de confirmação padrão para ações destrutivas ou irreversíveis
 * (excluir, inativar, cancelar). Use antes de disparar a mutation, nunca
 * direto no onClick do botão de ação.
 *
 * Padrão de uso — guarda o item pendente de confirmação em um estado local
 * e só chama a mutation quando o usuário confirma:
 *
 * const [confirmando, setConfirmando] = useState<Produto | null>(null)
 * ...
 * <Button onClick={() => setConfirmando(produto)}><Trash2 /></Button>
 * ...
 * {confirmando && (
 *   <ConfirmDialog
 *     title="Excluir produto?"
 *     message={`"${confirmando.nome}" será removido. Essa ação não pode ser desfeita.`}
 *     confirmLabel="Excluir"
 *     onConfirm={() => { deletar.mutate(confirmando.id); setConfirmando(null) }}
 *     onCancel={() => setConfirmando(null)}
 *   />
 * )}
 */
export default function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirmar',
  confirmVariant = 'danger',
  loading,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Modal
      title={title}
      onClose={onCancel}
      maxWidth="sm"
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={onCancel}>Cancelar</Button>
          <Button variant={confirmVariant} fullWidth loading={loading} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="flex gap-3">
        <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0
          ${confirmVariant === 'danger' ? 'bg-danger-50' : 'bg-warning-50'}`}>
          <AlertTriangle size={18} className={confirmVariant === 'danger' ? 'text-danger-600' : 'text-warning-600'} />
        </div>
        <p className="text-sm text-gray-600 pt-1.5">{message}</p>
      </div>
    </Modal>
  )
}