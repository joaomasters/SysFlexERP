// Planilha fiscal de produtos (CSV): gera a planilha com todos os produtos e lê a que volta preenchida.
import type { Produto } from '@/types/produto'

export interface LinhaPlanilha {
  linha: number
  codigoInterno: string
  descricao: string
  ncm: string
  cest: string
  cfop: string
  origem: string
  csosn: string
}

export const COLUNAS = ['codigo_interno', 'codigo_barras', 'descricao', 'ncm', 'cest', 'cfop', 'origem', 'csosn'] as const

/** Falta algo para o produto sair em nota fiscal. */
export const semFiscal = (p: Produto) => !p.ncm || !p.cfop || !p.csosn

function celula(v: string) {
  return /[;"\r\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v
}

/**
 * Número com zero à esquerda vai como ="0201..." para o Excel não tirar o zero nem virar 7,89E+12;
 * na volta, o sistema aproveita só os dígitos.
 */
const texto = (v?: string | null) => (v ? `="${v}"` : '')

export function gerarCsv(produtos: Produto[]): string {
  const linhas = produtos.map(p => [
    celula(p.codigoInterno), texto(p.ean13), celula(p.nome), texto(p.ncm), texto(p.cest),
    p.cfop ?? '', p.origem ?? '0', p.csosn ?? '',
  ].join(';'))
  return '﻿' + [COLUNAS.join(';'), ...linhas].join('\r\n')
}

export function baixarCsv(conteudo: string, nome: string) {
  const url = URL.createObjectURL(new Blob([conteudo], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = nome
  a.click()
  URL.revokeObjectURL(url)
}

/** CSV salvo pelo Excel em português vem em Windows-1252; o "CSV UTF-8" vem em UTF-8. */
export async function lerArquivo(arquivo: File): Promise<string> {
  const bytes = await arquivo.arrayBuffer()
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes).replace(/^﻿/, '')
  } catch {
    return new TextDecoder('windows-1252').decode(bytes)
  }
}

function separarLinhas(texto: string, sep: string): string[][] {
  const linhas: string[][] = []
  let linha: string[] = []
  let campo = ''
  let aspas = false
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i]
    if (aspas) {
      if (c === '"' && texto[i + 1] === '"') { campo += '"'; i++ }
      else if (c === '"') aspas = false
      else campo += c
    } else if (c === '"') aspas = true
    else if (c === sep) { linha.push(campo); campo = '' }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && texto[i + 1] === '\n') i++
      linha.push(campo); campo = ''
      linhas.push(linha); linha = []
    } else campo += c
  }
  if (campo !== '' || linha.length) { linha.push(campo); linhas.push(linha) }
  return linhas.filter(l => l.some(c => c.trim() !== ''))
}

const normalizar = (s: string) =>
  s.normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase().replace(/[\s.-]+/g, '_')

const APELIDOS: Record<string, keyof Omit<LinhaPlanilha, 'linha'>> = {
  codigo_interno: 'codigoInterno', codigo: 'codigoInterno', cod: 'codigoInterno', cod_interno: 'codigoInterno',
  descricao: 'descricao', descricao_resumida: 'descricao', nome: 'descricao', produto: 'descricao',
  ncm: 'ncm', cest: 'cest', cfop: 'cfop', origem: 'origem', csosn: 'csosn',
}

/** Lê a planilha; erro com mensagem clara se faltar a coluna do código. */
export function lerCsv(texto: string): LinhaPlanilha[] {
  const primeira = texto.split(/\r?\n/, 1)[0] ?? ''
  const sep = [';', '\t', ','].reduce((melhor, s) => primeira.split(s).length > primeira.split(melhor).length ? s : melhor, ';')
  const [cabecalho, ...dados] = separarLinhas(texto, sep)
  if (!cabecalho) throw new Error('A planilha está vazia.')
  const indices = cabecalho.map(h => APELIDOS[normalizar(h)])
  if (!indices.includes('codigoInterno')) {
    throw new Error('A planilha precisa da coluna "codigo_interno". Use a planilha baixada aqui como modelo.')
  }
  const pegar = (l: string[], campo: keyof Omit<LinhaPlanilha, 'linha'>) => {
    const i = indices.indexOf(campo)
    return i >= 0 ? (l[i] ?? '').trim() : ''
  }
  return dados.map((l, i) => ({
    linha: i + 2,
    codigoInterno: pegar(l, 'codigoInterno').replace(/^="?|"$/g, ''),
    descricao: pegar(l, 'descricao'),
    ncm: pegar(l, 'ncm'),
    cest: pegar(l, 'cest'),
    cfop: pegar(l, 'cfop'),
    origem: pegar(l, 'origem'),
    csosn: pegar(l, 'csosn'),
  }))
}
