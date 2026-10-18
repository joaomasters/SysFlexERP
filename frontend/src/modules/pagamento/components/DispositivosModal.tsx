import { useQuery, useMutation } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { Modal, Button, StatusBadge, EmptyState, LoadingState } from '@/shared/components/ui'
import type { DispositivoRemoto, ProvedorConfig } from '@/types/pagamento'

interface Props {
  provedor: ProvedorConfig
  onUsar: (identificador: string) => void
  onClose: () => void
}

/**
 * Maquininhas vinculadas à conta do Mercado Pago. Para receber cobranças do
 * sistema a Point precisa estar em modo PDV (não "STANDALONE").
 */
export default function DispositivosModal({ provedor, onUsar, onClose }: Props) {
  const { podeEditar, podeCriar } = usePermissao()

  const { data: dispositivos = [], isLoading, refetch } = useQuery<DispositivoRemoto[]>({
    queryKey: ['maquininhas-dispositivos', provedor.provedor],
    queryFn: () => api.get(`/maquininhas/provedores/${provedor.provedor}/dispositivos`).then(r => r.data),
  })

  const ativarPdv = useMutation({
    mutationFn: (id: string) =>
      api.post(`/maquininhas/provedores/${provedor.provedor}/dispositivos/${encodeURIComponent(id)}/modo-pdv`),
    onSuccess: () => {
      toast.success('Modo PDV ativado. Reinicie a maquininha se ela não mudar sozinha.')
      refetch()
    },
  })

  return (
    <Modal title={`Dispositivos — ${provedor.rotulo}`} maxWidth="xl" onClose={onClose}>
      {isLoading ? (
        <LoadingState />
      ) : dispositivos.length === 0 ? (
        <EmptyState>Nenhuma maquininha vinculada a esta conta.</EmptyState>
      ) : (
        <div className="space-y-2">
          {dispositivos.map(d => {
            const emPdv = d.modoOperacao === 'PDV'
            return (
              <div key={d.id} className="flex items-center gap-3 border rounded-lg px-3 py-2">
                <div className="flex-1 min-w-0">
                  <p className="font-mono text-xs text-gray-900 truncate">{d.id}</p>
                  <p className="text-xs text-gray-500">{d.descricao}</p>
                </div>
                <StatusBadge tone={emPdv ? 'success' : 'warning'}>{emPdv ? 'Modo PDV' : d.modoOperacao || '—'}</StatusBadge>
                {!emPdv && podeEditar('MAQUININHAS') && (
                  <Button
                    variant="warning" size="sm"
                    loading={ativarPdv.isPending && ativarPdv.variables === d.id}
                    onClick={() => ativarPdv.mutate(d.id)}
                  >
                    Ativar modo PDV
                  </Button>
                )}
                {podeCriar('MAQUININHAS') && (
                  <Button variant="primary" size="sm" onClick={() => onUsar(d.id)}>Cadastrar</Button>
                )}
              </div>
            )
          })}
        </div>
      )}
    </Modal>
  )
}
