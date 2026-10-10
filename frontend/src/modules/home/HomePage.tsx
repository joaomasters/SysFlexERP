import { Link } from 'react-router-dom'
import {
  ShoppingCart, Package, Scissors, DollarSign,
  CreditCard, BarChart2, Scale, AlertTriangle,
  ClipboardList, TrendingDown, ArrowDownCircle, BarChart,
  Truck, FileText, Users, ShieldCheck, Percent,
} from 'lucide-react'
import { usePermissao } from '@/shared/hooks/usePermissao'

const atalhos = [
  { label: 'PDV / Caixa',          href: '/pdv',                       icon: ShoppingCart,    modulo: 'PDV',            cor: 'bg-talho-600' },
  { label: 'Sangria / Suprimento', href: '/pdv/sangria',               icon: ArrowDownCircle, modulo: 'SANGRIA',        cor: 'bg-red-500' },
  { label: 'Produtos',             href: '/estoque/produtos',          icon: Package,         modulo: 'PRODUTOS',       cor: 'bg-blue-600' },
  { label: 'Recebimento',          href: '/estoque/recebimento',       icon: Truck,           modulo: 'RECEBIMENTO',    cor: 'bg-blue-500' },
  { label: 'Fichas Desossa',       href: '/estoque/fichas-desossa',    icon: ClipboardList,   modulo: 'FICHAS_DESOSSA', cor: 'bg-indigo-500' },
  { label: 'Rateio de Desossa',    href: '/estoque/desossa',           icon: Scissors,        modulo: 'RATEIO_DESOSSA', cor: 'bg-indigo-600' },
  { label: 'Inventário',           href: '/estoque/inventario',        icon: ClipboardList,   modulo: 'INVENTARIO',     cor: 'bg-cyan-600' },
  { label: 'Perdas',               href: '/estoque/perdas',            icon: AlertTriangle,   modulo: 'PERDAS',         cor: 'bg-amber-600' },
  { label: 'NF de Saída',          href: '/fiscal/notas',              icon: FileText,        modulo: 'NF_SAIDA',       cor: 'bg-slate-600' },
  { label: 'Faturamento',          href: '/financeiro/faturamento',    icon: DollarSign,      modulo: 'FATURAMENTO',    cor: 'bg-emerald-600' },
  { label: 'Contas a Receber',     href: '/financeiro/contas-receber', icon: CreditCard,      modulo: 'CONTAS_RECEBER', cor: 'bg-emerald-500' },
  { label: 'Contas a Pagar',       href: '/financeiro/contas-pagar',   icon: TrendingDown,    modulo: 'CONTAS_PAGAR',   cor: 'bg-orange-600' },
  { label: 'DRE',                  href: '/financeiro/dre',            icon: BarChart2,       modulo: 'DRE',            cor: 'bg-teal-600' },
  { label: 'Relatórios',           href: '/financeiro/relatorios',     icon: BarChart,        modulo: 'RELATORIOS',     cor: 'bg-teal-500' },
  { label: 'Comissões',            href: '/financeiro/comissoes',      icon: Percent,         modulo: 'COMISSOES',      cor: 'bg-emerald-700' },
  { label: 'Carga Balança',        href: '/balanca',                   icon: Scale,           modulo: 'CARGA_BALANCA',  cor: 'bg-violet-600' },
  { label: 'Usuários',             href: '/acesso/usuarios',           icon: Users,           modulo: 'USUARIOS',       cor: 'bg-gray-700' },
  { label: 'Perfis',               href: '/acesso/perfis',             icon: ShieldCheck,     modulo: 'PERFIS',         cor: 'bg-gray-800' },
  { label: 'Auditoria',            href: '/acesso/auditoria',          icon: FileText,     modulo: 'AUDITORIA',      cor: 'bg-talho-600' },
  { label: 'Validade',            href: '/estoque/validade',          icon: Package,     modulo: 'VALIDADE',      cor: 'bg-amber-600' },
]


function saudacao(): string {
  const h = new Date().getHours()
  if (h < 12) return 'Bom dia'
  if (h < 18) return 'Boa tarde'
  return 'Boa noite'
}

export default function HomePage() {
  const { podeVer, nome, perfil } = usePermissao()

  const disponiveis = atalhos.filter(a => podeVer(a.modulo))

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">
          {saudacao()}, {nome?.split(' ')[0] ?? 'usuário'}!
        </h1>
        <p className="text-gray-500 text-sm mt-1">
          Você está conectado como <span className="font-medium text-gray-700">{perfil}</span>.
          {' '}Estes são os módulos disponíveis para o seu perfil.
        </p>
      </div>

      {disponiveis.length === 0 ? (
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-5 max-w-lg">
          <p className="font-medium text-warning-700">Nenhum módulo liberado</p>
          <p className="text-sm text-warning-600 mt-1">
            Seu perfil ainda não tem permissão de acesso a nenhum módulo.
            Procure o administrador do sistema.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {disponiveis.map(a => {
            const Icon = a.icon
            const conteudo = (
              <>
                <div className={`${a.cor} w-12 h-12 rounded-xl flex items-center justify-center mb-3`}>
                  <Icon size={22} className="text-white" />
                </div>
                <p className="font-medium text-gray-900 text-sm">{a.label}</p>
              </>
            )
            const classe = 'bg-white rounded-2xl shadow-sm border border-gray-100 p-5 hover:shadow-md hover:border-gray-200 transition-all block'

            // PDV abre em nova aba (tela cheia, sem sidebar), igual no menu lateral
            return a.modulo === 'PDV' ? (
              <a key={a.href} href={a.href} target="_blank" rel="noreferrer" className={classe}>
                {conteudo}
              </a>
            ) : (
              <Link key={a.href} to={a.href} className={classe}>
                {conteudo}
              </Link>
            )
          })}
        </div>
      )}
    </div>
  )
}