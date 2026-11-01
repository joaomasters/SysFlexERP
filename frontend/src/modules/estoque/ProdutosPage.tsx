import { useMemo, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Search, AlertTriangle, Pencil, Trash2, Printer } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import { nomeTipoProduto } from '@/types/produto'
import type { Produto } from '@/types/produto'
import ProdutoForm from './components/ProdutoForm'
import RelatorioEstoqueModal from './components/RelatorioEstoqueModal'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { useBarcodeScan } from '@/shared/hooks/useBarcodeScan'
import { formatBRL, formatQuantidade } from '@/shared/utils/mask'
import {
  PageHeader, Button, StatusBadge, baseInputClass, ConfirmDialog,
  Table, THead, TH, TBody, TR, TD, LoadingState, filtrarProdutos, produtoPorCodigo,
} from '@/shared/components/ui'

const brl  = (v?: number) => formatBRL(v ?? 0)
const qtd3 = formatQuantidade

function margemPct(custo?: number, venda?: number): number | null {
  if (!custo || custo <= 0 || !venda) return null
  return ((venda - custo) / venda) * 100
}

export default function ProdutosPage() {
  const qc      = useQueryClient()
  const { podeVerIdentificacao } = usePermissao()
  const [busca, setBusca]   = useState('')
  const [form, setForm]     = useState<Partial<Produto> | null>(null)
  const [confirmando, setConfirmando] = useState<Produto | null>(null)
  const [showRelatorio, setShowRelatorio] = useState(false)
  const buscaRef = useRef<HTMLInputElement>(null)

  const { data: produtos = [], isLoading, isError, error } = useQuery<Produto[]>({
    queryKey: ['produtos', 'cadastro'],
    queryFn: () => api.get('/estoque/produtos').then(r => r.data),
  })

  // Nome (sem acento), código interno ou código de barras; etiqueta de balança acha pelo PLU
  const lista = useMemo(() => {
    if (!busca.trim()) return produtos
    const achados = filtrarProdutos(produtos, busca, Infinity)
    if (achados.length > 0) return achados
    const porCodigo = produtoPorCodigo(produtos, busca)
    return porCodigo ? [porCodigo] : []
  }, [produtos, busca])

  // Leitor com o foco fora do campo de busca (com o foco nele, os dígitos entram direto)
  useBarcodeScan({
    enabled: form === null && !showRelatorio,
    onScan: codigo => {
      const el = document.activeElement
      if (el && ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName)) return
      setBusca(codigo)
      buscaRef.current?.focus()
      // depois de renderizar o código: selecionado, o próximo bipado substitui este
      setTimeout(() => buscaRef.current?.select())
    },
  })

  const deletar = useMutation({
    mutationFn: (id: number) => api.delete(`/estoque/produtos/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['produtos'] })
      toast.success('Produto inativado')
      setConfirmando(null)
    },
  })

  return (
    <div className="p-6">
      <PageHeader
        title="Produtos"
        subtitle="Cadastro e gestão de estoque"
        actions={
          <>
            <Button variant="secondary" onClick={() => setShowRelatorio(true)}>
              <Printer size={16} /> Relatório de Estoque
            </Button>
            <Button variant="primary" onClick={() => setForm({})}>
              Novo Produto
            </Button>
          </>
        }
      />

      {/* Busca */}
      <div className="relative mb-4">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          ref={buscaRef}
          type="text"
          placeholder="Buscar por nome, código ou código de barras..."
          value={busca}
          onChange={e => setBusca(e.target.value)}
          onFocus={e => e.target.select()}
          // seleciona o texto para o próximo código bipado substituir o anterior
          onKeyDown={e => { if (e.key === 'Enter') e.currentTarget.select() }}
          className={`${baseInputClass} pl-9 py-2.5`}
        />
      </div>

      {/* Tabela */}
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? (
          <LoadingState />
        ) : isError ? (
          <div className="p-8 text-center">
            <p className="text-danger-600 font-medium mb-1">Erro ao carregar produtos</p>
            <p className="text-gray-400 text-sm">{String((error as Error)?.message ?? 'Falha na requisição')}</p>
          </div>
        ) : lista.length === 0 ? (
          <p className="p-8 text-center text-sm text-gray-400">Nenhum produto encontrado.</p>
        ) : (
          <div className="overflow-x-auto">
          <Table>
            <THead>
              <tr>
                <TH>Código</TH>
                <TH>Nome</TH>
                <TH>Marca</TH>
                <TH>Fornecedor</TH>
                <TH>Un.</TH>
                <TH align="right">Custo Médio</TH>
                <TH align="right">Preço Venda</TH>
                <TH align="right">Margem</TH>
                <TH align="right">Estoque</TH>
                <TH align="center">PLU</TH>
                <TH align="center">Status</TH>
                {podeVerIdentificacao && <TH>Cadastrado por</TH>}
                <TH />
              </tr>
            </THead>
            <TBody>
              {lista.map(p => (
                <TR key={p.id}>
                  <TD className="font-mono text-xs text-gray-500">
                    {p.codigoInterno}
                    {p.ean13 && <div className="text-[11px] text-gray-400">{p.ean13}</div>}
                  </TD>
                  <TD className="font-medium">
                    <div className="flex items-center gap-2">
                      {p.estoqueAtual <= p.estoqueMinimo && (
                        <AlertTriangle size={14} className="text-warning-500 shrink-0" />
                      )}
                      {p.nome}
                    </div>
                    <div className="text-xs font-normal text-gray-400">{nomeTipoProduto(p.tipoProduto)}</div>
                  </TD>
                  <TD className="text-gray-500">{p.marca || '—'}</TD>
                  <TD className="text-gray-500">{p.fornecedor || '—'}</TD>
                  <TD className="text-gray-500">{p.unidadeMedida}</TD>
                  <TD align="right" className="text-gray-500 tabular-nums">
                    {p.precoCusto ? brl(p.precoCusto) : '—'}
                  </TD>
                  <TD align="right" className="font-medium tabular-nums">{brl(p.precoVenda)}</TD>
                  <TD align="right" className="tabular-nums">
                    {(() => {
                      const m = margemPct(p.precoCusto, p.precoVenda)
                      if (m === null) return <span className="text-gray-400">—</span>
                      const cor = m < 15 ? 'text-danger-600' : m < 30 ? 'text-warning-500' : 'text-success-600'
                      return <span className={`font-semibold ${cor}`}>{m.toFixed(1)}%</span>
                    })()}
                  </TD>
                  <TD align="right" className={`tabular-nums font-medium
                    ${p.estoqueAtual <= p.estoqueMinimo ? 'text-warning-600' : 'text-gray-700'}`}>
                    {qtd3(p.estoqueAtual, p.unidadeMedida)} {p.unidadeMedida}
                  </TD>
                  <TD align="center" className="text-gray-500 font-mono text-xs">
                    {p.codigoBalanca ? String(p.codigoBalanca).padStart(5, '0') : '—'}
                  </TD>
                  <TD align="center">
                    <StatusBadge tone={p.ativo ? 'success' : 'neutral'}>{p.ativo ? 'Ativo' : 'Inativo'}</StatusBadge>
                  </TD>
                  {podeVerIdentificacao && (
                    <TD className="text-gray-500 text-xs">{p.criadoPorNome ?? '—'}</TD>
                  )}
                  <TD>
                    <div className="flex items-center gap-1 justify-end">
                      <Button variant="ghost" size="sm" aria-label="Editar produto" onClick={() => setForm(p)}>
                        <Pencil size={14} />
                      </Button>
                      <Button variant="ghost" size="sm" aria-label="Inativar produto" className="hover:!text-danger-600" onClick={() => setConfirmando(p)}>
                        <Trash2 size={14} />
                      </Button>
                    </div>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
          </div>
        )}
      </div>

      {/* Confirmação de inativação */}
      {confirmando && (
        <ConfirmDialog
          title="Inativar produto?"
          message={`"${confirmando.nome}" deixará de aparecer nas vendas e listagens ativas.`}
          confirmLabel="Inativar"
          loading={deletar.isPending}
          onConfirm={() => deletar.mutate(confirmando.id)}
          onCancel={() => setConfirmando(null)}
        />
      )}

      {showRelatorio && <RelatorioEstoqueModal onClose={() => setShowRelatorio(false)} />}

      {/* Modal de formulário */}
      {form !== null && (
        <ProdutoForm
          produto={form}
          onClose={() => setForm(null)}
          onSaved={() => {
            qc.invalidateQueries({ queryKey: ['produtos'] })
            setForm(null)
          }}
        />
      )}
    </div>
  )
}