import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Lock, Power, PowerOff, Building2 } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'
import toast from 'react-hot-toast'
import type { Tenant } from '@/types/tenant'
import {
  PageHeader, Card, Button, Field, baseInputClass, StatusBadge, Modal,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState,
} from '@/shared/components/ui'

export default function TenantsPage() {
  const qc = useQueryClient()
  const { isSuperAdmin } = usePermissao()
  const [showNovo, setShowNovo] = useState(false)
  const [novoNome, setNovoNome] = useState('')

  const { data: tenants = [], isLoading } = useQuery<Tenant[]>({
    queryKey: ['tenants'],
    queryFn: () => api.get('/admin/tenants').then(r => r.data),
    enabled: isSuperAdmin,
  })

  const provisionar = useMutation({
    mutationFn: () => api.post<Tenant>('/admin/tenants', { nome: novoNome.trim() }),
    onSuccess: ({ data }) => {
      toast.success(`Empresa criada! Código de acesso: ${data.codigo}`)
      qc.invalidateQueries({ queryKey: ['tenants'] })
      setNovoNome('')
      setShowNovo(false)
    },
  })

  const ativar = useMutation({
    mutationFn: (id: number) => api.patch(`/admin/tenants/${id}/ativar`),
    onSuccess: () => {
      toast.success('Empresa ativada — já pode logar com o código dela.')
      qc.invalidateQueries({ queryKey: ['tenants'] })
    },
  })

  const desativar = useMutation({
    mutationFn: (id: number) => api.patch(`/admin/tenants/${id}/desativar`),
    onSuccess: () => {
      toast.success('Empresa desativada.')
      qc.invalidateQueries({ queryKey: ['tenants'] })
    },
  })

  if (!isSuperAdmin) {
    return (
      <div className="p-6">
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-5 max-w-lg">
          <p className="flex items-center gap-2 font-medium text-warning-700">
            <Lock size={16} /> Acesso restrito
          </p>
          <p className="text-sm text-warning-600 mt-1">
            Apenas o SUPER_ADMIN pode gerenciar as empresas que usam o sistema.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="p-6">
      <PageHeader
        title="Empresas"
        subtitle="Cada empresa roda isolada, com seus próprios produtos, vendas e usuários"
        icon={<Building2 size={22} className="text-gray-400" />}
        actions={
          <Button variant="primary" onClick={() => setShowNovo(true)}>
            <Plus size={16} /> Nova Empresa
          </Button>
        }
      />

      <Card padding="none" className="overflow-hidden">
        {isLoading ? (
          <LoadingState />
        ) : tenants.length === 0 ? (
          <EmptyState>Nenhuma empresa cadastrada ainda.</EmptyState>
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Código</TH>
                <TH>Nome</TH>
                <TH>Status</TH>
                <TH align="right">Ações</TH>
              </tr>
            </THead>
            <TBody>
              {tenants.map(t => (
                <TR key={t.id}>
                  <TD className="font-mono font-semibold text-gray-700">{t.codigo}</TD>
                  <TD className="font-medium text-gray-900">
                    {t.nome}
                    {t.schemaName === 'public' && (
                      <span className="text-gray-400 font-normal"> — este negócio (produção)</span>
                    )}
                  </TD>
                  <TD>
                    <StatusBadge tone={t.ativo ? 'success' : 'neutral'}>
                      {t.ativo ? 'Ativa' : 'Inativa'}
                    </StatusBadge>
                  </TD>
                  <TD align="right">
                    {t.ativo ? (
                      <Button
                        variant="outline-danger"
                        size="sm"
                        disabled={t.schemaName === 'public'}
                        loading={desativar.isPending}
                        onClick={() => desativar.mutate(t.id)}
                      >
                        <PowerOff size={13} /> Desativar
                      </Button>
                    ) : (
                      <Button
                        variant="success"
                        size="sm"
                        loading={ativar.isPending}
                        onClick={() => ativar.mutate(t.id)}
                      >
                        <Power size={13} /> Ativar
                      </Button>
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {showNovo && (
        <Modal
          title="Nova Empresa"
          onClose={() => setShowNovo(false)}
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={() => setShowNovo(false)}>
                Cancelar
              </Button>
              <Button
                variant="primary"
                fullWidth
                disabled={!novoNome.trim()}
                loading={provisionar.isPending}
                onClick={() => provisionar.mutate()}
              >
                Criar
              </Button>
            </>
          }
        >
          <Field label="Nome da empresa *" hint="O código de acesso é gerado automaticamente (ex: 002).">
            <input
              value={novoNome}
              onChange={e => setNovoNome(e.target.value)}
              placeholder="Ex: Padaria da Maria"
              className={baseInputClass}
              autoFocus
            />
          </Field>
          <p className="text-xs text-gray-400">
            A empresa nasce <strong>inativa</strong> — ative na lista quando quiser liberar o acesso.
          </p>
        </Modal>
      )}
    </div>
  )
}
