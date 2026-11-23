import { ScanBarcode } from 'lucide-react'
import type { ItemVenda } from '@/types/venda'
import { formatBRL, formatWeightDisplay } from '@/shared/utils/mask'

interface Props {
  /** Item em destaque: o escolhido na lista ou, por padrão, o último lançado. */
  item?: ItemVenda
  /** Posição do item na comanda (1, 2, 3...). */
  numero?: number
}

const quantidade = (i: ItemVenda) =>
  i.produto.unidadeMedida === 'KG' ? `${formatWeightDisplay(i.quantidade)} kg` : `${i.quantidade} un`

/**
 * Destaque do último item lançado, como nos caixas de supermercado: nome grande e,
 * embaixo, quantidade × preço = total. Sem item, mostra o estado "caixa livre".
 */
export default function UltimoItem({ item, numero }: Props) {
  if (!item) {
    return (
      <div className="mx-4 mt-4 rounded-2xl border-2 border-dashed border-gray-300 bg-card px-6 py-8 text-center shrink-0">
        <ScanBarcode size={52} className="mx-auto text-gray-300 mb-2" aria-hidden="true" />
        <p className="text-3xl font-black tracking-wide text-gray-700">CAIXA LIVRE</p>
        <p className="text-sm text-gray-500 mt-1">Bipe o código de barras ou busque o produto <span className="font-semibold">(F2)</span></p>
      </div>
    )
  }

  return (
    <div className="mx-4 mt-4 rounded-2xl border border-border bg-card px-6 py-5 shadow-card shrink-0">
      <div className="flex items-baseline justify-between gap-3">
        <p className="text-[11px] font-semibold uppercase tracking-widest text-gray-500">Item atual</p>
        {numero ? <p className="text-xs text-gray-400 tabular-nums">Item {numero}</p> : null}
      </div>
      <p className="mt-1 text-3xl font-bold text-gray-900 truncate" title={item.produto.nome}>{item.produto.nome}</p>

      <div className="mt-4 grid grid-cols-3 gap-4 items-end">
        <div>
          <p className="text-[11px] uppercase tracking-wide text-gray-500">Quantidade</p>
          <p className="text-2xl font-semibold tabular-nums text-gray-900">{quantidade(item)}</p>
        </div>
        <div>
          <p className="text-[11px] uppercase tracking-wide text-gray-500">Preço unitário</p>
          <p className="text-2xl font-semibold tabular-nums text-danger-600">{formatBRL(item.precoUnitario)}</p>
        </div>
        <div className="text-right">
          <p className="text-[11px] uppercase tracking-wide text-gray-500">Total do item</p>
          <p className="text-4xl font-black tabular-nums text-danger-600">{formatBRL(item.totalItem)}</p>
          {item.descontoItem > 0 && <p className="text-xs text-warning-700 tabular-nums">desconto −{formatBRL(item.descontoItem)}</p>}
        </div>
      </div>
    </div>
  )
}
