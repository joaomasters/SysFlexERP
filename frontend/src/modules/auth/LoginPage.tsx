import { useState } from 'react'
import axios from 'axios'
import { useNavigate } from 'react-router-dom'
import { setSessao } from '../../shared/auth'
import { API_BASE_URL } from '../../shared/api/ambiente'
import FaixaAmbiente from '../../shared/components/ui/FaixaAmbiente'
import { Building2, Lock, ShieldCheck, User, LogIn } from 'lucide-react'
import logo from '../../assets/sysflex-logo-escuro.png'
import LoginIlustracao, { LoginOndas } from './LoginIlustracao'

export default function LoginPage() {
  const navigate = useNavigate()
  const [codigoEmpresa, setCodigoEmpresa] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [codigoVerificacao, setCodigoVerificacao] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const ehJarvis = codigoEmpresa.trim() === '000'

  // Código nunca é lembrado (localStorage, sessão, nada) — precisa ser
  // digitado em todo login, de propósito, inclusive pelo Açougue do Zé
  // (código 001). Ver TenantResolvingFilter/AuthController no backend.
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const res = await axios.post(
        `${API_BASE_URL}/auth/login`,
        ehJarvis ? { username, password, codigoVerificacao } : { username, password },
        { headers: { 'X-Tenant-Codigo': codigoEmpresa.trim() } }
      )
      setSessao(res.data)
      navigate(res.data.jarvis ? '/jarvis' : '/inicio')
    } catch (e: unknown) {
      const resp = (e as { response?: { status?: number; data?: { message?: string } } }).response
      // 429 = login bloqueado temporariamente por excesso de tentativas
      setError(resp?.status === 429 && resp.data?.message
        ? resp.data.message
        : 'Código da empresa, usuário ou senha incorretos')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex flex-col bg-white">
      <FaixaAmbiente />
      <main className="login-claro flex flex-1 flex-col md:grid md:grid-cols-2">
        {/* Ilustração (só a partir de 768px): metade esquerda da tela */}
        <section className="relative hidden flex-col items-center justify-center gap-10 overflow-hidden bg-primary-50 px-10 pb-44 pt-16 md:flex">
          <LoginIlustracao />
          <p className="relative z-10 whitespace-nowrap text-center text-[11px] font-semibold uppercase tracking-[0.12em] text-primary-800/70 lg:text-xs lg:tracking-[0.2em] xl:text-sm">
            PDV · Estoque · Financeiro · Fiscal
          </p>
          <LoginOndas />
        </section>

        {/* Formulário */}
        <section className="flex flex-1 items-center justify-center px-6 py-12 sm:px-12">
          <div className="w-full max-w-sm xl:max-w-md">
            <img src={logo} alt="SysFlex ERP" className="mx-auto h-14 w-auto xl:h-16" />
            <h1 className="sr-only">Entrar no sistema</h1>
            <p className="mb-8 mt-2 text-center text-sm text-gray-500">Sistema de Gestão e PDV</p>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Código da empresa</label>
                <div className="relative">
                  <Building2 size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-primary-600" />
                  <input
                    type="text"
                    inputMode="numeric"
                    value={codigoEmpresa}
                    onChange={e => setCodigoEmpresa(e.target.value)}
                    className="block w-full bg-white border border-gray-300 rounded-lg pl-10 pr-3 py-3 text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 transition"
                    placeholder="Ex: 001"
                    autoCapitalize="none"
                    autoCorrect="off"
                    autoComplete="off"
                    required
                    autoFocus
                  />
                </div>
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Usuário</label>
                <div className="relative">
                  <User size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-primary-600" />
                  <input
                    type="text"
                    value={username}
                    onChange={e => setUsername(e.target.value)}
                    className="block w-full bg-white border border-gray-300 rounded-lg pl-10 pr-3 py-3 text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 transition"
                    placeholder="Digite seu usuário"
                    required
                  />
                </div>
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-gray-600">Senha</label>
                <div className="relative">
                  <Lock size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-primary-600" />
                  <input
                    type="password"
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    className="block w-full bg-white border border-gray-300 rounded-lg pl-10 pr-3 py-3 text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 transition"
                    placeholder="••••••"
                    required
                  />
                </div>
              </div>

              {ehJarvis && (
                <div>
                  <label className="mb-1 block text-xs font-medium text-gray-600">Código do autenticador</label>
                  <div className="relative">
                    <ShieldCheck size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-primary-600" />
                    <input
                      type="text"
                      inputMode="numeric"
                      maxLength={6}
                      value={codigoVerificacao}
                      onChange={e => setCodigoVerificacao(e.target.value.replace(/\D/g, ''))}
                      className="block w-full bg-white border border-gray-300 rounded-lg pl-10 pr-3 py-3 text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 transition"
                      placeholder="6 dígitos"
                      autoComplete="one-time-code"
                      required
                    />
                  </div>
                </div>
              )}

              {error && (
                <div role="alert" className="rounded-lg border border-danger-200 bg-danger-50 px-3 py-2">
                  <p className="text-sm text-danger-700">{error}</p>
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="mt-2 flex w-full items-center justify-center gap-2 rounded-lg bg-primary-600 py-3 text-base font-semibold text-white transition hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-500/40 focus:ring-offset-2 disabled:opacity-50"
              >
                <LogIn size={18} aria-hidden="true" />
                {loading ? 'Entrando...' : 'Entrar'}
              </button>
            </form>

            <p className="mt-8 text-center text-xs text-gray-400">SysFlex ERP v1.0.0</p>
          </div>
        </section>
      </main>
    </div>
  )
}
