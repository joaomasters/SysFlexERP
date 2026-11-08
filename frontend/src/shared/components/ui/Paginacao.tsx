import { ChevronLeft, ChevronRight } from 'lucide-react'
import Button from './Button'
import { OPCOES_POR_PAGINA, MENOR_PAGINA } from '../../hooks/usePaginacao'

interface Props {
  paginacao: {
    total: number; inicio: number; paginaAtual: number; totalPaginas: number; porPagina: number
    itens: unknown[]
    irPara: (n: number) => void
    alterarPorPagina: (n: number) => void
  }
  /** Plural exibido em "de 25 ___" (ex: "fornecedores"). */
  rotulo: string
  /** Para listas de cartões (sem um Card em volta): desenha a própria caixa branca. */
  solta?: boolean
}

/** Barra de paginação padrão (visual e comportamento da tela de Produtos). */
export default function Paginacao({ paginacao: p, rotulo, solta }: Props) {
  if (p.total <= MENOR_PAGINA) return null
  return (
    <div className={`flex flex-wrap items-center justify-between gap-3 px-4 py-3 ${
      solta ? 'bg-white rounded-xl shadow border border-gray-100' : 'border-t border-gray-100'
    }`}>
      <p className="text-xs text-gray-500 tabular-nums">
        Mostrando {p.inicio + 1}–{p.inicio + p.itens.length} de {p.total} {rotulo}
      </p>
      <div className="flex items-center gap-4">
        <label className="flex items-center gap-2 text-xs text-gray-600">
          Exibir
          <select
            value={p.porPagina}
            onChange={e => p.alterarPorPagina(Number(e.target.value))}
            className="border border-gray-300 rounded-lg px-2 py-1 text-xs bg-white focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            {OPCOES_POR_PAGINA.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </label>
        {p.totalPaginas > 1 && (
          <div className="flex items-center gap-2">
            <Button
              variant="secondary" size="sm"
              onClick={() => p.irPara(p.paginaAtual - 1)}
              disabled={p.paginaAtual === 1}
              aria-label="Página anterior"
            >
              <ChevronLeft size={14} />
            </Button>
            <span className="text-xs text-gray-600 tabular-nums">
              Página {p.paginaAtual} de {p.totalPaginas}
            </span>
            <Button
              variant="secondary" size="sm"
              onClick={() => p.irPara(p.paginaAtual + 1)}
              disabled={p.paginaAtual === p.totalPaginas}
              aria-label="Próxima página"
            >
              <ChevronRight size={14} />
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}