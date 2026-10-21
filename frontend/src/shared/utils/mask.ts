/**
 * Utilitários de formatação e máscara de preenchimento (input mask).
 *
 * Centraliza TODA a lógica de moeda (R$) e peso (kg/g) do sistema, para que
 * nenhuma tela precise reimplementar sua própria versão — antes de existir
 * este arquivo, cada formulário tinha uma função `fmt`/`brl`/`maskCurrencyDigits`
 * ligeiramente diferente (algumas com 2 casas fixas, outras sem separador de
 * milhar, outras deixando o usuário digitar texto livre). Use sempre as
 * funções daqui.
 *
 * Estratégia de máscara: "caixa eletrônico" — o usuário só digita números e
 * eles preenchem a partir das casas decimais (da direita pra esquerda). Isso
 * evita estados intermediários inválidos (ex: "12,", "12,5,6") e é o padrão
 * mais comum em PDVs e apps bancários brasileiros.
 */

// ─── Moeda (BRL) ────────────────────────────────────────────────────────────

/** Formata um número para exibição monetária pt-BR, ex: 1234.5 -> "1.234,50" (sem símbolo). */
export function formatCurrencyDisplay(value: number): string {
  return (Number.isFinite(value) ? value : 0).toLocaleString('pt-BR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** Formata um número para exibição monetária completa, ex: 1234.5 -> "R$ 1.234,50". */
export function formatBRL(value: number): string {
  return (Number.isFinite(value) ? value : 0).toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  })
}

/**
 * Aplica a máscara "caixa eletrônico" sobre uma string digitada livremente.
 * Extrai só os dígitos e trata os 2 últimos como centavos.
 */
export function maskCurrencyDigits(raw: string): { display: string; value: number } {
  const digits = raw.replace(/\D/g, '')
  const cents = digits === '' ? 0 : parseInt(digits, 10)
  const value = cents / 100
  return { display: formatCurrencyDisplay(value), value }
}

// ─── Peso (kg / g) ──────────────────────────────────────────────────────────

/** Formata um número como peso, ex: 1.5 -> "1,500" (3 casas, padrão de balança). */
export function formatWeightDisplay(value: number, decimals = 3): string {
  return (Number.isFinite(value) ? value : 0).toLocaleString('pt-BR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
}

/** Formata peso com unidade para exibição, ex: 1.5 -> "1,500 kg". */
export function formatWeight(value: number, unit: 'kg' | 'g' = 'kg', decimals = 3): string {
  return `${formatWeightDisplay(value, decimals)} ${unit}`
}

/**
 * Aplica a máscara "caixa eletrônico" para peso. Por padrão 3 casas decimais
 * (kg com gramas de precisão — o padrão já usado nas balanças do sistema).
 */
export function maskWeightDigits(raw: string, decimals = 3): { display: string; value: number } {
  const digits = raw.replace(/\D/g, '')
  const factor = 10 ** decimals
  const units = digits === '' ? 0 : parseInt(digits, 10)
  const value = units / factor
  return { display: formatWeightDisplay(value, decimals), value }
}

// ─── Quantidade conforme a unidade de medida ────────────────────────────────

// UN (unidade) e CX (caixa) são contadas em peças inteiras: não têm fração.
// KG e G continuam com 3 casas (precisão de balança).
const UNIDADES_INTEIRAS = ['UN', 'CX', 'CAIXA']

/** true para unidades que só aceitam número inteiro (UN, CX). */
export function unidadeEhInteira(unidade?: string | null): boolean {
  return !!unidade && UNIDADES_INTEIRAS.includes(unidade.trim().toUpperCase())
}

/** Casas decimais da máscara de quantidade: 0 para UN/CX, 3 para peso. Use em <WeightInput decimals=...>. */
export function casasDaUnidade(unidade?: string | null): number {
  return unidadeEhInteira(unidade) ? 0 : 3
}

/**
 * Formata uma quantidade para exibição conforme a unidade: UN/CX sem vírgula
 * ("2"), peso com 3 casas ("2,000"). Registro antigo de UN/CX que tenha fração
 * continua aparecendo com as casas, para a fração não ficar escondida.
 */
export function formatQuantidade(value: number, unidade?: string | null): string {
  const n = Number.isFinite(value) ? value : 0
  if (unidadeEhInteira(unidade) && Number.isInteger(n)) return formatWeightDisplay(n, 0)
  return formatWeightDisplay(n, 3)
}

// ─── Genérico ───────────────────────────────────────────────────────────────

/** Formata percentual, ex: 12.5 -> "12,5%". */
export function formatPercent(value: number, decimals = 1): string {
  return `${(Number.isFinite(value) ? value : 0).toLocaleString('pt-BR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })}%`
}