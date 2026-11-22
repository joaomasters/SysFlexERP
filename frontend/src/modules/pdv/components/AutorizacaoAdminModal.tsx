import { useState } from 'react'
import { ShieldAlert, X } from 'lucide-react'
import { Button, Field, PasswordInput, baseInputClass } from '@/shared/components/ui'

interface Props {
  titulo: string
  /** Linha logo abaixo do título (ex.: o item ou a venda afetada). */
  subtitulo?: string
  /** Texto de apoio acima dos campos. */
  descricao?: string
  confirmarLabel: string
  /** Mostra o campo opcional "Motivo" (ex.: cancelamento de venda). */
  pedirMotivo?: boolean
  enviando?: boolean
  /** Devolve true se a operação foi concluída; false mantém o modal aberto (senha errada etc.). */
  onAutorizar: (login: string, senha: string, motivo?: string) => Promise<boolean>
  onCancelar: () => void
}

/**
 * Pede login e senha de um administrador para liberar uma operação sensível do PDV
 * (excluir item, cancelar venda). Tema claro, igual ao resto do sistema; só a borda é vermelha
 * para sinalizar a ação sensível. A credencial só trafega na chamada e é conferida no servidor.
 * Serve também de confirmação: a operação só acontece depois de autorizada.
 */
export default function AutorizacaoAdminModal({
  titulo, subtitulo, descricao, confirmarLabel, pedirMotivo, enviando, onAutorizar, onCancelar,
}: Props) {
  const [login, setLogin] = useState('')
  const [senha, setSenha] = useState('')
  const [motivo, setMotivo] = useState('')
  const pronto = login.trim() !== '' && senha !== '' && !enviando

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    if (!pronto) return
    const ok = await onAutorizar(login.trim(), senha, pedirMotivo ? motivo.trim() || undefined : undefined)
    if (!ok) setSenha('')   // erro: limpa só a senha e deixa tentar de novo
  }

  return (
    <div
      className="fixed inset-0 z-[70] flex items-center justify-center bg-black/40 p-3 sm:p-4"
      role="dialog" aria-modal="true" aria-label={titulo}
      // as teclas de atalho do PDV (F8, F9, Enter...) não podem agir por baixo do modal
      onKeyDown={e => { e.stopPropagation(); if (e.key === 'Escape' && !enviando) onCancelar() }}
    >
      <form onSubmit={enviar}
        className="w-full max-w-md max-h-full flex flex-col rounded-2xl bg-card border-2 border-danger-500 shadow-flutuante">
        <div className="flex items-start justify-between gap-3 px-4 sm:px-6 py-4 border-b shrink-0">
          <div className="flex items-center gap-3">
            <ShieldAlert className="text-danger-600 shrink-0" size={24} />
            <div>
              <h2 className="font-bold text-gray-900">{titulo}</h2>
              {subtitulo && <p className="text-sm text-gray-500">{subtitulo}</p>}
            </div>
          </div>
          <button type="button" onClick={onCancelar} disabled={enviando} aria-label="Fechar">
            <X size={20} className="text-gray-400 hover:text-gray-600" />
          </button>
        </div>

        <div className="px-4 sm:px-6 py-5 space-y-4 overflow-y-auto min-h-0 flex-1">
          <p className="text-sm text-gray-600">
            {descricao ?? 'Esta ação precisa da autorização de um administrador. Informe o login e a senha dele para continuar.'}
          </p>
          <Field label="Login do administrador">
            <input className={baseInputClass} value={login} onChange={e => setLogin(e.target.value)}
              autoComplete="off" autoFocus maxLength={50} />
          </Field>
          <Field label="Senha">
            <PasswordInput value={senha} onChange={e => setSenha(e.target.value)} autoComplete="new-password" maxLength={100} />
          </Field>
          {pedirMotivo && (
            <Field label="Motivo (opcional)">
              <input className={baseInputClass} value={motivo} onChange={e => setMotivo(e.target.value)}
                autoComplete="off" maxLength={200} placeholder="Ex.: cliente desistiu da compra" />
            </Field>
          )}
        </div>

        <div className="flex gap-3 px-4 sm:px-6 pb-4 sm:pb-6 pt-2 shrink-0">
          <Button type="button" variant="secondary" fullWidth onClick={onCancelar} disabled={enviando}>
            Voltar <span className="opacity-60">[Esc]</span>
          </Button>
          <Button type="submit" variant="danger" fullWidth disabled={!pronto} loading={enviando}>
            {enviando ? 'Validando…' : confirmarLabel}
          </Button>
        </div>
      </form>
    </div>
  )
}
