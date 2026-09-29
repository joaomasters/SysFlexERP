import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, KeyRound, Power, Lock } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'
import toast from 'react-hot-toast'
import type { Usuario, Perfil } from '@/types/acesso'
import {
  PageHeader, Card, Modal, Button, StatusBadge, Field, baseInputClass, ConfirmDialog,
  Table, THead, TH, TBody, TR, TD, EmptyState,
} from '@/shared/components/ui'

export default function UsuariosPage() {
  const qc = useQueryClient()
  const { podeVer, podeCriar, podeEditar, podeExcluir } = usePermissao()

  const [showNovo, setShowNovo]   = useState(false)
  const [nome, setNome]           = useState('')
  const [login, setLogin]         = useState('')
  const [senha, setSenha]         = useState('')
  const [perfilId, setPerfilId]   = useState('')
  const [trocandoSenhaId, setTrocandoSenhaId] = useState<number | null>(null)
  const [novaSenha, setNovaSenha] = useState('')
  const [confirmando, setConfirmando] = useState<Usuario | null>(null)

  const { data: usuarios = [] } = useQuery<Usuario[]>({
    queryKey: ['usuarios'],
    queryFn: () => api.get('/usuarios').then(r => r.data),
    enabled: podeVer('USUARIOS'),
  })

  const { data: perfis = [] } = useQuery<Perfil[]>({
    queryKey: ['perfis'],
    queryFn: () => api.get('/perfis').then(r => r.data),
    enabled: podeVer('USUARIOS'),
  })

  const criar = useMutation({
    mutationFn: () => api.post('/usuarios', { nome, login, senha, perfilId: Number(perfilId) }),
    onSuccess: () => {
      toast.success('Usuário criado!')
      qc.invalidateQueries({ queryKey: ['usuarios'] })
      setNome(''); setLogin(''); setSenha(''); setPerfilId(''); setShowNovo(false)
    },
  })

  const trocarPerfil = useMutation({
    mutationFn: ({ id, nome, perfilId }: { id: number; nome: string; perfilId: number }) =>
      api.put(`/usuarios/${id}`, { nome, perfilId }),
    onSuccess: () => {
      toast.success('Perfil do usuário atualizado.')
      qc.invalidateQueries({ queryKey: ['usuarios'] })
    },
  })

  const alterarStatus = useMutation({
    mutationFn: ({ id, ativo }: { id: number; ativo: boolean }) =>
      api.patch(`/usuarios/${id}/status`, { ativo }),
    onSuccess: () => {
      toast.success('Status atualizado.')
      qc.invalidateQueries({ queryKey: ['usuarios'] })
      setConfirmando(null)
    },
  })

  const trocarSenha = useMutation({
    mutationFn: (id: number) => api.put(`/usuarios/${id}/senha`, { novaSenha }),
    onSuccess: () => {
      toast.success('Senha alterada.')
      setTrocandoSenhaId(null)
      setNovaSenha('')
    },
  })

  if (!podeVer('USUARIOS')) {
    return (
      <div className="p-6">
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-5 max-w-lg">
          <p className="flex items-center gap-2 font-medium text-warning-700">
            <Lock size={16} /> Acesso restrito
          </p>
          <p className="text-sm text-warning-600 mt-1">
            Seu perfil não tem permissão para visualizar os usuários do sistema.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="p-6">
      <PageHeader
        title="Usuários"
        subtitle="Contas de acesso ao sistema e seus perfis"
        actions={
          podeCriar('USUARIOS') && (
            <Button variant="primary" onClick={() => setShowNovo(v => !v)}>
              <Plus size={16} /> Novo Usuário
            </Button>
          )
        }
      />

      {showNovo && podeCriar('USUARIOS') && (
        <Card className="mb-5 grid grid-cols-1 md:grid-cols-5 gap-3 items-end">
          <Field label="Nome *">
            <input value={nome} onChange={e => setNome(e.target.value)} className={baseInputClass} />
          </Field>
          <Field label="Login *">
            <input value={login} onChange={e => setLogin(e.target.value)} className={baseInputClass} />
          </Field>
          <Field label="Senha *">
            <input type="password" value={senha} onChange={e => setSenha(e.target.value)} className={baseInputClass} />
          </Field>
          <Field label="Perfil *">
            <select value={perfilId} onChange={e => setPerfilId(e.target.value)} className={baseInputClass}>
              <option value="">Selecione...</option>
              {perfis.map(p => <option key={p.id} value={p.id}>{p.nome}</option>)}
            </select>
          </Field>
          <Button
            variant="success"
            disabled={!nome || !login || !senha || !perfilId}
            loading={criar.isPending}
            onClick={() => criar.mutate()}
          >
            Criar
          </Button>
        </Card>
      )}

      <Card padding="none" className="overflow-hidden">
        <Table>
          <THead>
            <tr>
              <TH>Nome</TH>
              <TH>Login</TH>
              <TH>Perfil</TH>
              <TH>Status</TH>
              <TH />
            </tr>
          </THead>
          <TBody>
            {usuarios.map(u => (
              <TR key={u.id}>
                <TD className="font-medium text-gray-900">{u.nome}</TD>
                <TD className="text-gray-500 font-mono text-xs">{u.login}</TD>
                <TD>
                  {podeEditar('USUARIOS') ? (
                    <select
                      value={u.perfil.id}
                      onChange={e => trocarPerfil.mutate({ id: u.id, nome: u.nome, perfilId: Number(e.target.value) })}
                      className="border border-gray-300 rounded px-2 py-1 text-xs"
                    >
                      {perfis.map(p => <option key={p.id} value={p.id}>{p.nome}</option>)}
                    </select>
                  ) : (
                    <span className="text-gray-600">{u.perfil.nome}</span>
                  )}
                </TD>
                <TD>
                  <StatusBadge tone={u.ativo ? 'success' : 'neutral'}>{u.ativo ? 'Ativo' : 'Inativo'}</StatusBadge>
                </TD>
                <TD align="right">
                  <div className="flex items-center justify-end gap-1">
                    {podeEditar('USUARIOS') && (
                      <Button variant="ghost" size="sm" className="hover:!text-info-600" onClick={() => setTrocandoSenhaId(u.id)} title="Trocar senha">
                        <KeyRound size={15} />
                      </Button>
                    )}
                    {podeExcluir('USUARIOS') && (
                      <Button
                        variant="ghost" size="sm"
                        className="hover:!text-danger-600"
                        onClick={() => u.ativo ? setConfirmando(u) : alterarStatus.mutate({ id: u.id, ativo: true })}
                        title={u.ativo ? 'Desativar' : 'Reativar'}
                      >
                        <Power size={15} />
                      </Button>
                    )}
                  </div>
                </TD>
              </TR>
            ))}
            {usuarios.length === 0 && (
              <tr><td colSpan={5}><EmptyState>Nenhum usuário cadastrado.</EmptyState></td></tr>
            )}
          </TBody>
        </Table>
      </Card>

      {/* Modal de troca de senha */}
      {trocandoSenhaId !== null && (
        <Modal
          title="Trocar senha"
          onClose={() => { setTrocandoSenhaId(null); setNovaSenha('') }}
          footer={
            <Button
              variant="primary"
              fullWidth
              disabled={novaSenha.length < 4}
              loading={trocarSenha.isPending}
              onClick={() => trocarSenha.mutate(trocandoSenhaId)}
            >
              Salvar nova senha
            </Button>
          }
        >
          <input
            type="password"
            autoFocus
            value={novaSenha}
            onChange={e => setNovaSenha(e.target.value)}
            placeholder="Nova senha (mín. 4 caracteres)"
            className={baseInputClass}
          />
        </Modal>
      )}

      {/* Confirmação de desativação */}
      {confirmando && (
        <ConfirmDialog
          title="Desativar usuário?"
          message={`"${confirmando.nome}" não vai mais conseguir fazer login no sistema até ser reativado.`}
          confirmLabel="Desativar"
          loading={alterarStatus.isPending}
          onConfirm={() => alterarStatus.mutate({ id: confirmando.id, ativo: false })}
          onCancel={() => setConfirmando(null)}
        />
      )}
    </div>
  )
}