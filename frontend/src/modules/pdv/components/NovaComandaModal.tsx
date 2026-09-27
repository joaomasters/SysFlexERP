import { useState, useCallback } from 'react'
import { X, Search, UserPlus, User } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { Cliente } from '@/types/venda'

interface Props {
  onSelecionar: (clienteId: number) => void
  onFechar: () => void
}

export default function NovaComandaModal({ onSelecionar, onFechar }: Props) {
  const [busca, setBusca]         = useState('')
  const [resultados, setResultados] = useState<Cliente[]>([])
  const [buscando, setBuscando]   = useState(false)
  const [criando, setCriando]     = useState(false)

  const buscar = useCallback(async (nome: string) => {
    setBusca(nome)
    if (nome.trim().length < 2) {
      setResultados([])
      return
    }
    setBuscando(true)
    try {
      const { data } = await api.get<Cliente[]>('/clientes', { params: { nome } })
      setResultados(data)
    } finally {
      setBuscando(false)
    }
  }, [])

  const criarECriarComanda = useCallback(async () => {
    if (!busca.trim()) return
    setCriando(true)
    try {
      const { data } = await api.post<Cliente>('/clientes', { nome: busca.trim() })
      toast.success(`Cliente "${data.nome}" cadastrado`)
      onSelecionar(data.id)
    } finally {
      setCriando(false)
    }
  }, [busca, onSelecionar])

  const semResultadoExato = busca.trim().length >= 2 &&
    !resultados.some(c => c.nome.toLowerCase() === busca.trim().toLowerCase())

  return (
    <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50">
      <div className="bg-gray-900 border border-gray-800 rounded-2xl w-full max-w-md p-5 text-white">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-bold text-lg">Nova Comanda</h2>
          <button onClick={onFechar} className="text-gray-400 hover:text-white">
            <X size={20} />
          </button>
        </div>

        <div className="relative mb-3">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
          <input
            autoFocus
            value={busca}
            onChange={e => buscar(e.target.value)}
            placeholder="Nome do cliente..."
            className="w-full bg-gray-800 border border-gray-700 rounded-lg pl-9 pr-3 py-2.5 text-sm
                       focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        <div className="max-h-64 overflow-y-auto space-y-1">
          {buscando && <p className="text-gray-500 text-sm px-2 py-3">Buscando...</p>}

          {!buscando && resultados.map(c => (
            <button
              key={c.id}
              onClick={() => onSelecionar(c.id)}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-gray-800 text-left transition-colors"
            >
              <User size={16} className="text-gray-500 shrink-0" />
              <span className="text-sm">{c.nome}</span>
            </button>
          ))}

          {!buscando && semResultadoExato && (
            <button
              onClick={criarECriarComanda}
              disabled={criando}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg
                         bg-primary-700/20 hover:bg-primary-700/30 text-left transition-colors disabled:opacity-60"
            >
              <UserPlus size={16} className="text-primary-400 shrink-0" />
              <span className="text-sm">
                {criando ? 'Criando...' : <>Criar cliente "<strong>{busca.trim()}</strong>" e abrir comanda</>}
              </span>
            </button>
          )}

          {!buscando && busca.trim().length < 2 && (
            <p className="text-gray-500 text-sm px-2 py-3">Digite ao menos 2 letras para buscar.</p>
          )}
        </div>
      </div>
    </div>
  )
}