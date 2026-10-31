import { useState } from 'react'
import { ShieldAlert, X } from 'lucide-react'
import { Eye, EyeOff } from 'lucide-react'
import { formatBRL } from '@/shared/utils/mask'
import type { LimiteExcedidoInfo } from '../hooks/usePdv'

interface Props {
  info: LimiteExcedidoInfo
  /** Login sugerido (o do operador logado, quando ele mesmo é administrador). */
  loginInicial?: string
  enviando?: boolean
  onAutorizar: (login: string, senha: string) => void
  onCancelar: () => void
}

const campo =
  'w-full rounded-lg bg-gray-800 border border-gray-600 px-3 py-2.5 text-sm text-white placeholder-gray-500 ' +
  'focus:outline-none focus:ring-2 focus:ring-amber-500 focus:border-amber-500'

/** Pede a senha de um administrador para liberar fiado acima do limite de crédito. */
export default function AutorizacaoLimiteModal({ info, loginInicial = '', enviando, onAutorizar, onCancelar }: Props) {
  const [login, setLogin] = useState(loginInicial)
  const [senha, setSenha] = useState('')
  const [ver, setVer] = useState(false)
  const pronto = login.trim() !== '' && senha !== '' && !enviando

  function enviar(e: React.FormEvent) {
    e.preventDefault()
    if (pronto) onAutorizar(login.trim(), senha)
  }

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true">
      <form onSubmit={enviar} className="w-full max-w-md rounded-2xl bg-gray-900 border border-amber-500/40 shadow-2xl">
        <div className="flex items-start justify-between gap-3 p-5 border-b border-gray-700">
          <div className="flex items-center gap-3">
            <ShieldAlert className="text-amber-400 shrink-0" size={26} />
            <div>
              <h2 className="text-lg font-semibold text-white">Limite de crédito excedido</h2>
              <p className="text-sm text-gray-400">{info.clienteNome}</p>
            </div>
          </div>
          <button type="button" onClick={onCancelar} aria-label="Fechar" className="text-gray-400 hover:text-white">
            <X size={20} />
          </button>
        </div>

        <div className="p-5 space-y-4">
          <dl className="rounded-lg bg-gray-800/60 p-3 text-sm space-y-1.5">
            <div className="flex justify-between"><dt className="text-gray-400">Limite do cliente</dt><dd className="text-white tabular-nums">{formatBRL(info.limite)}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-400">Fiado em aberto</dt><dd className="text-white tabular-nums">{formatBRL(info.saldoAtual)}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-400">Fiado desta venda</dt><dd className="text-white tabular-nums">{formatBRL(info.valorFiado)}</dd></div>
            <div className="flex justify-between border-t border-gray-700 pt-1.5"><dt className="text-amber-300 font-medium">Acima do limite</dt><dd className="text-amber-300 font-semibold tabular-nums">{formatBRL(info.excedente)}</dd></div>
          </dl>

          <p className="text-sm text-gray-300">Para continuar, um administrador precisa informar o login e a senha.</p>

          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1" htmlFor="aut-login">Login do administrador</label>
            <input id="aut-login" className={campo} value={login} onChange={e => setLogin(e.target.value)}
              autoComplete="off" autoFocus={!loginInicial} maxLength={50} />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-400 mb-1" htmlFor="aut-senha">Senha</label>
            <div className="relative">
              <input id="aut-senha" className={`${campo} pr-10`} type={ver ? 'text' : 'password'} value={senha}
                onChange={e => setSenha(e.target.value)} autoComplete="new-password" autoFocus={!!loginInicial} maxLength={100} />
              <button type="button" onClick={() => setVer(v => !v)} aria-label={ver ? 'Ocultar senha' : 'Mostrar senha'}
                className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-gray-400 hover:text-white">
                {ver ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>
        </div>

        <div className="flex gap-3 p-5 pt-0">
          <button type="button" onClick={onCancelar}
            className="flex-1 rounded-lg border border-gray-600 py-2.5 text-sm font-medium text-gray-200 hover:bg-gray-800">
            Cancelar
          </button>
          <button type="submit" disabled={!pronto}
            className="flex-1 rounded-lg bg-amber-500 py-2.5 text-sm font-semibold text-gray-900 hover:bg-amber-400 disabled:opacity-50 disabled:cursor-not-allowed">
            {enviando ? 'Validando…' : 'Autorizar e finalizar'}
          </button>
        </div>
      </form>
    </div>
  )
}