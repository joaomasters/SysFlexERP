import { useState } from 'react'
import axios from 'axios'
import { useNavigate } from 'react-router-dom'
import { setSessao } from '../../shared/auth'
import { Lock, User } from 'lucide-react'
import logo from '../../assets/sysflex-logo.png'

export default function LoginPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const res = await axios.post('/api/auth/login', { username, password })
      setSessao(res.data)
      navigate('/inicio')
    } catch {
      setError('Usuário ou senha incorretos')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-aco-900 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <img
            src={logo}
            alt="SysFlex ERP"
            className="h-14 w-auto mx-auto"
          />
        </div>

        <div className="bg-aco-800 rounded-2xl p-8 shadow-2xl border border-white/10">
          <h2 className="text-white font-display font-semibold text-base mb-6">Entrar no sistema</h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-xs text-white/50 font-medium">Usuário</label>
              <div className="relative mt-1">
                <User size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/40" />
                <input
                  type="text"
                  value={username}
                  onChange={e => setUsername(e.target.value)}
                  className="block w-full bg-aco-900 border border-white/15 rounded-lg pl-9 pr-3 py-2.5 text-white placeholder-white/30 text-sm focus:outline-none focus:border-talho-600 transition"
                  placeholder="Digite seu usuário"
                  required
                  autoFocus
                />
              </div>
            </div>

            <div>
              <label className="text-xs text-white/50 font-medium">Senha</label>
              <div className="relative mt-1">
                <Lock size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/40" />
                <input
                  type="password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  className="block w-full bg-aco-900 border border-white/15 rounded-lg pl-9 pr-3 py-2.5 text-white placeholder-white/30 text-sm focus:outline-none focus:border-talho-600 transition"
                  placeholder="••••••"
                  required
                />
              </div>
            </div>

            {error && (
              <div className="bg-talho-600/10 border border-talho-600/40 rounded-lg px-3 py-2">
                <p className="text-talho-100 text-sm">{error}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 bg-talho-600 text-white rounded-lg font-medium text-sm hover:bg-talho-700 disabled:opacity-50 transition mt-2"
            >
              {loading ? 'Entrando...' : 'Entrar'}
            </button>
          </form>
        </div>
        <p className="text-center text-white/30 text-xs mt-6">
          SysFlex ERP v1.0.0
        </p>
      </div>
    </div>
  )
}