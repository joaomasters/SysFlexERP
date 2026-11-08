import { useEffect, useState } from 'react'

/** Opções do seletor "Exibir" — 0 = todos numa página só. Mesmo padrão da tela de Produtos. */
export const OPCOES_POR_PAGINA = [
  { value: 10, label: '10' },
  { value: 25, label: '25' },
  { value: 50, label: '50' },
  { value: 0,  label: 'Todos' },
]

/** Abaixo disso a barra de paginação nem aparece. */
export const MENOR_PAGINA = 10

/**
 * Paginação no cliente (o backend devolve a lista inteira), igual à de Produtos.
 * `resetKey`: quando muda (busca, filtro, ordenação), volta para a página 1.
 * O Math.min evita ficar numa página que deixou de existir.
 */
export function usePaginacao<T>(lista: T[], resetKey: string | number = '') {
  const [pagina, setPagina] = useState(1)
  const [porPagina, setPorPagina] = useState(10)

  useEffect(() => { setPagina(1) }, [resetKey])

  const tamanho = porPagina || lista.length
  const totalPaginas = Math.max(1, Math.ceil(lista.length / (tamanho || 1)))
  const paginaAtual = Math.min(pagina, totalPaginas)
  const inicio = (paginaAtual - 1) * tamanho
  const itens = lista.slice(inicio, inicio + tamanho)

  return {
    itens, total: lista.length, inicio, paginaAtual, totalPaginas, porPagina,
    irPara: setPagina,
    alterarPorPagina: (n: number) => { setPorPagina(n); setPagina(1) },
  }
}

export type Paginacao = ReturnType<typeof usePaginacao>