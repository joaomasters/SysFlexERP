import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { X, Printer, Search, AlertTriangle } from 'lucide-react'
import { api } from '@/shared/api/axios'
import type { Produto } from '@/types/produto'
import { formatWeightDisplay } from '@/shared/utils/mask'
import { Button, baseInputClass } from '@/shared/components/ui'

type OrdemCampo = 'nome' | 'marca' | 'fornecedor'

interface Props {
  onClose: () => void
}

const qtd = formatWeightDisplay

/**
 * Relatório de produtos/estoque, pensado pra impressão em papel: busca livre
 * (nome, marca ou fornecedor) + ordenação por Nome/Marca/Fornecedor. Usa a
 * técnica padrão de "print isolado" — na hora de imprimir, tudo que não está
 * dentro de #relatorio-estoque-print fica oculto, então só a tabela vai pro
 * papel (sem o overlay escuro, cabeçalho do modal, botões, etc).
 */
export default function RelatorioEstoqueModal({ onClose }: Props) {
  const [busca, setBusca]         = useState('')
  const [ordenarPor, setOrdenarPor] = useState<OrdemCampo>('nome')
  const [apenasAbaixoMinimo, setApenasAbaixoMinimo] = useState(false)

  const { data: produtos = [], isLoading } = useQuery<Produto[]>({
    queryKey: ['produtos-relatorio'],
    queryFn: () => api.get('/estoque/produtos').then(r => r.data),
  })

  const linhas = useMemo(() => {
    const buscaLower = busca.trim().toLowerCase()
    let filtrados = produtos.filter(p => p.ativo)

    if (buscaLower) {
      filtrados = filtrados.filter(p =>
        p.nome.toLowerCase().includes(buscaLower) ||
        (p.marca ?? '').toLowerCase().includes(buscaLower) ||
        (p.fornecedor ?? '').toLowerCase().includes(buscaLower)
      )
    }

    if (apenasAbaixoMinimo) {
      filtrados = filtrados.filter(p => p.estoqueAtual <= p.estoqueMinimo)
    }

    return [...filtrados].sort((a, b) => {
      const va = (a[ordenarPor] ?? '').toString().toLowerCase()
      const vb = (b[ordenarPor] ?? '').toString().toLowerCase()
      // Produtos sem marca/fornecedor vão pro fim da lista, não pro topo
      if (!va && vb) return 1
      if (va && !vb) return -1
      return va.localeCompare(vb, 'pt-BR')
    })
  }, [produtos, busca, ordenarPor, apenasAbaixoMinimo])

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4 print:static print:bg-white print:p-0">
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #relatorio-estoque-print, #relatorio-estoque-print * { visibility: visible; }
          #relatorio-estoque-print { position: absolute; top: 0; left: 0; width: 100%; }
        }
      `}</style>

      <div className="bg-white rounded-2xl w-full max-w-4xl h-[85vh] flex flex-col shadow-xl print:h-auto print:shadow-none print:rounded-none print:max-w-full">
        {/* Cabeçalho — some na impressão */}
        <div className="flex items-center justify-between px-6 py-4 border-b print:hidden">
          <div>
            <h2 className="font-bold text-gray-900">Relatório de Estoque</h2>
            <p className="text-xs text-gray-400">{linhas.length} produto(s) ativo(s)</p>
          </div>
          <button onClick={onClose} aria-label="Fechar">
            <X size={20} className="text-gray-400 hover:text-gray-600" />
          </button>
        </div>

        {/* Filtros — somem na impressão */}
        <div className="flex flex-wrap items-end gap-3 px-6 py-3 border-b bg-gray-50 print:hidden">
          <div className="relative flex-1 min-w-[200px]">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={busca}
              onChange={e => setBusca(e.target.value)}
              placeholder="Buscar por nome, marca ou fornecedor..."
              className={`${baseInputClass} pl-8 py-1.5 text-sm`}
            />
          </div>
          <div>
            <label className="text-[11px] text-gray-500 block mb-1">Ordenar por</label>
            <select
              value={ordenarPor}
              onChange={e => setOrdenarPor(e.target.value as OrdemCampo)}
              className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm bg-white"
            >
              <option value="nome">Nome do produto</option>
              <option value="marca">Marca</option>
              <option value="fornecedor">Fornecedor</option>
            </select>
          </div>
          <label className="flex items-center gap-1.5 text-xs text-gray-600 pb-2 cursor-pointer select-none">
            <input type="checkbox" checked={apenasAbaixoMinimo} onChange={e => setApenasAbaixoMinimo(e.target.checked)} />
            Só abaixo do mínimo
          </label>
          <Button variant="primary" size="sm" onClick={() => window.print()} className="ml-auto">
            <Printer size={14} /> Imprimir
          </Button>
        </div>

        {/* Área impressa */}
        <div id="relatorio-estoque-print" className="flex-1 overflow-y-auto p-6 print:overflow-visible print:p-8">
          {/* Cabeçalho só visível na impressão */}
          <div className="hidden print:block mb-4">
            <h1 className="text-xl font-bold text-gray-900">Relatório de Estoque</h1>
            <p className="text-xs text-gray-500">
              Gerado em {new Date().toLocaleString('pt-BR')}
              {busca && ` — filtro: "${busca}"`}
              {' '}— ordenado por {ordenarPor === 'nome' ? 'nome' : ordenarPor === 'marca' ? 'marca' : 'fornecedor'}
            </p>
          </div>

          {isLoading ? (
            <p className="text-sm text-gray-400">Carregando...</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-gray-500 uppercase tracking-wide border-b-2 border-gray-300">
                  <th className="text-left py-2 pr-3">Nome</th>
                  <th className="text-left py-2 pr-3">Marca</th>
                  <th className="text-left py-2 pr-3">Fornecedor</th>
                  <th className="text-right py-2 pr-3">Estoque Atual</th>
                  <th className="text-right py-2">Estoque Mín.</th>
                </tr>
              </thead>
              <tbody>
                {linhas.map(p => {
                  const abaixo = p.estoqueAtual <= p.estoqueMinimo
                  return (
                    <tr key={p.id} className="border-b border-gray-100">
                      <td className="py-2 pr-3 font-medium text-gray-800">
                        <span className="flex items-center gap-1.5">
                          {abaixo && <AlertTriangle size={12} className="text-warning-500 print:hidden" />}
                          {p.nome}
                        </span>
                      </td>
                      <td className="py-2 pr-3 text-gray-600">{p.marca || '—'}</td>
                      <td className="py-2 pr-3 text-gray-600">{p.fornecedor || '—'}</td>
                      <td className={`py-2 pr-3 text-right tabular-nums ${abaixo ? 'text-warning-700 font-semibold' : 'text-gray-700'}`}>
                        {qtd(p.estoqueAtual)} {p.unidadeMedida}
                      </td>
                      <td className="py-2 text-right tabular-nums text-gray-500">
                        {qtd(p.estoqueMinimo)} {p.unidadeMedida}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}

          {!isLoading && linhas.length === 0 && (
            <p className="text-center text-gray-400 text-sm py-8">Nenhum produto encontrado.</p>
          )}
        </div>
      </div>
    </div>
  )
}