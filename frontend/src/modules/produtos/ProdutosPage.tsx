import { useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Search, X, AlertTriangle, Pencil, Trash2, ChevronLeft, ChevronRight,
  ArrowUp, ArrowDown, ArrowUpDown, Printer,
} from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import { nomeTipoProduto } from '@/types/produto'
import type { Produto } from '@/types/produto'
import RelatorioEstoqueModal from './components/RelatorioEstoqueModal'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { useBarcodeScan } from '@/shared/hooks/useBarcodeScan'
import { formatBRL, formatQuantidade } from '@/shared/utils/mask'
import { margemPct, corMargem } from './produtoUtils'
import {
  PageHeader, Card, Button, StatusBadge, baseInputClass, ConfirmDialog,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao, filtrarProdutos, produtoPorCodigo,
} from '@/shared/components/ui'
import { usePaginacao } from '@/shared/hooks/usePaginacao'

type CampoOrdem = 'codigoInterno' | 'nome' | 'precoVenda' | 'margem' | 'estoqueAtual'
type Ordem = { campo: CampoOrdem; direcao: 'asc' | 'desc' }

function valorOrdem(p: Produto, campo: CampoOrdem): string | number | null {
  if (campo === 'margem') return margemPct(p.precoCusto, p.precoVenda)
  return p[campo]
}

function ordenar(produtos: Produto[], { campo, direcao }: Ordem): Produto[] {
  const sinal = direcao === 'asc' ? 1 : -1
  return [...produtos].sort((a, b) => {
    const va = valorOrdem(a, campo)
    const vb = valorOrdem(b, campo)
    // Sem valor (ex: margem sem custo) vai sempre para o fim, em qualquer direção.
    if (va === null) return vb === null ? 0 : 1
    if (vb === null) return -1
    if (typeof va === 'string' && typeof vb === 'string') {
      return va.localeCompare(vb, 'pt-BR', { numeric: true, sensitivity: 'base' }) * sinal
    }
    return (Number(va) - Number(vb)) * sinal
  })
}

/** Cabeçalho clicável que alterna a ordenação da coluna. */
function THOrdenavel({ campo, ordem, onOrdenar, align, children }: {
  campo: CampoOrdem
  ordem: Ordem
  onOrdenar: (campo: CampoOrdem) => void
  align?: 'left' | 'right'
  children: ReactNode
}) {
  const ativo = ordem.campo === campo
  const Icone = !ativo ? ArrowUpDown : ordem.direcao === 'asc' ? ArrowUp : ArrowDown
  return (
    <TH align={align} aria-sort={ativo ? (ordem.direcao === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button
        type="button"
        onClick={() => onOrdenar(campo)}
        className={`inline-flex items-center gap-1 uppercase tracking-wide rounded hover:text-gray-700
          focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500
          ${align === 'right' ? 'flex-row-reverse' : ''} ${ativo ? 'text-gray-700' : ''}`}
      >
        {children}
        <Icone size={12} aria-hidden="true" className={ativo ? '' : 'text-gray-300'} />
      </button>
    </TH>
  )
}

export default function ProdutosPage() {
  const qc       = useQueryClient()
  const navigate = useNavigate()
  const { podeVerIdentificacao } = usePermissao()
  const [busca, setBusca]   = useState('')
  const [termo, setTermo]   = useState('')
  const [ordem, setOrdem]   = useState<Ordem>({ campo: 'nome', direcao: 'asc' })
  const [confirmando, setConfirmando] = useState<Produto | null>(null)
  const [showRelatorio, setShowRelatorio] = useState(false)
  const buscaRef = useRef<HTMLInputElement>(null)

  // Só filtra a lista quando o usuário para de digitar.
  useEffect(() => {
    const t = setTimeout(() => setTermo(busca.trim()), 300)
    return () => clearTimeout(t)
  }, [busca])

  const { data: produtos = [], isLoading, isError, error } = useQuery<Produto[]>({
    queryKey: ['produtos', 'cadastro'],
    queryFn: () => api.get('/estoque/produtos').then(r => r.data),
  })

  // Nome (sem acento), código interno ou código de barras; etiqueta de balança acha pelo PLU
  const lista = useMemo(() => {
    if (!termo) return produtos
    const achados = filtrarProdutos(produtos, termo, Infinity)
    if (achados.length > 0) return achados
    const porCodigo = produtoPorCodigo(produtos, termo)
    return porCodigo ? [porCodigo] : []
  }, [produtos, termo])

  // Leitor com o foco fora do campo de busca (com o foco nele, os dígitos entram direto)
  useBarcodeScan({
    enabled: !showRelatorio && confirmando === null,
    onScan: codigo => {
      const el = document.activeElement
      if (el && ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName)) return
      alterarBusca(codigo)
      buscaRef.current?.focus()
      // depois de renderizar o código: selecionado, o próximo bipado substitui este
      setTimeout(() => buscaRef.current?.select())
    },
  })

  // Paginação no cliente (hook compartilhado): volta à página 1 quando busca/ordem mudam.
  const ordenados      = ordenar(lista, ordem)
  const pag            = usePaginacao(ordenados, `${busca}|${ordem.campo}|${ordem.direcao}`)
  const produtosPagina = pag.itens

  function alterarBusca(valor: string) {
    setBusca(valor)
  }

  function ordenarPor(campo: CampoOrdem) {
    setOrdem(o => ({ campo, direcao: o.campo === campo && o.direcao === 'asc' ? 'desc' : 'asc' }))
  }

  const deletar = useMutation({
    mutationFn: (id: number) => api.delete(`/estoque/produtos/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['produtos'] })
      toast.success('Produto inativado')
      setConfirmando(null)
    },
  })

  return (
    <div className="p-4 sm:p-6">
      <PageHeader
        title="Produtos"
        subtitle="Cadastro e gestão de estoque"
        actions={
          <>
            <Button variant="secondary" onClick={() => setShowRelatorio(true)}>
              <Printer size={16} /> Relatório de Estoque
            </Button>
            <Button variant="primary" onClick={() => navigate('/produtos/novo')}>
              Novo Produto
            </Button>
          </>
        }
      />

      {/* Busca */}
      <div className="relative mb-4">
        <Search size={16} aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          ref={buscaRef}
          type="search"
          aria-label="Buscar produtos por nome, código ou código de barras"
          placeholder="Buscar por nome, código ou código de barras..."
          value={busca}
          onChange={e => alterarBusca(e.target.value)}
          onFocus={e => e.target.select()}
          // seleciona o texto para o próximo código bipado substituir o anterior
          onKeyDown={e => { if (e.key === 'Enter') e.currentTarget.select() }}
          className={`${baseInputClass} pl-9 pr-9 py-2.5 [&::-webkit-search-cancel-button]:hidden`}
        />
        {busca && (
          <button
            type="button"
            onClick={() => alterarBusca('')}
            aria-label="Limpar busca"
            className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded text-gray-400 hover:text-gray-600
              focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
          >
            <X size={16} />
          </button>
        )}
      </div>

      {/* Tabela */}
      <Card padding="none" className="overflow-hidden">
        {isLoading ? (
          <LoadingState />
        ) : isError ? (
          <div className="p-8 text-center">
            <p className="text-danger-600 font-medium mb-1">Erro ao carregar produtos</p>
            <p className="text-gray-400 text-sm">{String((error as Error)?.message ?? 'Falha na requisição')}</p>
          </div>
        ) : lista.length === 0 ? (
          <EmptyState>
            {termo ? `Nenhum produto encontrado para "${termo}".` : 'Nenhum produto cadastrado ainda.'}
          </EmptyState>
        ) : (
          <div className="overflow-x-auto">
          <Table>
            <THead>
              <tr>
                <THOrdenavel campo="codigoInterno" ordem={ordem} onOrdenar={ordenarPor}>Código</THOrdenavel>
                <THOrdenavel campo="nome" ordem={ordem} onOrdenar={ordenarPor}>Nome</THOrdenavel>
                <TH>Marca</TH>
                <TH>Fornecedor</TH>
                <TH>Un.</TH>
                <TH align="right">Custo Médio</TH>
                <THOrdenavel campo="precoVenda" ordem={ordem} onOrdenar={ordenarPor} align="right">Preço Venda</THOrdenavel>
                <THOrdenavel campo="margem" ordem={ordem} onOrdenar={ordenarPor} align="right">Margem</THOrdenavel>
                <THOrdenavel campo="estoqueAtual" ordem={ordem} onOrdenar={ordenarPor} align="right">Estoque</THOrdenavel>
                <TH align="center">PLU</TH>
                <TH align="center">Status</TH>
                {podeVerIdentificacao && <TH>Cadastrado por</TH>}
                <TH />
              </tr>
            </THead>
            <TBody>
              {produtosPagina.map(p => {
                const margem = margemPct(p.precoCusto, p.precoVenda)
                return (
                <TR key={p.id}>
                  <TD className="font-mono text-xs text-gray-500">
                    {p.codigoInterno}
                    {p.ean13 && <div className="text-[11px] text-gray-400">{p.ean13}</div>}
                  </TD>
                  <TD className="font-medium whitespace-nowrap">
                    <div className="flex items-center gap-2 max-w-xs">
                      {p.estoqueAtual <= p.estoqueMinimo && (
                        <AlertTriangle size={14} className="text-warning-500 shrink-0" />
                      )}
                      {/* Nome longo é cortado com "…"; o completo aparece ao passar o mouse. */}
                      <span className="truncate" title={p.nome}>{p.nome}</span>
                    </div>
                    <div className="text-xs font-normal text-gray-400">{nomeTipoProduto(p.tipoProduto)}</div>
                  </TD>
                  <TD className="text-gray-500">{p.marca || '—'}</TD>
                  <TD className="text-gray-500">{p.fornecedor || '—'}</TD>
                  <TD className="text-gray-500">{p.unidadeMedida}</TD>
                  <TD align="right" className="text-gray-500 tabular-nums">
                    {p.precoCusto ? formatBRL(p.precoCusto) : '—'}
                  </TD>
                  <TD align="right" className="font-medium tabular-nums">{formatBRL(p.precoVenda)}</TD>
                  <TD align="right" className="tabular-nums">
                    {margem === null
                      ? <span className="text-gray-400">—</span>
                      : <span className={`font-semibold ${corMargem(margem)}`}>{margem.toFixed(1)}%</span>}
                  </TD>
                  <TD align="right" className={`tabular-nums font-medium whitespace-nowrap
                    ${p.estoqueAtual <= p.estoqueMinimo ? 'text-warning-600' : 'text-gray-700'}`}>
                    {formatQuantidade(p.estoqueAtual, p.unidadeMedida)} {p.unidadeMedida}
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
                      <Button variant="ghost" size="sm" aria-label="Editar produto" onClick={() => navigate(`/produtos/${p.id}/editar`)}>
                        <Pencil size={14} />
                      </Button>
                      <Button variant="ghost" size="sm" aria-label="Inativar produto" className="hover:!text-danger-600" onClick={() => setConfirmando(p)}>
                        <Trash2 size={14} />
                      </Button>
                    </div>
                  </TD>
                </TR>
                )
              })}
            </TBody>
          </Table>
          </div>
        )}

        {/* Paginação */}
        {!isLoading && !isError && <Paginacao paginacao={pag} rotulo="produtos" />}
      </Card>

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
    </div>
  )
}