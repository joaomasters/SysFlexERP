// Margem de lucro do produto — compartilhado entre a listagem e o cadastro.

/** Margem sobre o preço de venda, em %. null quando não há custo ou venda para calcular. */
export function margemPct(custo?: number | null, venda?: number | null): number | null {
  if (!custo || custo <= 0 || !venda || venda <= 0) return null
  return ((venda - custo) / venda) * 100
}

/** Preço de venda que dá esta margem sobre a venda (inverso de margemPct). Null se não dá para calcular. */
export function precoPorMargem(custo: number, margem: number): number | null {
  if (!(custo > 0) || !(margem >= 0) || margem >= 100) return null
  return Math.round((custo / (1 - margem / 100)) * 100) / 100
}

/** Cor da margem: abaixo de 15% é ruim, abaixo de 30% pede atenção. */
export function corMargem(margem: number) {
  if (margem < 15) return 'text-danger-600'
  if (margem < 30) return 'text-warning-600'
  return 'text-success-600'
}
