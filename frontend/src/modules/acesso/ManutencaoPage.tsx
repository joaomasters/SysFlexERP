import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { PageHeader, Card, Button, ConfirmDialog } from '@/shared/components/ui'

interface StatusCache {
  versao: number
  limpoEm: string | null
  limpoPor: string | null
}

/** Só o SUPER_ADMIN vê (Sidebar) e só ele consegue limpar (LimpezaCacheService). */
export default function ManutencaoPage() {
  const qc = useQueryClient()
  const [confirmando, setConfirmando] = useState(false)

  const { data: status } = useQuery<StatusCache>({
    queryKey: ['manutencao-cache'],
    queryFn: () => api.get('/manutencao/cache').then(r => r.data),
  })

  const limpar = useMutation({
    mutationFn: () => api.post<StatusCache>('/manutencao/cache/limpar').then(r => r.data),
    onSuccess: () => {
      setConfirmando(false)
      toast.success('Cache limpo. Os computadores da empresa vão recarregar a tela.')
      // A próxima resposta já traz a versão nova: este navegador também recarrega
      qc.invalidateQueries({ queryKey: ['manutencao-cache'] })
    },
  })

  const ultimaLimpeza = status?.limpoEm
    ? `${new Date(status.limpoEm).toLocaleString('pt-BR')}${status.limpoPor ? ` por ${status.limpoPor}` : ''}`
    : 'Nunca'

  return (
    <div className="p-4 sm:p-6">
      <PageHeader title="Manutenção" subtitle="Ferramentas do administrador da empresa" />

      <Card padding="md" className="max-w-2xl space-y-4">
        <div className="flex items-start gap-3">
          <RefreshCw size={20} className="text-primary-600 shrink-0 mt-0.5" />
          <div className="space-y-2">
            <h2 className="text-sm font-semibold text-gray-800">Limpar cache</h2>
            <p className="text-sm text-gray-600">
              Use quando alguma tela estiver mostrando dados antigos ou uma atualização do sistema não
              apareceu. Todos os computadores desta empresa descartam os dados guardados e recarregam a
              tela; no servidor, são descartados as sessões em memória, os bloqueios de login por senha
              errada e o login da maquininha Veloce.
            </p>
            <p className="text-sm text-gray-600">
              Ninguém é deslogado. No PDV a tela não recarrega no meio da venda: o operador recebe um aviso
              para apertar F5 ao terminar.
            </p>
            <p className="text-xs text-gray-500">Última limpeza: {ultimaLimpeza}</p>
          </div>
        </div>
        <div className="flex justify-end">
          <Button variant="warning" onClick={() => setConfirmando(true)}>
            <RefreshCw size={15} /> Limpar cache
          </Button>
        </div>
      </Card>

      {confirmando && (
        <ConfirmDialog
          title="Limpar o cache da empresa?"
          message="Todos os computadores desta empresa vão recarregar a tela. Ninguém será deslogado."
          confirmLabel="Limpar cache"
          confirmVariant="warning"
          loading={limpar.isPending}
          onConfirm={() => limpar.mutate()}
          onCancel={() => setConfirmando(false)}
        />
      )}
    </div>
  )
}
