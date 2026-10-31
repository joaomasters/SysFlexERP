import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Lock, Power, PowerOff, Building2, Eraser, AlertTriangle } from 'lucide-react'
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
  const { isAdministradorPlataforma } = usePermissao()
  const [showNovo, setShowNovo] = useState(false)
  const [limpando, setLimpando] = useState<Tenant | null>(null)
  const [renomeando, setRenomeando] = useState<Tenant | null>(null)

  const { data: tenants = [], isLoading } = useQuery<Tenant[]>({
    queryKey: ['tenants'],
    queryFn: () => api.get('/admin/tenants').then(r => r.data),
    enabled: isAdministradorPlataforma,
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

  if (!isAdministradorPlataforma) {
    return (
      <div className="p-6">
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-5 max-w-lg">
          <p className="flex items-center gap-2 font-medium text-warning-700">
            <Lock size={16} /> Acesso restrito
          </p>
          <p className="text-sm text-warning-600 mt-1">
            Apenas o super administrador da empresa principal gerencia as empresas que usam o sistema.
            Se você é esse administrador, saia e entre de novo para atualizar sua sessão.
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
        actions={
          <Button variant="primary" onClick={() => setShowNovo(true)}>
            Nova Empresa
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
              {tenants.map(t => {
                const principal = t.schemaName === 'public'
                return (
                  <TR key={t.id}>
                    <TD className="font-mono font-semibold text-gray-700">{t.codigo}</TD>
                    <TD className="font-medium text-gray-900">
                      {t.nome}
                      {principal && <span className="text-gray-400 font-normal"> — empresa principal</span>}
                    </TD>
                    <TD>
                      <StatusBadge tone={t.ativo ? 'success' : 'neutral'}>
                        {t.ativo ? 'Ativa' : 'Inativa'}
                      </StatusBadge>
                    </TD>
                    <TD align="right">
                      <div className="flex justify-end gap-2">
                        <Button variant="secondary" size="sm" onClick={() => setRenomeando(t)}>
                          Renomear
                        </Button>
                        {!principal && (
                          <Button variant="outline-danger" size="sm" onClick={() => setLimpando(t)}>
                            Limpar dados
                          </Button>
                        )}
                        {t.ativo ? (
                          <Button
                            variant="outline-danger"
                            size="sm"
                            disabled={principal}
                            loading={desativar.isPending && desativar.variables === t.id}
                            onClick={() => desativar.mutate(t.id)}
                          >
                            Desativar
                          </Button>
                        ) : (
                          <Button
                            variant="success"
                            size="sm"
                            loading={ativar.isPending && ativar.variables === t.id}
                            onClick={() => ativar.mutate(t.id)}
                          >
                            <Power size={13} /> Ativar
                          </Button>
                        )}
                      </div>
                    </TD>
                  </TR>
                )
              })}
            </TBody>
          </Table>
        )}
      </Card>

      {showNovo && <NovaEmpresaModal onClose={() => setShowNovo(false)} />}
      {limpando && <LimparDadosModal empresa={limpando} onClose={() => setLimpando(null)} />}
      {renomeando && <RenomearEmpresaModal empresa={renomeando} onClose={() => setRenomeando(null)} />}
    </div>
  )
}

// ─── Nova empresa ───────────────────────────────────────────────────────────

function NovaEmpresaModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const [nome, setNome] = useState('')
  const [adminNome, setAdminNome] = useState('')
  const [adminLogin, setAdminLogin] = useState('')
  const [adminSenha, setAdminSenha] = useState('')

  const provisionar = useMutation({
    mutationFn: () => api.post<Tenant>('/admin/tenants', {
      nome: nome.trim(), adminNome: adminNome.trim(), adminLogin: adminLogin.trim(), adminSenha,
    }),
    onSuccess: ({ data }) => {
      toast.success(`Empresa criada! Código de acesso: ${data.codigo}`, { duration: 8000 })
      qc.invalidateQueries({ queryKey: ['tenants'] })
      onClose()
    },
  })

  const valido = nome.trim() && adminNome.trim() && adminLogin.trim() && adminSenha.length >= 8

  return (
    <Modal
      title="Nova Empresa"
      maxWidth="md"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
          <Button variant="primary" fullWidth disabled={!valido} loading={provisionar.isPending} onClick={() => provisionar.mutate()}>
            Criar
          </Button>
        </>
      }
    >
      <Field label="Nome da empresa *" hint="O código de acesso é gerado automaticamente (ex: 004).">
        <input value={nome} onChange={e => setNome(e.target.value)} placeholder="Ex: Padaria da Maria"
               className={baseInputClass} autoFocus />
      </Field>

      <div className="border-t pt-4">
        <p className="text-sm font-medium text-gray-700 mb-3">Administrador da empresa</p>
        <div className="space-y-3">
          <Field label="Nome *">
            <input value={adminNome} onChange={e => setAdminNome(e.target.value)} className={baseInputClass} />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Login *">
              <input value={adminLogin} onChange={e => setAdminLogin(e.target.value)} autoComplete="off"
                     className={baseInputClass} />
            </Field>
            <Field label="Senha *" hint="Mínimo 8 caracteres">
              <input type="password" value={adminSenha} onChange={e => setAdminSenha(e.target.value)}
                     autoComplete="new-password" className={baseInputClass} />
            </Field>
          </div>
        </div>
      </div>

      <p className="text-xs text-gray-500">
        A empresa nasce <strong>vazia</strong> (sem produtos, vendas ou clientes de outras empresas) e{' '}
        <strong>inativa</strong> — ative na lista quando quiser liberar o acesso. Passe ao cliente o código,
        o login e a senha.
      </p>
    </Modal>
  )
}

// ─── Renomear empresa ───────────────────────────────────────────────────────

function RenomearEmpresaModal({ empresa, onClose }: { empresa: Tenant; onClose: () => void }) {
  const qc = useQueryClient()
  const [nome, setNome] = useState(empresa.nome)
  const limpo = nome.trim().replace(/\s+/g, ' ')
  const alterou = limpo !== '' && limpo !== empresa.nome

  const renomear = useMutation({
    mutationFn: () => api.patch<Tenant>(`/admin/tenants/${empresa.id}/nome`, { nome: limpo }),
    onSuccess: ({ data }) => {
      toast.success(`Empresa renomeada para "${data.nome}".`)
      qc.invalidateQueries({ queryKey: ['tenants'] })
      onClose()
    },
  })

  function salvar() {
    if (alterou && !renomear.isPending) renomear.mutate()
  }

  return (
    <Modal
      title={`Renomear empresa ${empresa.codigo}`}
      maxWidth="md"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
          <Button variant="primary" fullWidth disabled={!alterou} loading={renomear.isPending} onClick={salvar}>
            Salvar
          </Button>
        </>
      }
    >
      <Field
        label="Nome da empresa *"
        hint={`O código de acesso (${empresa.codigo}) e os dados continuam os mesmos. O cabeçalho do cupom tem configuração própria, em Comprovante.`}
      >
        <input
          value={nome}
          onChange={e => setNome(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') salvar() }}
          maxLength={120}
          className={baseInputClass}
          autoFocus
          onFocus={e => e.currentTarget.select()}
        />
      </Field>
    </Modal>
  )
}

// ─── Limpar dados de uma empresa ────────────────────────────────────────────

function LimparDadosModal({ empresa, onClose }: { empresa: Tenant; onClose: () => void }) {
  const [confirmacao, setConfirmacao] = useState('')

  const { data: contagem, isLoading } = useQuery<Record<string, number>>({
    queryKey: ['tenant-dados', empresa.id],
    queryFn: () => api.get(`/admin/tenants/${empresa.id}/dados`).then(r => r.data),
  })

  const limpar = useMutation({
    mutationFn: () => api.post<Record<string, number>>(`/admin/tenants/${empresa.id}/limpar-dados`,
      { confirmacaoCodigo: confirmacao.trim() }),
    onSuccess: ({ data }) => {
      const total = Object.values(data).reduce((s, n) => s + n, 0)
      toast.success(`Dados de "${empresa.nome}" apagados (${total} registros).`)
      onClose()
    },
  })

  const linhas = Object.entries(contagem ?? {})
  const total = linhas.reduce((s, [, n]) => s + n, 0)

  return (
    <Modal
      title={`Limpar dados — ${empresa.nome}`}
      maxWidth="md"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
          <Button
            variant="danger"
            fullWidth
            disabled={confirmacao.trim() !== empresa.codigo || total === 0}
            loading={limpar.isPending}
            onClick={() => limpar.mutate()}
          >
            Apagar definitivamente
          </Button>
        </>
      }
    >
      <div className="bg-danger-50 border border-danger-100 rounded-lg p-3 flex gap-2 text-sm text-danger-700">
        <AlertTriangle size={16} className="shrink-0 mt-0.5" />
        <p>
          Apaga <strong>todos</strong> os produtos, vendas, clientes, contas, caixas, estoque e relatórios desta
          empresa. <strong>Não tem como desfazer.</strong> Usuários, perfis e permissões são mantidos.
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-500">Contando registros...</p>
      ) : total === 0 ? (
        <p className="text-sm text-gray-500">Esta empresa não tem dados para apagar.</p>
      ) : (
        <div className="max-h-48 overflow-y-auto border rounded-lg divide-y text-sm">
          {linhas.map(([tabela, n]) => (
            <div key={tabela} className="flex justify-between px-3 py-1.5">
              <span className="font-mono text-xs text-gray-600">{tabela}</span>
              <span className="tabular-nums text-gray-900">{n}</span>
            </div>
          ))}
          <div className="flex justify-between px-3 py-1.5 bg-gray-50 font-medium">
            <span>Total</span><span className="tabular-nums">{total}</span>
          </div>
        </div>
      )}

      <Field label={`Para confirmar, digite o código da empresa (${empresa.codigo})`}>
        <input value={confirmacao} onChange={e => setConfirmacao(e.target.value)} className={baseInputClass} />
      </Field>
    </Modal>
  )
}