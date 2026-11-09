import { useEffect } from 'react'
import { NavLink } from 'react-router-dom'
import {
  X,
  ShoppingCart, Package, Scissors, DollarSign,
  CreditCard, BarChart2, Scale, AlertTriangle,
  ClipboardList, TrendingDown, ArrowDownCircle, BarChart, LogOut,
  Truck, FileText, Users, ShieldCheck, Home, Contact,
  ShieldAlert, CalendarClock, Percent, Building2, Store, Printer, Wrench, FileKey
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { api } from '../../api/axios'
import { removeSessao } from '../../auth'
import { usePermissao } from '../../hooks/usePermissao'
import logo from '../../../assets/sysflex-logo.png'

const nav = [
  { label: 'Início', href: '/inicio', icon: Home, sempreVisivel: true },
  { label: 'PDV / Caixa',          href: '/pdv',              icon: ShoppingCart,   external: true, modulo: 'PDV' },
  { label: 'Sangria / Suprimento', href: '/pdv/sangria',      icon: ArrowDownCircle, modulo: 'SANGRIA' },

  { separator: 'Cadastro' },
  { label: 'Produtos',          href: '/produtos',               icon: Package,        modulo: 'PRODUTOS' },
  { label: 'Fornecedores',      href: '/estoque/fornecedores',   icon: Store,          modulo: 'FORNECEDORES' },
  { label: 'Clientes',          href: '/financeiro/clientes',    icon: Contact,        modulo: 'CLIENTES' },
  { label: 'Usuários',          href: '/acesso/usuarios',        icon: Users,          modulo: 'USUARIOS' },

  { separator: 'Estoque' },
  { label: 'Recebimento',       href: '/estoque/recebimento',    icon: Truck,          modulo: 'RECEBIMENTO' },
  { label: 'Fichas Desossa',    href: '/estoque/fichas-desossa', icon: ClipboardList,  modulo: 'FICHAS_DESOSSA' },
  { label: 'Rateio de Desossa', href: '/estoque/desossa',        icon: Scissors,       modulo: 'RATEIO_DESOSSA' },
  { label: 'Inventário',        href: '/estoque/inventario',     icon: ClipboardList,  modulo: 'INVENTARIO' },
  { label: 'Perdas',            href: '/estoque/perdas',         icon: AlertTriangle,  modulo: 'PERDAS' },
  { label: 'Validade',          href: '/estoque/validade',       icon: CalendarClock,  modulo: 'PRODUTOS' },
  { label: 'Carga Balança',     href: '/balanca',                icon: Scale,          modulo: 'CARGA_BALANCA' },

  { separator: 'Fiscal' },
  { label: 'NF de Saída', href: '/fiscal/notas', icon: FileText, modulo: 'NF_SAIDA' },
  { label: 'Emissão de Notas', href: '/fiscal/emissao', icon: FileKey, modulo: 'FISCAL' },

  { separator: 'Financeiro' },
  { label: 'Faturamento',      href: '/financeiro/faturamento',    icon: DollarSign,  modulo: 'FATURAMENTO' },
  { label: 'Contas a Receber', href: '/financeiro/contas-receber', icon: CreditCard,  modulo: 'CONTAS_RECEBER' },
  { label: 'Contas a Pagar',   href: '/financeiro/contas-pagar',   icon: TrendingDown, modulo: 'CONTAS_PAGAR' },
  { label: 'Comissões',        href: '/financeiro/comissoes',      icon: Percent,     modulo: 'COMISSOES' },

  { separator: 'Relatórios gerenciais' },
  { label: 'Relatórios',       href: '/financeiro/relatorios',     icon: BarChart,    modulo: 'RELATORIOS' },
  { label: 'DRE',              href: '/financeiro/dre',            icon: BarChart2,   modulo: 'DRE' },

  { separator: 'Administração' },
  { label: 'Perfis',   href: '/acesso/perfis',   icon: ShieldCheck, modulo: 'PERFIS' },
  { label: 'Auditoria', href: '/acesso/auditoria', icon: ShieldAlert, modulo: 'AUDITORIA' },
  { label: 'Maquininhas', href: '/acesso/maquininhas', icon: CreditCard, modulo: 'MAQUININHAS' },
  { label: 'Comprovante', href: '/acesso/comprovante', icon: Printer, modulo: 'COMPROVANTE' },
  // Sem Modulo: só o SUPER_ADMIN de cada empresa (limpar cache etc.)
  { label: 'Manutenção', href: '/acesso/manutencao', icon: Wrench, somenteSuperAdmin: true },
  // Sem Modulo no backend de propósito: só o super admin da EMPRESA PRINCIPAL
  // gerencia empresas (o SUPER_ADMIN de cada empresa cliente não vê este item).
  { label: 'Empresas', href: '/acesso/empresas', icon: Building2, somentePlataforma: true },
]

interface SidebarProps {
  /** Abaixo de lg o menu é uma gaveta: só aparece quando aberto. */
  aberto: boolean
  onFechar: () => void
}

export default function Sidebar({ aberto, onFechar }: SidebarProps) {
  // Esc fecha a gaveta (celular/tablet)
  useEffect(() => {
    if (!aberto) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onFechar() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [aberto, onFechar])

  const { podeVer, isSuperAdmin, isAdministradorPlataforma, sessao } = usePermissao()

  const podeVerBalanca = isSuperAdmin || podeVer('CARGA_BALANCA')

  // Badge de pendências de preço na balança — só busca se o usuário
  // realmente enxerga o módulo, pra não gerar chamada desnecessária.
  const { data: pendentesBalanca } = useQuery<{ total: number }>({
    queryKey: ['balanca-pendentes-count'],
    queryFn: () => api.get('/balanca/pendentes/count').then(r => r.data),
    enabled: podeVerBalanca,
    refetchInterval: 60_000,
  })
  const qtdPendentesBalanca = pendentesBalanca?.total ?? 0

  const logout = () => {
    removeSessao()
    // Recarrega de propósito: o cache de consultas fica em memória e, numa troca
    // de rota comum, a próxima empresa veria os dados da anterior
    window.location.href = '/login'
  }

  // Filtra os itens pelo que o perfil pode VER, e depois remove separadores
  // que ficaram sem nenhum item visível embaixo (evita título "solto").
  const itensVisiveis = nav.filter(item => {
    if ('separator' in item || item.sempreVisivel) return true
    if (item.somentePlataforma) return isAdministradorPlataforma
    if (item.somenteSuperAdmin) return isSuperAdmin && !sessao?.somenteLeitura
    return isSuperAdmin || podeVer(item.modulo!)
  })
  const navFiltrado = itensVisiveis.filter((item, i) => {
    if (!('separator' in item)) return true
    const proximo = itensVisiveis[i + 1]
    return proximo && !('separator' in proximo)
  })

  return (
    <>
    {/* Fundo escuro atrás da gaveta (só celular/tablet) — toque fecha */}
    <div
      onClick={onFechar}
      aria-hidden
      className={clsx(
        'fixed inset-0 z-40 bg-black/50 transition-opacity lg:hidden',
        aberto ? 'opacity-100' : 'opacity-0 pointer-events-none'
      )}
    />
    <aside
      className={clsx(
        'w-64 lg:w-60 shrink-0 bg-aco-900 text-white flex flex-col h-full',
        // Celular/tablet: gaveta fixa que desliza da esquerda. Desktop: coluna normal.
        'fixed inset-y-0 left-0 z-50 transition-transform duration-200 lg:static lg:translate-x-0',
        aberto ? 'translate-x-0 shadow-2xl' : '-translate-x-full'
      )}
    >
      <div className="px-5 py-5 border-b border-white/10 flex items-center justify-between">
        <img src={logo} alt="SysFlex ERP" className="h-9 w-auto" />
        <button onClick={onFechar} aria-label="Fechar menu" className="lg:hidden p-1.5 -mr-2 text-white/70 hover:text-white">
          <X size={20} />
        </button>
      </div>

      {/* Rola com roda/toque, mas sem barra visível (Firefox e Chrome/Edge) */}
      <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {navFiltrado.map((item, i) => {
          if ('separator' in item) {
            return (
              <p
                key={i}
                className="px-3 pt-5 pb-1.5 text-xs text-white/40 border-t border-white/10 first:border-t-0 first:pt-0"
              >
                {item.separator}
              </p>
            )
          }
          const Icon = item.icon!
          if (item.external) {
            return (
              <a
                key={item.href}
                href={item.href}
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-3 pl-[13px] pr-3 py-3 lg:py-2.5 border-l-[3px] border-transparent text-sm text-white/70 hover:bg-white/5 hover:text-white transition-colors"
              >
                <Icon size={16} />
                {item.label}
              </a>
            )
          }
          return (
            <NavLink
              key={item.href}
              to={item.href!}
              className={({ isActive }) =>
                clsx(
                  'flex items-center gap-3 pl-[13px] pr-3 py-3 lg:py-2.5 border-l-[3px] text-sm transition-colors',
                  isActive
                    ? 'border-talho-600 bg-aco-800 text-white font-medium'
                    : 'border-transparent text-white/70 hover:bg-white/5 hover:text-white'
                )
              }
            >
              <Icon size={16} />
              <span className="flex-1">{item.label}</span>
              {item.href === '/balanca' && qtdPendentesBalanca > 0 && (
                <span className="bg-mostarda-600 text-white text-[10px] font-semibold rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-1">
                  {qtdPendentesBalanca > 99 ? '99+' : qtdPendentesBalanca}
                </span>
              )}
            </NavLink>
          )
        })}
      </nav>

      <div className="px-3 py-3 border-t border-white/10">
        <button
          onClick={logout}
          className="flex items-center gap-3 pl-[13px] pr-3 py-3 lg:py-2.5 border-l-[3px] border-transparent text-sm text-white/50 hover:bg-white/5 hover:text-white transition-colors w-full"
        >
          <LogOut size={16} />
          Sair
        </button>
      </div>
    </aside>
    </>
  )
}