import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, X } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { baseInputClass } from './Field'
import { normalizarBusca } from './CampoBusca'
import type { OpcaoFornecedor } from '@/types/fornecedor'

interface Props {
  /** Nome do fornecedor escolhido ('' = nenhum). */
  value: string
  onSelect: (nome: string) => void
  invalido?: boolean
  autoFocus?: boolean
}

/**
 * Campo de fornecedor com busca na base de cadastro (mesmo padrão do BuscaProduto).
 * Teclado: setas navegam, Enter escolhe, Esc fecha. Só aceita fornecedores cadastrados;
 * um nome antigo que não está mais na lista (entrada já gravada) continua visível até ser trocado.
 */
export default function BuscaFornecedor({ value, onSelect, invalido, autoFocus }: Props) {
  const { data: fornecedores = [], isLoading, isError } = useQuery<OpcaoFornecedor[]>({
    queryKey: ['fornecedores-opcoes'],
    queryFn: () => api.get('/estoque/fornecedores-opcoes').then(r => r.data),
  })

  const [termo, setTermo] = useState('')
  const [aberto, setAberto] = useState(false)
  const [ativo, setAtivo] = useState(0)
  const caixaRef = useRef<HTMLDivElement>(null)
  const listaRef = useRef<HTMLUListElement>(null)

  const resultados = useMemo(() => {
    const t = normalizarBusca(termo)
    const lista = [...fornecedores].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
    if (!t) return lista.slice(0, 50)
    const palavras = t.split(/\s+/)
    return lista.filter(f => { const n = normalizarBusca(f.nome); return palavras.every(w => n.includes(w)) }).slice(0, 50)
  }, [fornecedores, termo])

  useEffect(() => {
    if (!aberto) return
    const fora = (e: MouseEvent) => {
      if (caixaRef.current && !caixaRef.current.contains(e.target as Node)) { setAberto(false); setTermo('') }
    }
    document.addEventListener('mousedown', fora)
    return () => document.removeEventListener('mousedown', fora)
  }, [aberto])

  useEffect(() => { listaRef.current?.children[ativo]?.scrollIntoView({ block: 'nearest' }) }, [ativo])

  function escolher(f: OpcaoFornecedor) {
    onSelect(f.nome); setTermo(''); setAberto(false); setAtivo(0)
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') { e.preventDefault(); setAberto(true); setAtivo(i => Math.min(i + 1, resultados.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setAtivo(i => Math.max(i - 1, 0)) }
    else if (e.key === 'Escape') { setAberto(false); setTermo('') }
    else if (e.key === 'Enter') { e.preventDefault(); if (aberto && resultados[ativo]) escolher(resultados[ativo]) }
  }

  // Sem permissão para ler o cadastro (ou erro de rede): não trava o recebimento, volta ao texto livre.
  if (isError) {
    return (
      <input value={value} onChange={e => onSelect(e.target.value)} autoFocus={autoFocus}
        className={`${baseInputClass} ${invalido ? 'border-danger-500' : ''}`} placeholder="Nome do fornecedor" />
    )
  }

  const textoCampo = aberto || termo ? termo : value

  return (
    <div ref={caixaRef} className="relative">
      <div className="relative">
        <Search size={16} aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
        <input
          type="text"
          value={textoCampo}
          autoFocus={autoFocus}
          placeholder={isLoading ? 'Carregando fornecedores...' : 'Buscar fornecedor cadastrado...'}
          onFocus={() => { setAberto(true); setAtivo(0) }}
          onChange={e => { setTermo(e.target.value); setAberto(true); setAtivo(0) }}
          onKeyDown={onKeyDown}
          role="combobox"
          aria-expanded={aberto}
          aria-autocomplete="list"
          autoComplete="off"
          className={`${baseInputClass} pl-9 pr-9 ${invalido ? 'border-danger-500' : ''}`}
        />
        {value && !aberto && (
          <button type="button" tabIndex={-1} onClick={() => { setTermo(''); setAberto(true) }}
            className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-gray-400 hover:text-gray-600" title="Trocar fornecedor">
            <X size={14} />
          </button>
        )}
      </div>

      {aberto && (
        <ul ref={listaRef} role="listbox"
          className="absolute z-30 mt-1 w-full max-h-64 overflow-y-auto border border-gray-200 rounded-lg shadow-flutuante text-sm bg-card">
          {resultados.length === 0 ? (
            <li className="px-3 py-2 text-gray-400">
              Nenhum fornecedor encontrado. Cadastre em Fornecedores.
            </li>
          ) : resultados.map((f, i) => (
            <li key={f.id} role="option" aria-selected={i === ativo}
              onMouseDown={e => { e.preventDefault(); escolher(f) }}
              onMouseEnter={() => setAtivo(i)}
              className={`px-3 py-1.5 cursor-pointer ${i === ativo ? 'bg-primary-50 text-primary-700' : 'text-gray-700'}`}>
              {f.nome}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
