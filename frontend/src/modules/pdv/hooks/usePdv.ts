import { useState, useCallback, useEffect } from 'react'
import { api, mensagemDeErro } from '@/shared/api/axios'
import { getUsuarioId } from '@/shared/auth'
import toast from 'react-hot-toast'
import type { Venda, ItemVendaDTO, PagamentoDTO } from '@/types/venda'

/** Credencial de administrador que libera um fiado acima do limite do cliente. */
export interface AutorizacaoLimite { login: string; senha: string }

/** Resposta 409 do backend quando o fiado ultrapassa o limite de crédito. */
export interface LimiteExcedidoInfo {
  clienteNome: string
  limite: number
  saldoAtual: number
  valorFiado: number
  excedente: number
}

/** Lançado por fecharVenda quando o fiado passa do limite e falta autorização de administrador. */
export class LimiteExcedidoError extends Error {
  constructor(public info: LimiteExcedidoInfo, message: string) {
    super(message)
    this.name = 'LimiteExcedidoError'
  }
}

export function usePdv() {
  const [venda, setVenda]         = useState<Venda | null>(null)
  const [caixaId, setCaixaId]     = useState<number | null>(null)
  const [semCaixa, setSemCaixa]   = useState(false)
  const [comandas, setComandas]   = useState<Venda[]>([])
  const [loading, setLoading]     = useState(false)
  const [scanLoading, setScan]    = useState(false)

  const operadorId = getUsuarioId()

  // Descobre o caixa aberto do operador logado, em vez de fixar um id que
  // pode estar fechado ou pertencer a outro operador (isso travava toda
  // venda com "Caixa está fechado" e não dava pra ter mais de um caixa
  // aberto ao mesmo tempo no sistema).
  const verificarCaixa = useCallback(() => {
    if (!operadorId) return
    api.get<{ id: number }>('/pdv/caixa/aberto', { params: { operadorId }, silent: true } as any)
      .then(({ data }) => { setCaixaId(data.id); setSemCaixa(false) })
      .catch(() => { setCaixaId(null); setSemCaixa(true) })
  }, [operadorId])

  useEffect(() => { verificarCaixa() }, [verificarCaixa])

  const carregarComandas = useCallback(async () => {
    if (!caixaId) return
    const { data } = await api.get<Venda[]>('/pdv/vendas/abertas', { params: { caixaId } })
    setComandas(data)
  }, [caixaId])

  // Sempre que o caixa for identificado, já carrega as comandas em aberto
  useEffect(() => { carregarComandas() }, [carregarComandas])

  const selecionarComanda = useCallback(async (vendaId: number) => {
    const { data } = await api.get<Venda>(`/pdv/vendas/${vendaId}`)
    setVenda(data)
  }, [])

  // Atualiza uma comanda específica na lista local, sem precisar recarregar tudo da API
  const atualizarComandaLocal = useCallback((vendaAtualizada: Venda) => {
    setComandas(prev => prev.map(c => c.id === vendaAtualizada.id ? vendaAtualizada : c))
  }, [])

  const iniciarVenda = useCallback(async (clienteId?: number): Promise<Venda> => {
    if (!caixaId) throw new Error('Nenhum caixa aberto.')
    const { data } = await api.post<Venda>('/pdv/vendas/abrir', {
      operadorId,
      caixaId,
      clienteId:  clienteId ?? null,
    })
    setVenda(data)
    carregarComandas()
    return data
  }, [caixaId, operadorId, carregarComandas])

  const adicionarItem = useCallback(async (barcode: string) => {
    setScan(true)
    try {
      // 1. Decodificar barcode
      const { data: itemDto } = await api.get<ItemVendaDTO>(`/pdv/barcode/${barcode}`)

      // 2. Garantir que há uma venda aberta
      let vendaAtual = venda
      if (!vendaAtual) {
        vendaAtual = await iniciarVenda()
      }

      // 3. Adicionar item
      const { data: vendaAtualizada } = await api.post<Venda>(
        `/pdv/vendas/${vendaAtual.id}/itens`,
        itemDto
      )
      setVenda(vendaAtualizada)
      atualizarComandaLocal(vendaAtualizada)
      toast.success(`${itemDto.nomeProduto} adicionado`, { duration: 1500 })
    } catch {
      // erro já tratado pelo interceptor do axios
    } finally {
      setScan(false)
    }
  }, [venda, iniciarVenda, atualizarComandaLocal])

  /**
   * Produto escolhido na busca (sem código de barras): abre a comanda se
   * preciso, como o leitor faz. O servidor calcula o total pelo preço atual.
   */
  const adicionarProduto = useCallback(async (produtoId: number, nome: string, quantidade: number) => {
    setScan(true)
    try {
      let vendaAtual = venda
      if (!vendaAtual) {
        vendaAtual = await iniciarVenda()
      }
      const { data: vendaAtualizada } = await api.post<Venda>(
        `/pdv/vendas/${vendaAtual.id}/itens`,
        { produtoId, quantidade, tipoEntrada: 'MANUAL' }
      )
      setVenda(vendaAtualizada)
      atualizarComandaLocal(vendaAtualizada)
      toast.success(`${nome} adicionado`, { duration: 1500 })
    } catch {
      // erro já tratado pelo interceptor do axios
    } finally {
      setScan(false)
    }
  }, [venda, iniciarVenda, atualizarComandaLocal])

  const adicionarItemManual = useCallback(async (dto: ItemVendaDTO) => {
    if (!venda) {
      toast.error('Abra uma venda primeiro.')
      return
    }
    setLoading(true)
    try {
      const { data } = await api.post<Venda>(`/pdv/vendas/${venda.id}/itens`, dto)
      setVenda(data)
      atualizarComandaLocal(data)
    } finally {
      setLoading(false)
    }
  }, [venda, atualizarComandaLocal])

  const removerItem = useCallback(async (itemId: number) => {
    if (!venda) return
    const { data } = await api.delete<Venda>(`/pdv/vendas/${venda.id}/itens/${itemId}`)
    setVenda(data)
    atualizarComandaLocal(data)
  }, [venda, atualizarComandaLocal])

  const aplicarDescontoItem = useCallback(async (itemId: number, valor: number) => {
    if (!venda) return
    const { data } = await api.put<Venda>(`/pdv/vendas/${venda.id}/itens/${itemId}/desconto`, { valor })
    setVenda(data)
    atualizarComandaLocal(data)
  }, [venda, atualizarComandaLocal])

  const fecharVenda = useCallback(async (pagamentos: PagamentoDTO[], autorizacaoLimite?: AutorizacaoLimite) => {
    if (!venda) return
    setLoading(true)
    try {
      // silent: o toast global não deve aparecer no "limite excedido" — isso abre o pedido de autorização
      const { data: vendaFechada } = await api.post<Venda>(
        `/pdv/vendas/fechar`,
        { vendaId: venda.id, pagamentos, ...(autorizacaoLimite ? { autorizacaoLimite } : {}) },
        { silent: true } as any
      )
      toast.success('Venda finalizada!')
      setVenda(null)
      carregarComandas()
      return vendaFechada
    } catch (e: any) {
      const data = e?.response?.data
      if (e?.response?.status === 409 && data?.codigo === 'LIMITE_CREDITO_EXCEDIDO') {
        throw new LimiteExcedidoError(data as LimiteExcedidoInfo, String(data.message ?? 'Limite de crédito excedido.'))
      }
      toast.error(mensagemDeErro(e))
      throw e
    } finally {
      setLoading(false)
    }
  }, [venda, carregarComandas])

  const cancelarVenda = useCallback(async () => {
    if (!venda) return
    await api.post(`/pdv/vendas/${venda.id}/cancelar`)
    toast('Venda cancelada', { icon: '⚠️' })
    setVenda(null)
    carregarComandas()
  }, [venda, carregarComandas])

  /** Sai da comanda aberta e volta à tela inicial do PDV. A comanda continua em aberto, nas abas. */
  const sairDaComanda = useCallback(() => {
    setVenda(null)
    carregarComandas()
  }, [carregarComandas])

  const abrirCaixa = useCallback(async (valorAbertura: number) => {
    if (!operadorId) throw new Error('Usuário não identificado.')
    await api.post('/pdv/caixa/abrir', null, { params: { operadorId, valorAbertura } })
    verificarCaixa()
  }, [operadorId, verificarCaixa])

  const fecharCaixa = useCallback(async (valorInformado: number) => {
    if (!caixaId) return
    await api.post(`/pdv/caixa/${caixaId}/fechar`, null, { params: { valorInformado } })
    setCaixaId(null)
    setVenda(null)
    setComandas([])
    verificarCaixa()
  }, [caixaId, verificarCaixa])

  return {
    venda,
    comandas,
    caixaId,
    semCaixa,
    operadorId,
    loading,
    scanLoading,
    totalVenda: venda?.total ?? 0,
    adicionarItem,
    adicionarItemManual,
    adicionarProduto,
    removerItem,
    aplicarDescontoItem,
    fecharVenda,
    cancelarVenda,
    sairDaComanda,
    iniciarVenda,
    selecionarComanda,
    abrirCaixa,
    fecharCaixa,
    novaComanda: () => setVenda(null), // limpa a comanda ativa, sem fechar nenhuma
  }
}