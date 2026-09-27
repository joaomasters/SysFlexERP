import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: string
  subtitle?: string
  icon?: ReactNode
  actions?: ReactNode
}

/**
 * Cabeçalho padrão de todas as páginas do sistema — título (text-2xl font-bold),
 * subtítulo opcional (text-sm text-gray-500) e slot de ações à direita
 * (botões primários da tela, ex: "Novo Produto", "Gerar carga agora").
 * Modelo de referência: tela Contas a Receber.
 */
export default function PageHeader({ title, subtitle, icon, actions }: PageHeaderProps) {
  return (
    <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          {icon}
          {title}
        </h1>
        {subtitle && <p className="text-gray-500 text-sm">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}
