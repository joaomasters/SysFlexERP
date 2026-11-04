import { Navigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle, Eye, LogOut } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { getSessao, iniciarVisualizacao, removeSessao, type SessaoUsuario } from '@/shared/auth'
import FaixaAmbiente from '@/shared/components/ui/FaixaAmbiente'

interface ResumoEmpresa {
  id: number
  codigo: string
  nome: string
  ativo: boolean
  vendasHoje: number
  faturamentoHoje: number
  faturamentoMes: number
  produtosAtivos: number
  usuariosAtivos: number
  ultimaVenda: string | null
  erro: boolean
}

interface Acesso {
  id: number
  login: string
  acao: 'LOGIN' | 'VISUALIZAR_EMPRESA'
  empresaCodigo: string | null
  empresaNome: string | null
  criadoEm: string
}

const moeda = (v: number) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
const dataHora = (v: string) => new Date(v).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

function Indicador({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
      <p className="text-xs text-gray-500">{rotulo}</p>
      <p className="text-2xl font-semibold text-gray-900 mt-1">{valor}</p>
    </div>
  )
}

export default function JarvisPage() {
  const sessao = getSessao()
  const ehPainel = !!sessao?.jarvis && !sessao.somenteLeitura

  const { data: empresas = [], isLoading } = useQuery<ResumoEmpresa[]>({
    queryKey: ['jarvis-empresas'],
    queryFn: () => api.get('/jarvis/empresas').then(r => r.data),
    enabled: ehPainel,
    refetchInterval: 60_000,
  })

  const { data: acessos = [] } = useQuery<Acesso[]>({
    queryKey: ['jarvis-acessos'],
    queryFn: () => api.get('/jarvis/acessos').then(r => r.data),
    enabled: ehPainel,
  })

  // Recarrega a página: nada do painel fica em memória durante a visualização
  const visualizar = useMutation({
    mutationFn: (id: number) => api.post<SessaoUsuario>(`/jarvis/empresas/${id}/visualizar`).then(r => r.data),
    onSuccess: (sessaoEmpresa) => {
      iniciarVisualizacao(sessaoEmpresa)
      window.location.href = '/inicio'
    },
  })

  const sair = () => {
    removeSessao()
    window.location.href = '/login'
  }

  if (!ehPainel) return <Navigate to="/inicio" replace />

  const ativas = empresas.filter(e => e.ativo).length

  return (
    <div className="min-h-screen bg-papel-50">
      <FaixaAmbiente />
      <header className="bg-aco-900 text-white px-6 py-4 flex items-center justify-between">
        <div>
          <h1 className="font-display font-semibold text-lg tracking-wide">JARVIS</h1>
          <p className="text-xs text-white/50">Visão de todas as empresas, somente leitura</p>
        </div>
        <button onClick={sair} className="flex items-center gap-2 text-sm text-white/60 hover:text-white transition-colors">
          <LogOut size={16} />
          Sair
        </button>
      </header>

      <main className="max-w-6xl mx-auto p-6 space-y-6">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <Indicador rotulo="Empresas ativas" valor={`${ativas} de ${empresas.length}`} />
          <Indicador rotulo="Vendas hoje" valor={String(empresas.reduce((s, e) => s + e.vendasHoje, 0))} />
          <Indicador rotulo="Faturamento hoje" valor={moeda(empresas.reduce((s, e) => s + e.faturamentoHoje, 0))} />
          <Indicador rotulo="Faturamento no mês" valor={moeda(empresas.reduce((s, e) => s + e.faturamentoMes, 0))} />
        </div>

        <section className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-gray-500 border-b border-gray-100">
                <th className="px-4 py-3 font-medium">Empresa</th>
                <th className="px-4 py-3 font-medium">Situação</th>
                <th className="px-4 py-3 font-medium text-right">Vendas hoje</th>
                <th className="px-4 py-3 font-medium text-right">Hoje</th>
                <th className="px-4 py-3 font-medium text-right">No mês</th>
                <th className="px-4 py-3 font-medium text-right">Produtos</th>
                <th className="px-4 py-3 font-medium text-right">Usuários</th>
                <th className="px-4 py-3 font-medium">Última venda</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {empresas.map(e => (
                <tr key={e.id} className="border-b border-gray-50 last:border-0">
                  <td className="px-4 py-3">
                    <span className="font-mono text-xs text-gray-400 mr-2">{e.codigo}</span>
                    <span className="font-medium text-gray-900">{e.nome}</span>
                  </td>
                  <td className="px-4 py-3">
                    {e.erro ? (
                      <span className="inline-flex items-center gap-1 text-xs text-red-700">
                        <AlertTriangle size={13} /> Falha na leitura
                      </span>
                    ) : (
                      <span className={`text-xs ${e.ativo ? 'text-emerald-700' : 'text-gray-500'}`}>
                        {e.ativo ? 'Ativa' : 'Inativa'}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">{e.vendasHoje}</td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">{moeda(e.faturamentoHoje)}</td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">{moeda(e.faturamentoMes)}</td>
                  <td className="px-4 py-3 text-right">{e.produtosAtivos}</td>
                  <td className="px-4 py-3 text-right">{e.usuariosAtivos}</td>
                  <td className="px-4 py-3 text-gray-600 whitespace-nowrap">{e.ultimaVenda ? dataHora(e.ultimaVenda) : '—'}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => visualizar.mutate(e.id)}
                      disabled={visualizar.isPending}
                      aria-label={`Ver empresa ${e.codigo}`}
                      className="inline-flex items-center gap-1.5 text-xs font-medium text-violet-700 hover:text-violet-900 disabled:opacity-50 whitespace-nowrap"
                    >
                      <Eye size={14} /> Ver empresa
                    </button>
                  </td>
                </tr>
              ))}
              {!isLoading && empresas.length === 0 && (
                <tr><td colSpan={9} className="px-4 py-8 text-center text-gray-500">Nenhuma empresa cadastrada.</td></tr>
              )}
            </tbody>
          </table>
        </section>

        <section className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
          <h2 className="text-sm font-semibold text-gray-900 mb-3">Acessos recentes</h2>
          <ul className="divide-y divide-gray-50 text-sm">
            {acessos.map(a => (
              <li key={a.id} className="py-2 flex justify-between gap-4">
                <span className="text-gray-700">
                  {a.acao === 'LOGIN' ? `Login de ${a.login}` : `${a.login} visualizou ${a.empresaCodigo} · ${a.empresaNome}`}
                </span>
                <span className="text-gray-500 whitespace-nowrap">{dataHora(a.criadoEm)}</span>
              </li>
            ))}
          </ul>
        </section>
      </main>
    </div>
  )
}
