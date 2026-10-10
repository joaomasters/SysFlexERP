import { NavLink, useNavigate } from 'react-router-dom'
import {
  ShoppingCart, Package, Scissors, DollarSign,
  CreditCard, BarChart2, Scale, AlertTriangle,
  ClipboardList, TrendingDown, ArrowDownCircle, BarChart, LogOut,
  Truck, FileText, Users, ShieldCheck, Home, Contact, ShieldAlert, CalendarClock, Percent
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

  { separator: 'Estoque' },
  { label: 'Produtos',          href: '/estoque/produtos',       icon: Package,        modulo: 'PRODUTOS' },
  { label: 'Recebimento',       href: '/estoque/recebimento',    icon: Truck,          modulo: 'RECEBIMENTO' },
  { label: 'Fichas Desossa',    href: '/estoque/fichas-desossa', icon: ClipboardList,  modulo: 'FICHAS_DESOSSA' },
  { label: 'Rateio de Desossa', href: '/estoque/desossa',        icon: Scissors,       modulo: 'RATEIO_DESOSSA' },
  { label: 'Inventário',        href: '/estoque/inventario',     icon: ClipboardList,  modulo: 'INVENTARIO' },
  { label: 'Perdas',            href: '/estoque/perdas',         icon: AlertTriangle,  modulo: 'PERDAS' },
  { label: 'Validade',          href: '/estoque/validade',       icon: CalendarClock,  modulo: 'PRODUTOS' },

  { separator: 'Fiscal' },
  { label: 'NF de Saída', href: '/fiscal/notas', icon: FileText, modulo: 'NF_SAIDA' },

  { separator: 'Financeiro' },
  { label: 'Clientes',         href: '/financeiro/clientes',       icon: Contact,      modulo: 'CLIENTES' },
  { label: 'Faturamento',      href: '/financeiro/faturamento',    icon: DollarSign,  modulo: 'FATURAMENTO' },
  { label: 'Contas a Receber', href: '/financeiro/contas-receber', icon: CreditCard,  modulo: 'CONTAS_RECEBER' },
  { label: 'Contas a Pagar',   href: '/financeiro/contas-pagar',   icon: TrendingDown, modulo: 'CONTAS_PAGAR' },
  { label: 'DRE',              href: '/financeiro/dre',            icon: BarChart2,   modulo: 'DRE' },
  { label: 'Relatórios',       href: '/financeiro/relatorios',     icon: BarChart,    modulo: 'RELATORIOS' },
  { label: 'Comissões',        href: '/financeiro/comissoes',      icon: Percent,     modulo: 'COMISSOES' },

  { separator: 'Balança' },
  { label: 'Carga Balança', href: '/balanca', icon: Scale, modulo: 'CARGA_BALANCA' },

  { separator: 'Administração' },
  { label: 'Usuários', href: '/acesso/usuarios', icon: Users,       modulo: 'USUARIOS' },
  { label: 'Perfis',   href: '/acesso/perfis',   icon: ShieldCheck, modulo: 'PERFIS' },
  { label: 'Auditoria', href: '/acesso/auditoria', icon: ShieldAlert, modulo: 'AUDITORIA' },
  // Sem Modulo correspondente no backend de propósito — só super admin
  // provisiona/ativa empresas, e o filtro abaixo já dá bypass pra ele
  // (isSuperAdmin ||) antes mesmo de chamar podeVer.
  { label: 'Empresas', href: '/acesso/empresas', icon: Building2, modulo: 'TENANTS' },
]

export default function Sidebar() {
  const navigate = useNavigate()
  const { podeVer, isSuperAdmin } = usePermissao()

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
    navigate('/login')
  }

  // Filtra os itens pelo que o perfil pode VER, e depois remove separadores
  // que ficaram sem nenhum item visível embaixo (evita título "solto").
  const itensVisiveis = nav.filter(item =>
    'separator' in item || item.sempreVisivel || isSuperAdmin || podeVer(item.modulo!)
  )
  const navFiltrado = itensVisiveis.filter((item, i) => {
    if (!('separator' in item)) return true
    const proximo = itensVisiveis[i + 1]
    return proximo && !('separator' in proximo)
  })

  return (
    <aside className="w-60 bg-aco-900 text-white flex flex-col min-h-screen">
      <div className="px-5 py-5 border-b border-white/10">
        <img src={logo} alt="SysFlex ERP" className="h-9 w-auto" />
      </div>

      <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
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
                className="flex items-center gap-3 pl-[13px] pr-3 py-2.5 border-l-[3px] border-transparent text-sm text-white/70 hover:bg-white/5 hover:text-white transition-colors"
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
                  'flex items-center gap-3 pl-[13px] pr-3 py-2.5 border-l-[3px] text-sm transition-colors',
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
          className="flex items-center gap-3 pl-[13px] pr-3 py-2.5 border-l-[3px] border-transparent text-sm text-white/50 hover:bg-white/5 hover:text-white transition-colors w-full"
        >
          <LogOut size={16} />
          Sair
        </button>
      </div>
    </aside>
  )
}