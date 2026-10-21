import { useEffect, useMemo, useRef, useState } from 'react'
import { Search, X } from 'lucide-react'

export interface ProdutoBuscavel {
  id: number
  nome: string
  unidadeMedida?: string
  codigoInterno?: string | null
  ean13?: string | null
  codigoBalanca?: number | null
  precoVenda?: number | null
}

interface Props {
  produtos: ProdutoBuscavel[]
  /** Produto selecionado (0 = nenhum). Em modo "acao" fica sempre vazio. */
  value?: number
  onSelect: (produto: ProdutoBuscavel) => void
  /** "selecao": mostra o produto escolhido no campo. "acao": limpa depois de escolher (barra de bipagem). */
  modo?: 'selecao' | 'acao'
  placeholder?: string
  invalido?: boolean
  autoFocus?: boolean
  inputRef?: React.Ref<HTMLInputElement>
  /** Chamado quando Enter é pressionado com um código que não corresponde a nenhum produto. */
  onCodigoNaoEncontrado?: (codigo: string) => void
  /**
   * Se informado, um código numérico (8 a 14 dígitos) + Enter vai direto para cá em vez de
   * ser procurado na lista — ex.: PDV, onde o servidor lê peso/preço da etiqueta de balança.
   */
  aoDigitarCodigo?: (codigo: string) => void
  /** Texto à direita de cada produto na lista (padrão: código de barras ou código interno). */
  detalhe?: (produto: ProdutoBuscavel) => string
  /** Tema escuro (console do PDV). */
  escuro?: boolean
  className?: string
}

const MAX_RESULTADOS = 50

/** minúsculas e sem acento: "Limão" → "limao" */
export const normalizar = (s: string) =>
  s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim()

/**
 * Acha o produto de um código lido/digitado: EAN cadastrado, código interno,
 * ou etiqueta de balança (EAN-13 começando com 2: dígitos 2-6 = código da balança).
 */
export function produtoPorCodigo(produtos: ProdutoBuscavel[], codigo: string): ProdutoBuscavel | undefined {
  const c = codigo.trim()
  if (!c) return undefined
  const porEan = produtos.find(p => p.ean13 && p.ean13 === c)
  if (porEan) return porEan
  const porInterno = produtos.find(p => p.codigoInterno && p.codigoInterno.toLowerCase() === c.toLowerCase())
  if (porInterno) return porInterno
  if (/^2\d{12}$/.test(c)) {
    const plu = parseInt(c.substring(1, 6), 10)
    return produtos.find(p => p.codigoBalanca === plu)
  }
  return undefined
}

/** Filtra por nome (todas as palavras, sem acento), código interno ou início do código de barras. */
export function filtrarProdutos(produtos: ProdutoBuscavel[], termo: string): ProdutoBuscavel[] {
  const t = normalizar(termo)
  if (!t) return produtos.slice(0, MAX_RESULTADOS)
  const palavras = t.split(/\s+/)
  const soDigitos = /^\d+$/.test(t)
  const resultado: ProdutoBuscavel[] = []
  for (const p of produtos) {
    const nome = normalizar(p.nome)
    const casaNome = palavras.every(w => nome.includes(w))
    const casaCodigo = (p.codigoInterno && normalizar(p.codigoInterno).includes(t))
      || (soDigitos && p.ean13 && p.ean13.startsWith(t))
    if (casaNome || casaCodigo) resultado.push(p)
    if (resultado.length >= MAX_RESULTADOS) break
  }
  return resultado
}

/**
 * Campo de busca de produto com lista filtrada (substitui o <select> longo).
 * Teclado: setas navegam, Enter escolhe, Esc fecha. Um código de barras
 * digitado ou bipado + Enter escolhe o produto direto.
 */
export default function BuscaProduto({
  produtos, value = 0, onSelect, modo = 'selecao', placeholder = 'Buscar produto por nome ou código...',
  invalido, autoFocus, inputRef, onCodigoNaoEncontrado, aoDigitarCodigo, detalhe, escuro = false, className = '',
}: Props) {
  const selecionado = modo === 'selecao' ? produtos.find(p => p.id === value) : undefined
  const [termo, setTermo] = useState('')
  const [aberto, setAberto] = useState(false)
  const [ativo, setAtivo] = useState(0)
  const caixaRef = useRef<HTMLDivElement>(null)
  const listaRef = useRef<HTMLUListElement>(null)

  const resultados = useMemo(() => filtrarProdutos(produtos, termo), [produtos, termo])

  // Fecha ao clicar fora
  useEffect(() => {
    if (!aberto) return
    const fora = (e: MouseEvent) => {
      if (caixaRef.current && !caixaRef.current.contains(e.target as Node)) {
        setAberto(false)
        setTermo('')
      }
    }
    document.addEventListener('mousedown', fora)
    return () => document.removeEventListener('mousedown', fora)
  }, [aberto])

  // Mantém o item destacado visível ao navegar com as setas
  useEffect(() => {
    listaRef.current?.children[ativo]?.scrollIntoView({ block: 'nearest' })
  }, [ativo])

  function escolher(p: ProdutoBuscavel) {
    onSelect(p)
    setTermo('')
    setAberto(false)
    setAtivo(0)
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault(); setAberto(true); setAtivo(i => Math.min(i + 1, resultados.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault(); setAtivo(i => Math.max(i - 1, 0))
    } else if (e.key === 'Escape') {
      setAberto(false); setTermo('')
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (aoDigitarCodigo && /^\d{8,14}$/.test(termo.trim())) {
        aoDigitarCodigo(termo.trim())
        setTermo(''); setAberto(false)
        return
      }
      // Código exato (bipado ou digitado) tem prioridade sobre a lista filtrada
      const porCodigo = produtoPorCodigo(produtos, termo)
      if (porCodigo) { escolher(porCodigo); return }
      if (/^\d{8,14}$/.test(termo.trim()) && resultados.length === 0) {
        onCodigoNaoEncontrado?.(termo.trim())
        setTermo('')
        return
      }
      if (aberto && resultados[ativo]) escolher(resultados[ativo])
    }
  }

  const textoCampo = aberto || termo ? termo : (selecionado?.nome ?? '')

  return (
    <div ref={caixaRef} className={`relative ${className}`}>
      <div className="relative">
        <Search size={14} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
        <input
          ref={inputRef}
          type="text"
          value={textoCampo}
          autoFocus={autoFocus}
          placeholder={selecionado ? selecionado.nome : placeholder}
          onFocus={() => { setAberto(true); setAtivo(0) }}
          onChange={e => { setTermo(e.target.value); setAberto(true); setAtivo(0) }}
          onKeyDown={onKeyDown}
          role="combobox"
          aria-expanded={aberto}
          aria-autocomplete="list"
          autoComplete="off"
          className={`w-full border rounded pl-7 pr-7 py-1.5 text-sm focus:outline-none focus:ring-2
            ${escuro
              ? 'bg-gray-800 text-white placeholder-gray-500 focus:ring-emerald-500'
              : 'bg-white focus:ring-primary-500'}
            ${invalido ? 'border-danger-500' : escuro ? 'border-gray-600' : 'border-gray-300'}`}
        />
        {modo === 'selecao' && selecionado && !aberto && (
          <button
            type="button"
            tabIndex={-1}
            onClick={() => { setTermo(''); setAberto(true) }}
            className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
            title="Trocar produto"
          >
            <X size={13} />
          </button>
        )}
      </div>

      {aberto && (
        <ul
          ref={listaRef}
          role="listbox"
          className={`absolute z-30 mt-1 w-full max-h-64 overflow-y-auto border rounded-lg shadow-lg text-sm
            ${escuro ? 'bg-gray-900 border-gray-700' : 'bg-white border-gray-200'}`}
        >
          {resultados.length === 0 ? (
            <li className={`px-3 py-2 ${escuro ? 'text-gray-500' : 'text-gray-400'}`}>Nenhum produto encontrado</li>
          ) : resultados.map((p, i) => (
            <li
              key={p.id}
              role="option"
              aria-selected={i === ativo}
              onMouseDown={e => { e.preventDefault(); escolher(p) }}
              onMouseEnter={() => setAtivo(i)}
              className={`px-3 py-1.5 cursor-pointer flex items-baseline justify-between gap-3
                ${escuro
                  ? (i === ativo ? 'bg-emerald-800 text-white' : 'text-gray-200')
                  : (i === ativo ? 'bg-primary-50 text-primary-700' : 'text-gray-700')}`}
            >
              <span className="truncate">{p.nome}</span>
              <span className={`shrink-0 text-[11px] font-mono tabular-nums ${escuro ? 'text-gray-400' : 'text-gray-400'}`}>
                {detalhe ? detalhe(p) : (p.ean13 || p.codigoInterno || '')}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
