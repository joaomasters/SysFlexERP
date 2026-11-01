import { Percent, Trash2 } from 'lucide-react'
import type { ItemVenda } from '@/types/venda'
import { formatBRL, formatWeightDisplay } from '@/shared/utils/mask'

interface Props {
  itens: ItemVenda[]
  onRemover: (id: number) => void
  onDesconto: (item: ItemVenda) => void
  /** Sem a permissão, o botão fica visível e desabilitado, como o de cancelar venda. */
  podeDescontar: boolean
  /** Item em que os atalhos F6 e F8 agem. */
  selecionadoId?: number
  onSelecionar: (id: number) => void
}

// Tema escuro proposital — console de operação do caixa (PDV), separado do
// tema claro usado no back-office. Ver nota em PDVPage.tsx.
const brl = formatBRL
const kg3 = formatWeightDisplay

const tecla = 'rounded bg-black/30 px-1 font-mono text-[10px] leading-4'

export default function ListaItens({ itens, onRemover, onDesconto, podeDescontar, selecionadoId, onSelecionar }: Props) {
  if (itens.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-500 text-sm">
        Bipe o código de barras ou busque o produto acima (F2)
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto scrollbar-thin">
      <table className="w-full text-sm">
        <thead className="sticky top-0 bg-gray-800 text-gray-400 text-xs uppercase">
          <tr>
            <th className="px-4 py-2 text-left">#</th>
            <th className="px-4 py-2 text-left">Produto</th>
            <th className="px-4 py-2 text-right">Qtd</th>
            <th className="px-4 py-2 text-right">Preço/Kg</th>
            <th className="px-4 py-2 text-right">Total</th>
            <th className="px-2 py-2"></th>
          </tr>
        </thead>
        <tbody>
          {itens.map((item, idx) => {
            const selecionado = item.id === selecionadoId
            return (
            <tr
              key={item.id}
              ref={selecionado ? el => el?.scrollIntoView({ block: 'nearest' }) : undefined}
              onClick={() => onSelecionar(item.id)}
              aria-selected={selecionado}
              className={`border-b border-gray-800 cursor-pointer transition-colors
                ${selecionado ? 'bg-emerald-900/25 shadow-[inset_3px_0_0_0_#34d399]' : 'hover:bg-gray-800/50'}`}
            >
              <td className="px-4 py-3 text-gray-500">{idx + 1}</td>
              <td className="px-4 py-3 font-medium">{item.produto.nome}</td>
              <td className="px-4 py-3 text-right tabular-nums text-gray-300">
                {item.produto.unidadeMedida === 'KG'
                  ? `${kg3(item.quantidade)} kg`
                  : `${item.quantidade} un`}
              </td>
              <td className="px-4 py-3 text-right tabular-nums text-gray-300">
                {brl(item.precoUnitario)}
              </td>
              <td className="px-4 py-3 text-right tabular-nums font-bold text-emerald-400">
                {brl(item.totalItem)}
                {item.descontoItem > 0 && (
                  <span className="block text-xs font-normal text-amber-400">-{brl(item.descontoItem)}</span>
                )}
              </td>
              <td className="px-3 py-2 whitespace-nowrap text-right space-x-2">
                <button
                  onClick={() => onDesconto(item)}
                  disabled={!podeDescontar}
                  aria-label="Desconto no item"
                  title={podeDescontar ? 'Desconto no item' : 'Seu perfil não pode dar desconto'}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-amber-500/50 bg-amber-500/15 px-2.5 py-1.5
                             text-xs font-semibold text-amber-300 hover:bg-amber-500/30 transition-colors
                             disabled:border-gray-700 disabled:bg-transparent disabled:text-gray-600 disabled:cursor-not-allowed"
                >
                  <Percent size={13} /> Desconto
                  {selecionado && podeDescontar && <kbd className={tecla}>F6</kbd>}
                </button>
                <button
                  onClick={() => onRemover(item.id)}
                  aria-label="Excluir item"
                  title="Excluir item"
                  className="inline-flex items-center gap-1.5 rounded-lg border border-red-500/50 bg-red-500/15 px-2.5 py-1.5
                             text-xs font-semibold text-red-300 hover:bg-red-500/30 transition-colors"
                >
                  <Trash2 size={13} /> Excluir
                  {selecionado && <kbd className={tecla}>F8</kbd>}
                </button>
              </td>
            </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
