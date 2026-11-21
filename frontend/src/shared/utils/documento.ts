/*
 CPF e CNPJ do cadastro (clientes, fornecedores, emitente) — mesma regra do backend
 (DocumentosFiscais.java). O CNPJ pode ser alfanumérico (atribuído pela Receita a partir de
 jul/2026): 12 caracteres [0-9A-Z] + 2 dígitos verificadores; no cálculo, cada caractere vale
 o código ASCII menos 48, o que dá o resultado de sempre para os CNPJs só de números.
*/
export type TipoPessoa = 'PF' | 'PJ'

export const soDigitos = (v: string) => v.replace(/\D/g, '')

/** CPF: só dígitos. CNPJ: letras e dígitos, em maiúsculas (sem pontos, barra e traço). */
export function normalizarDocumento(v: string, tipo: TipoPessoa): string {
  return tipo === 'PF' ? soDigitos(v) : v.toUpperCase().replace(/[^0-9A-Z]/g, '')
}

export function formatarCpfCnpj(v: string, tipo: TipoPessoa): string {
  const d = normalizarDocumento(v, tipo).slice(0, tipo === 'PF' ? 11 : 14)
  if (tipo === 'PF') {
    return d
      .replace(/^(\d{3})(\d)/, '$1.$2')
      .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
      .replace(/\.(\d{3})(\d)/, '.$1-$2')
  }
  const partes = [d.slice(0, 2), d.slice(2, 5), d.slice(5, 8), d.slice(8, 12), d.slice(12, 14)]
  let r = partes[0]
  if (partes[1]) r += '.' + partes[1]
  if (partes[2]) r += '.' + partes[2]
  if (partes[3]) r += '/' + partes[3]
  if (partes[4]) r += '-' + partes[4]
  return r
}

export function cpfValido(v: string): boolean {
  const d = soDigitos(v)
  if (d.length !== 11 || /^(\d)\1+$/.test(d)) return false
  const dv = (qtd: number) => {
    let s = 0
    for (let i = 0; i < qtd; i++) s += Number(d[i]) * (qtd + 1 - i)
    const r = s % 11
    return r < 2 ? 0 : 11 - r
  }
  return dv(9) === Number(d[9]) && dv(10) === Number(d[10])
}

export function cnpjValido(v: string): boolean {
  const c = normalizarDocumento(v, 'PJ')
  if (!/^[0-9A-Z]{12}[0-9]{2}$/.test(c) || /^(.)\1{13}$/.test(c)) return false
  const dv = (tamanho: number) => {
    let soma = 0
    let peso = 2
    for (let i = tamanho - 1; i >= 0; i--) {
      soma += (c.charCodeAt(i) - 48) * peso
      peso = peso === 9 ? 2 : peso + 1
    }
    const r = soma % 11
    return r < 2 ? 0 : 11 - r
  }
  return dv(12) === Number(c[12]) && dv(13) === Number(c[13])
}

export function documentoValido(v: string, tipo: TipoPessoa): boolean {
  return tipo === 'PF' ? cpfValido(v) : cnpjValido(v)
}

/** Tamanho esperado sem máscara: 11 (CPF) ou 14 (CNPJ). */
export const tamanhoDocumento = (tipo: TipoPessoa) => (tipo === 'PF' ? 11 : 14)
