import { useState, useRef, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bell, AlertTriangle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'

interface ProdutoAlerta {
  id: number
  nome: string
  estoqueAtual: number
  estoqueMinimo: number
  unidadeMedida: string
}

export default function NotificacaoEstoque() {
  const { isSuperAdmin, perfil } = usePermissao()

  // Só perfis de gestão veem esse alerta — CAIXA também tem permissão de
  // VER Produtos (pra consultar preço), mas não deve ser incomodado com
  // isso, que é mais uma informação estratégica de reposição.
  const podeVerAlerta = isSuperAdmin || perfil === 'ADMIN' || perfil === 'GESTOR'

  const [aberto, setAberto] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const { data: alertas = [] } = useQuery<ProdutoAlerta[]>({
    queryKey: ['produtos-alertas'],
    queryFn: () => api.get('/estoque/produtos/alertas').then(r => r.data),
    enabled: podeVerAlerta,
    refetchInterval: 60_000,      // atualiza sozinho a cada 60s
    refetchOnWindowFocus: true,   // e também quando o usuário volta pra aba
  })

  // Fecha o dropdown ao clicar fora dele
  useEffect(() => {
    function aoClicarFora(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setAberto(false)
    }
    document.addEventListener('mousedown', aoClicarFora)
    return () => document.removeEventListener('mousedown', aoClicarFora)
  }, [])

  if (!podeVerAlerta) return null

  const qtd = alertas.length

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setAberto(v => !v)}
        className="relative p-2 rounded-lg hover:bg-gray-100 transition-colors"
        title="Alertas de estoque mínimo"
      >
        <Bell size={20} className={qtd > 0 ? 'text-amber-600' : 'text-gray-400'} />
        {qtd > 0 && (
          <span className="absolute -top-0.5 -right-0.5 bg-talho-600 text-white text-[10px] font-bold
                           rounded-full min-w-[16px] h-4 flex items-center justify-center px-1">
            {qtd > 99 ? '99+' : qtd}
          </span>
        )}
      </button>

      {aberto && (
        <div className="absolute right-0 mt-2 w-80 bg-white rounded-xl shadow-lg border border-gray-200 z-50 overflow-hidden">
          <div className="px-4 py-3 border-b bg-gray-50">
            <p className="font-semibold text-sm text-gray-800 flex items-center gap-1.5">
              <AlertTriangle size={14} className="text-amber-600" />
              Estoque abaixo do mínimo
            </p>
          </div>

          <div className="max-h-80 overflow-y-auto">
            {qtd === 0 && (
              <p className="px-4 py-6 text-center text-sm text-gray-400">
                Tudo certo — nenhum produto abaixo do mínimo.
              </p>
            )}
            {alertas.map(p => (
              <Link
                key={p.id}
                to="/estoque/produtos"
                onClick={() => setAberto(false)}
                className="flex items-center justify-between px-4 py-2.5 hover:bg-amber-50 border-b border-gray-50 last:border-0"
              >
                <div>
                  <p className="text-sm font-medium text-gray-800">{p.nome}</p>
                  <p className="text-xs text-gray-500">
                    Mínimo: {p.estoqueMinimo} {p.unidadeMedida}
                  </p>
                </div>
                <span className="text-sm font-semibold text-red-600">
                  {p.estoqueAtual} {p.unidadeMedida}
                </span>
              </Link>
            ))}
          </div>

          {qtd > 0 && (
            <Link
              to="/estoque/produtos"
              onClick={() => setAberto(false)}
              className="block text-center py-2.5 text-sm font-medium text-red-600 hover:bg-gray-50 border-t"
            >
              Ver todos os produtos
            </Link>
          )}
        </div>
      )}
    </div>
  )
}