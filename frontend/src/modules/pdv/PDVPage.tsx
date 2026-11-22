import { useState, useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ShoppingBag, Wifi, WifiOff, Trash2, Plus, User, LockOpen, Lock, Printer, LogOut, Coins } from 'lucide-react'
import { useBarcodeScan } from '@/shared/hooks/useBarcodeScan'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { getNomeUsuario } from '@/shared/auth'
import { api } from '@/shared/api/axios'
import { usePdv, LimiteExcedidoError } from './hooks/usePdv'
import type { LimiteExcedidoInfo } from './hooks/usePdv'
import AutorizacaoLimiteModal from './components/AutorizacaoLimiteModal'
import AutorizacaoAdminModal from './components/AutorizacaoAdminModal'
import ContagemCedulas, { totalContagem, contagemParaApi } from './components/ContagemCedulas'
import type { Contagem } from './components/ContagemCedulas'
import type { PagamentoDTO } from '@/types/venda'
import ListaItens, { ehEtiquetaBalanca } from './components/ListaItens'
import QuantidadeItemModal from './components/QuantidadeItemModal'
import DescontoItemModal from './components/DescontoItemModal'
import ModalPagamento from './components/ModalPagamento'
import CpfNaNotaModal from './components/CpfNaNotaModal'
import type { EscolhaNota } from './components/CpfNaNotaModal'
import type { Nfce, SituacaoNfce } from '@/types/fiscal'
import NovaComandaModal from './components/NovaComandaModal'
import { formatBRL, formatCurrencyDisplay } from '@/shared/utils/mask'
import { CurrencyInput, WeightInput, BuscaProduto } from '@/shared/components/ui'
import type { ProdutoBuscavel } from '@/shared/components/ui'
import { casasDaUnidade } from '@/shared/utils/mask'
import FaixaAmbiente from '@/shared/components/ui/FaixaAmbiente'
import FaixaJarvis from '@/shared/components/ui/FaixaJarvis'
import { imprimirComprovante } from './imprimirComprovante'
import type { ConfiguracaoComprovante, PagamentoCupom } from '@/types/comprovante'
import type { ItemVenda, Venda } from '@/types/venda'
import toast from 'react-hot-toast'

// NOTA DE DESIGN: o PDV usa o mesmo tema claro do restante do sistema (tokens
// bg-background / bg-card / border-border). Teclas de atalho (F10...) e texto
// grande no total continuam para operação rápida em balcão. Cores: verde = ação
// positiva/confirmar, vermelho = cancelar/perigo e valores dos itens, azul =
// informativo, âmbar = atenção. Campos de valor usam CurrencyInput/WeightInput.
const brl = formatBRL

/** Tamanho do total a pagar conforme os dígitos: 55,35 → grande; 12.345,67 → menor. */
const tamanhoTotal = (valor: string) =>
  valor.length <= 6 ? 'text-6xl' : valor.length <= 9 ? 'text-5xl' : 'text-4xl'

interface FechamentoResumo {
  valorAbertura: number
  totalVendas: number
  totalDinheiro: number
  totalSuprimento: number
  totalSangria: number
  saldoEsperado: number
  quantidadeVendas: number
}

export default function PDVPage() {
  const {
    venda, comandas, loading, scanLoading, totalVenda, caixaId, semCaixa,
    adicionarItem, adicionarProduto, removerItem, aplicarDescontoItem, alterarQuantidadeItem, fecharVenda, cancelarVenda, sairDaComanda,
    iniciarVenda, selecionarComanda, abrirCaixa, fecharCaixa,
  } = usePdv()

  const [showPagto, setShowPagto] = useState(false)
  // Fiado acima do limite: guarda o pagamento e os valores até um administrador autorizar
  const [limitePendente, setLimitePendente] = useState<{ pagamentos: PagamentoDTO[]; info: LimiteExcedidoInfo; escolha?: EscolhaNota } | null>(null)
  const [autorizando, setAutorizando] = useState(false)
  const [showNovaComanda, setShowNovaComanda] = useState(false)
  const [descontando, setDescontando] = useState<ItemVenda | null>(null)
  const [quantificando, setQuantificando] = useState<ItemVenda | null>(null)
  const [excluindo, setExcluindo] = useState<ItemVenda | null>(null)
  const [cancelando, setCancelando] = useState(false)
  const [selecionadoId, setSelecionadoId] = useState<number | null>(null)
  const [hora, setHora]           = useState(new Date())
  const [valorAbertura, setValorAbertura] = useState(0)
  const [abrindo, setAbrindo]     = useState(false)
  // Contagem de cédulas e moedas (opcional) na abertura e no fechamento do caixa
  const [contarAbertura, setContarAbertura] = useState(false)
  const [cedulasAbertura, setCedulasAbertura] = useState<Contagem>({})
  const [contarFechamento, setContarFechamento] = useState(false)
  const [cedulasFechamento, setCedulasFechamento] = useState<Contagem>({})
  // Validando a senha do administrador (excluir item / cancelar venda)
  const [autorizandoAdmin, setAutorizandoAdmin] = useState(false)

  const [showFechar, setShowFechar]       = useState(false)
  const [resumoFechar, setResumoFechar]   = useState<FechamentoResumo | null>(null)
  const [valorContado, setValorContado]   = useState(0)
  const [fechando, setFechando]           = useState(false)

  const nomeOperador = getNomeUsuario()

  // ── Comprovante (cupom não fiscal) ────────────────────────────
  const { data: configCupom } = useQuery<ConfiguracaoComprovante>({
    queryKey: ['comprovante-config'],
    queryFn: () => api.get('/comprovante/configuracao', { silent: true } as never).then(r => r.data),
    staleTime: 5 * 60_000,
  })
  const [ultimoCupom, setUltimoCupom] = useState<{ venda: Venda; pagamentos: PagamentoCupom[]; nfce?: Nfce | null } | null>(null)
  const [perguntarImpressao, setPerguntarImpressao] = useState(false)

  // ── NFC-e ("CPF na nota?"), só com a emissão ligada na configuração fiscal ──
  const { data: situacaoNfce } = useQuery<SituacaoNfce>({
    queryKey: ['nfce-situacao'],
    queryFn: () => api.get('/fiscal/nfce/situacao', { silent: true } as never).then(r => r.data),
    staleTime: 60_000,
  })
  const [perguntarCpf, setPerguntarCpf] = useState<PagamentoDTO[] | null>(null)

  /** Emite a NFC-e da venda já fechada; se não sair, a venda segue valendo com o cupom não fiscal. */
  async function emitirNfce(vendaId: number, documento?: string): Promise<Nfce | null> {
    try {
      const { data } = await api.post<Nfce>(`/fiscal/nfce/venda/${vendaId}`, { documento: documento ?? null }, { silent: true } as never)
      if (data.status === 'AUTORIZADA') {
        toast.success(`NFC-e nº ${data.numero} autorizada`)
        return data
      }
      if (data.status === 'DESCONHECIDO') {
        toast.error(`NFC-e nº ${data.numero} sem resposta da SEFAZ: pode ter sido autorizada. Sai o cupom não fiscal; `
          + 'consulte a situação em Fiscal → Emissão de Notas antes de emitir de novo.', { duration: 10000 })
        return null
      }
      toast.error(`NFC-e não autorizada: ${data.mensagem ?? data.status}. A venda foi registrada; sai o cupom não fiscal.`, { duration: 8000 })
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(`NFC-e não emitida: ${msg ?? 'falha de comunicação'}. A venda foi registrada; sai o cupom não fiscal.`, { duration: 8000 })
    }
    return null
  }

  /** Fecha a venda e trata o comprovante. Usado no pagamento normal e após a autorização do admin. */
  async function finalizarVenda(pagamentos: PagamentoDTO[], autorizacao?: { login: string; senha: string }, escolha?: EscolhaNota) {
    const vendaFechada = await fecharVenda(pagamentos, autorizacao)
    setShowPagto(false)
    setPerguntarCpf(null)
    if (vendaFechada) {
      const nfce = escolha?.emitir ? await emitirNfce(vendaFechada.id, escolha.documento) : null
      const cupom = { venda: vendaFechada, pagamentos, nfce }
      setUltimoCupom(cupom)
      if (configCupom?.imprimirAutomatico) {
        try { imprimirComprovante(cupom.venda, nomeOperador ?? '', configCupom, pagamentos, nfce ? 1 : configCupom.vias, nfce) }
        catch { toast.error('Não foi possível imprimir o comprovante.') }
      } else {
        setPerguntarImpressao(true)
      }
    }
  }

  /** Do pagamento: com NFC-e ligada, pergunta "CPF na nota?" antes de fechar. */
  async function confirmarPagamento(pagamentos: PagamentoDTO[], escolha?: EscolhaNota) {
    if (situacaoNfce?.emitirNfce && !escolha) {
      setPerguntarCpf(pagamentos)
      return
    }
    try {
      await finalizarVenda(pagamentos, undefined, escolha)
    } catch (e) {
      setPerguntarCpf(null)
      if (e instanceof LimiteExcedidoError) setLimitePendente({ pagamentos, info: e.info, escolha })
    }
  }

  function imprimirUltimo() {
    if (!ultimoCupom || !configCupom) return
    try {
      imprimirComprovante(ultimoCupom.venda, nomeOperador ?? '', configCupom, ultimoCupom.pagamentos,
        ultimoCupom.nfce ? 1 : configCupom.vias, ultimoCupom.nfce)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Não foi possível imprimir.')
    }
    setPerguntarImpressao(false)
  }
  const { podeEditar } = usePermissao()
  // Excluir item e cancelar venda: qualquer operador pode pedir, mas só conclui com o login e a
  // senha de um administrador (AutorizacaoAdminModal) — a senha é conferida no servidor.
  const podeDescontar = podeEditar('PDV')

  // Item em que F6 e F8 agem: o escolhido ou, por padrão, o último lançado
  const itens = venda?.itens ?? []
  const itemSelecionado = itens.find(i => i.id === selecionadoId) ?? itens[itens.length - 1]
  useEffect(() => { setSelecionadoId(null) }, [venda?.id, itens.length])

  // Relógio
  useEffect(() => {
    const t = setInterval(() => setHora(new Date()), 1000)
    return () => clearInterval(t)
  }, [])

  // ── Busca de produto (além do leitor) ─────────────────────────
  const buscaRef = useRef<HTMLInputElement>(null)
  const [pesando, setPesando] = useState<ProdutoBuscavel | null>(null)
  const [peso, setPeso] = useState(0)

  const { data: produtosPdv = [] } = useQuery<(ProdutoBuscavel & { ativo?: boolean })[]>({
    queryKey: ['pdv-produtos'],
    queryFn: () => api.get('/estoque/produtos', { silent: true } as never).then(r => r.data),
    staleTime: 60_000,
    enabled: !semCaixa,
  })
  const produtosAtivos = produtosPdv.filter(p => p.ativo !== false)

  const porPeso = (un?: string) => ['KG', 'G'].includes((un ?? '').toUpperCase())

  function escolherProduto(prod: ProdutoBuscavel) {
    if (porPeso(prod.unidadeMedida)) {
      setPeso(0)
      setPesando(prod)          // pede o peso antes de lançar
    } else {
      adicionarProduto(prod.id, prod.nome, 1)
    }
  }

  async function confirmarPeso() {
    if (!pesando || peso <= 0) return
    await adicionarProduto(pesando.id, pesando.nome, peso)
    setPesando(null)
    buscaRef.current?.focus()
  }

  // Deixa a comanda atual em espera (continua aberta nas abas) e volta à tela inicial do PDV
  function sairDaComandaAberta() {
    if (showPagto || limitePendente) return
    sairDaComanda()
    buscaRef.current?.focus()
  }
  const sairRef = useRef(sairDaComandaAberta)
  sairRef.current = sairDaComandaAberta

  // F6 = Desconto, F7 = Quantidade, + / − = uma unidade a mais ou a menos, F8 = Excluir (pede confirmação),
  // ↑ ↓ ou PgUp PgDn = escolher o item.
  // As setas ficam de fora nos campos: na busca, elas navegam a lista de produtos.
  function atalhoDoItem(e: KeyboardEvent) {
    if (showPagto || limitePendente || pesando || perguntarImpressao || showNovaComanda || showFechar || quantificando) return
    const el = document.activeElement
    const emCampo = Boolean(el && ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName))
    const sobe = e.key === 'PageUp' || (e.key === 'ArrowUp' && !emCampo)
    const desce = e.key === 'PageDown' || (e.key === 'ArrowDown' && !emCampo)

    if (e.key === 'F6') {
      e.preventDefault()
      if (!itemSelecionado) return
      if (podeDescontar) setDescontando(itemSelecionado)
      else toast.error('Seu perfil não pode dar desconto.')
    } else if (e.key === 'F7') {
      e.preventDefault()
      if (!itemSelecionado) return
      if (ehEtiquetaBalanca(itemSelecionado)) toast.error('O peso vem da etiqueta da balança.')
      else setQuantificando(itemSelecionado)
    } else if ((e.key === '+' || e.key === '-') && !emCampo && itemSelecionado) {
      e.preventDefault()
      somarUm(itemSelecionado, e.key === '+' ? 1 : -1)
    } else if (e.key === 'F8') {
      e.preventDefault()
      if (itemSelecionado) setExcluindo(itemSelecionado)
    } else if ((sobe || desce) && itemSelecionado) {
      e.preventDefault()
      const novo = itens[itens.indexOf(itemSelecionado) + (sobe ? -1 : 1)]
      if (novo) setSelecionadoId(novo.id)
    }
  }
  // + / − só em produto por unidade; o de peso abre a quantidade (F7) para digitar os kg
  async function somarUm(item: ItemVenda, delta: number) {
    if (ehEtiquetaBalanca(item)) { toast.error('O peso vem da etiqueta da balança.'); return }
    if (item.produto.unidadeMedida === 'KG' || item.produto.unidadeMedida === 'G') { setQuantificando(item); return }
    const nova = item.quantidade + delta
    if (nova < 1) { toast('Para tirar o item, use F8 (Excluir).', { icon: 'ℹ️' }); return }
    try {
      await alterarQuantidadeItem(item.id, nova)
    } catch {
      // erro já mostrado pelo interceptor do axios
    }
  }

  const atalhoRef = useRef(atalhoDoItem)
  atalhoRef.current = atalhoDoItem

  // Os dois devolvem true quando concluíram. O erro (senha errada, sem permissão...) já aparece no
  // toast do interceptor do axios; o modal continua aberto para tentar de novo.
  async function confirmarExclusao(login: string, senha: string): Promise<boolean> {
    if (!excluindo) return false
    setAutorizandoAdmin(true)
    try {
      await removerItem(excluindo.id, { login, senha })
      toast.success('Item excluído.', { duration: 1500 })
      setExcluindo(null)
      return true
    } catch {
      return false
    } finally {
      setAutorizandoAdmin(false)
    }
  }

  async function confirmarCancelamento(login: string, senha: string, motivo?: string): Promise<boolean> {
    setAutorizandoAdmin(true)
    try {
      await cancelarVenda({ login, senha, motivo })
      setCancelando(false)
      return true
    } catch {
      return false
    } finally {
      setAutorizandoAdmin(false)
    }
  }

  // F2 = Buscar produto, F4 = Sair da comanda, F9 = Cancelar venda, F10 = Finalizar.
  // Com uma janela aberta eles ficam desligados: no pagamento, F1–F6 e F9 escolhem a forma.
  function atalhoDaTela(e: KeyboardEvent) {
    const janelaAberta = showPagto || limitePendente || pesando || perguntarImpressao || showNovaComanda
      || showFechar || excluindo || descontando || quantificando || cancelando
    if (janelaAberta) return
    if (e.key === 'F4') { e.preventDefault(); sairRef.current() }
    if (e.key === 'F10') { e.preventDefault(); if (venda?.itens?.length) setShowPagto(true) }
    if (e.key === 'F2') { e.preventDefault(); buscaRef.current?.focus() }
    if (e.key === 'F9') {
      e.preventDefault()
      if (venda) setCancelando(true)
    }
  }
  const atalhoTelaRef = useRef(atalhoDaTela)
  atalhoTelaRef.current = atalhoDaTela

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      atalhoRef.current(e)
      atalhoTelaRef.current(e)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  // Listener global do leitor de código de barras
  useBarcodeScan({
    onScan: codigo => {
      const el = document.activeElement
      if (el && ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName)) return // evita lançar em dobro
      adicionarItem(codigo)
    },
    enabled: !showPagto && !semCaixa && !pesando && !perguntarImpressao && !descontando && !quantificando && !excluindo && !cancelando,
  })

  const handleAbrirCaixa = async () => {
    setAbrindo(true)
    try {
      await abrirCaixa(contarAbertura ? totalContagem(cedulasAbertura) : valorAbertura, contarAbertura ? contagemParaApi(cedulasAbertura) : undefined)
      setValorAbertura(0)
      setContarAbertura(false)
      setCedulasAbertura({})
    } finally {
      setAbrindo(false)
    }
  }

  const handleAbrirFechamento = async () => {
    if (!caixaId) return
    const { data } = await api.get(`/pdv/caixa/${caixaId}/fechamento`)
    setResumoFechar(data)
    setShowFechar(true)
  }

  // Com a contagem por cédula ligada, o valor contado é a soma dela
  const valorInformadoFechamento = contarFechamento ? totalContagem(cedulasFechamento) : valorContado

  const handleConfirmarFechamento = async () => {
    setFechando(true)
    try {
      await fecharCaixa(valorInformadoFechamento, contarFechamento ? contagemParaApi(cedulasFechamento) : undefined)
      setShowFechar(false)
      setResumoFechar(null)
      setValorContado(0)
      setContarFechamento(false)
      setCedulasFechamento({})
    } finally {
      setFechando(false)
    }
  }

  return (
    <div className="h-screen flex flex-col bg-background text-gray-900 select-none overflow-hidden">
      <FaixaAmbiente />
      <FaixaJarvis />

      {/* ── Topbar ── */}
      <header className="flex items-center justify-between px-6 py-3 bg-card border-b border-border">
        <div className="flex items-center gap-3">
          <span className="font-bold text-lg">PDV</span>
          {nomeOperador && (
            <span className="flex items-center gap-1.5 text-xs bg-gray-100 text-gray-600 px-2.5 py-1 rounded-full">
              <User size={12} /> {nomeOperador}
            </span>
          )}
          {venda && (
            <span className="text-xs bg-gray-200 text-gray-600 px-2 py-0.5 rounded-full">
              Cupom #{venda.id}
            </span>
          )}
          {scanLoading && (
            <span className="text-xs bg-warning-100 text-warning-700 px-2 py-0.5 rounded-full animate-pulse">
              Processando...
            </span>
          )}
        </div>
        <div className="flex items-center gap-4 text-sm text-gray-500">
          {caixaId && (
            <button
              onClick={handleAbrirFechamento}
              className="flex items-center gap-1.5 text-xs bg-gray-100 hover:bg-gray-200 text-gray-600 px-2.5 py-1.5 rounded-lg"
            >
              Fechar Caixa
            </button>
          )}
          {scanLoading
            ? <WifiOff size={16} className="text-warning-500 animate-pulse" />
            : <Wifi size={16} className="text-success-500" />}
          <span className="tabular-nums">
            {hora.toLocaleTimeString('pt-BR')}
          </span>
          <span>{hora.toLocaleDateString('pt-BR')}</span>
        </div>
      </header>

      {/* ── Sem caixa aberto: bloqueia o PDV até abrir um ── */}
      {semCaixa && (
        <div className="flex-1 flex items-center justify-center">
          <div className={`bg-card border border-border shadow-flutuante rounded-2xl p-8 w-full text-center space-y-4 max-h-full overflow-y-auto
                           ${contarAbertura ? 'max-w-lg' : 'max-w-sm'}`}>
            <LockOpen size={32} className="mx-auto text-warning-500" />
            <div>
              <h2 className="font-bold text-lg">Nenhum caixa aberto</h2>
              <p className="text-sm text-gray-500 mt-1">
                {nomeOperador ? `${nomeOperador}, abra` : 'Abra'} um caixa informando o valor inicial (fundo de troco).
              </p>
            </div>
            <CurrencyInput
              value={contarAbertura ? totalContagem(cedulasAbertura) : valorAbertura}
              onChange={setValorAbertura}
              autoFocus={!contarAbertura}
              disabled={contarAbertura}
              aria-label="Valor inicial do caixa"
              className="[&_input]:text-lg [&_input]:text-center"
            />
            <button
              type="button"
              onClick={() => setContarAbertura(v => !v)}
              className="inline-flex items-center gap-1.5 text-sm text-primary-600 hover:text-primary-700"
            >
              <Coins size={15} />
              {contarAbertura ? 'Informar só o valor total' : 'Discriminar cédulas e moedas'}
            </button>
            {contarAbertura && (
              <div className="text-left">
                <ContagemCedulas contagem={cedulasAbertura} onChange={setCedulasAbertura} />
              </div>
            )}
            <button
              onClick={handleAbrirCaixa}
              disabled={!(contarAbertura ? totalContagem(cedulasAbertura) : valorAbertura) || abrindo}
              className="w-full py-3 bg-success-600 hover:bg-success-500 rounded-xl font-bold disabled:opacity-50 disabled:cursor-not-allowed text-white"
            >
              {abrindo ? 'Abrindo...' : 'Abrir Caixa'}
            </button>
          </div>
        </div>
      )}

      {/* ── Barra de Comandas ── */}
      {!semCaixa && (
      <div className="flex items-center gap-2 px-4 py-2 bg-card border-b border-border overflow-x-auto shrink-0">
        <button
          onClick={() => setShowNovaComanda(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
                     bg-primary-600 hover:bg-primary-500 transition-colors shrink-0 text-white"
        >
          Nova Comanda
        </button>

        {comandas.length === 0 && (
          <span className="text-xs text-gray-400 px-2">Nenhuma comanda aberta</span>
        )}

        {comandas.map(c => (
          <button
            key={c.id}
            onClick={() => selecionarComanda(c.id)}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium shrink-0 transition-colors
              ${venda?.id === c.id
                ? 'bg-success-700 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
          >
            <User size={12} />
            {c.cliente?.nome ?? `Comanda #${c.id}`}
            <span className="opacity-70 tabular-nums">{brl(c.total)}</span>
          </button>
        ))}
      </div>
      )}

      {/* ── Corpo ── */}
      {!semCaixa && (
      <div className="flex flex-1 overflow-hidden">

        {/* Coluna esquerda: itens */}
        <div className="flex-1 flex flex-col overflow-hidden border-r border-border">
          <div className="px-4 py-3 border-b border-border bg-card flex items-center gap-3">
            <BuscaProduto
              produtos={produtosAtivos}
              modo="acao"
              inputRef={buscaRef}
              placeholder="Buscar produto por nome ou código  (F2)"
              onSelect={escolherProduto}
              aoDigitarCodigo={adicionarItem}
              detalhe={pr => pr.precoVenda != null
                ? `${brl(pr.precoVenda)}${porPeso(pr.unidadeMedida) ? '/kg' : ''}`
                : ''}
              className="flex-1 [&_input]:py-2.5 [&_input]:text-base"
            />
          </div>
          <ListaItens
            itens={itens}
            onRemover={id => { const it = itens.find(i => i.id === id); if (it) setExcluindo(it) }}
            onDesconto={setDescontando}
            onQuantidade={setQuantificando}
            podeDescontar={podeDescontar}
            selecionadoId={itemSelecionado?.id}
            onSelecionar={setSelecionadoId}
          />
          {/* Rodapé da lista */}
          <div className="px-4 py-3 bg-card border-t border-border flex justify-between text-sm text-gray-500">
            <span>{venda?.itens?.length ?? 0} iten(s)</span>
            {itens.length > 0 && (
              <span className="text-xs text-gray-500">↑ ↓ escolher item · + − quantidade · F6 desconto · F7 qtd · F8 excluir</span>
            )}
            {(venda?.desconto ?? 0) > 0 && (
              <span>Desconto: -{brl(venda?.desconto ?? 0)}</span>
            )}
            <span>Subtotal: <span className="text-gray-900 font-medium tabular-nums">{brl(venda?.subtotal ?? 0)}</span></span>
          </div>
        </div>

        {/* Coluna direita: totais e ações */}
        <div className="w-96 shrink-0 flex flex-col gap-4 p-6 bg-card">

          {/* Total */}
          <div className="bg-gray-100 rounded-2xl px-4 py-8 text-center shadow-inner">
            <p className="text-gray-500 text-sm uppercase tracking-widest mb-4">Total a Pagar</p>
            {/* "R$" menor e valor que encolhe com a quantidade de dígitos: sempre cabe centralizado */}
            <p className="flex items-baseline justify-center gap-1.5 whitespace-nowrap font-black tabular-nums text-danger-600 leading-none">
              <span className="text-3xl">R$</span>
              <span className={tamanhoTotal(formatCurrencyDisplay(totalVenda))}>
                {formatCurrencyDisplay(totalVenda)}
              </span>
            </p>
          </div>

          {/* Botões */}
          <button
            onClick={() => setShowPagto(true)}
            disabled={!venda?.itens?.length || loading}
            className="py-5 rounded-2xl text-xl font-bold
                       bg-success-600 hover:bg-success-500 active:scale-95
                       disabled:bg-gray-200 disabled:text-gray-500 disabled:cursor-not-allowed
                       transition-all duration-150 text-white"
          >
            FINALIZAR
            <span className="block text-xs font-normal opacity-70 mt-0.5">[F10]</span>
          </button>

          <button
            onClick={sairDaComandaAberta}
            disabled={!venda || loading}
            title="Deixa esta comanda em aberto (continua na aba) e volta à tela inicial do PDV"
            className="py-3 rounded-xl text-sm font-medium bg-gray-100 hover:bg-gray-200 text-gray-700                       disabled:text-gray-400 disabled:cursor-not-allowed
                       flex items-center justify-center gap-2 transition-colors"
          >
            <LogOut size={15} />
            SAIR DA COMANDA
            <span className="text-xs font-normal opacity-60">[F4]</span>
          </button>

          <button
            onClick={() => setCancelando(true)}
            disabled={!venda}
            title="Pede a senha de um administrador"
            className="py-3 rounded-xl text-sm font-medium
                       border border-danger-200 bg-danger-50 hover:bg-danger-100 text-danger-700
                       disabled:bg-gray-100 disabled:text-gray-400 disabled:cursor-not-allowed
                       flex items-center justify-center gap-2 transition-colors"
          >
            <Trash2 size={15} />
            CANCELAR VENDA
            <span className="text-xs font-normal opacity-60">[F9]</span>
          </button>

          <button
            onClick={imprimirUltimo}
            disabled={!ultimoCupom || !configCupom}
            title={ultimoCupom ? `Venda #${ultimoCupom.venda.id}` : 'Nenhuma venda finalizada neste caixa ainda'}
            className="w-full py-2.5 rounded-xl text-sm font-medium bg-gray-100 hover:bg-gray-200 text-gray-600                       disabled:text-gray-400 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
          >
            <Printer size={15} />
            Reimprimir último cupom
          </button>

          <div className="mt-auto text-center text-xs text-gray-400">
            Leitor de barcode ativo
          </div>
        </div>
      </div>
      )}

      {/* Pergunta de impressão ao finalizar a venda */}
      {perguntarImpressao && ultimoCupom && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
             onKeyDown={e => { if (e.key === 'Escape') setPerguntarImpressao(false) }}>
          <div className="bg-card border border-border rounded-2xl w-full max-w-sm p-6 space-y-4 text-center">
            <Printer size={32} className="mx-auto text-success-700" />
            <div>
              <h2 className="text-lg font-bold">Venda #{ultimoCupom.venda.id} finalizada</h2>
              <p className="text-sm text-gray-500">
                {ultimoCupom.nfce ? `NFC-e nº ${ultimoCupom.nfce.numero} autorizada. Imprimir o DANFE?` : 'Imprimir o comprovante para o cliente?'}
              </p>
            </div>
            <div className="flex gap-3">
              <button onClick={() => setPerguntarImpressao(false)}
                className="flex-1 py-3 rounded-xl bg-gray-200 hover:bg-gray-300 text-sm font-medium">
                Não imprimir <span className="opacity-60">[Esc]</span>
              </button>
              <button autoFocus onClick={imprimirUltimo}
                className="flex-1 py-3 rounded-xl bg-success-600 hover:bg-success-500 font-bold text-sm text-white">
                Imprimir <span className="opacity-70 font-normal">[Enter]</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Exclusão de item (lixeira ou F8): só conclui com a senha de um administrador */}
      {excluindo && (
        <AutorizacaoAdminModal
          titulo="Excluir item?"
          subtitulo={`${excluindo.produto.nome} · ${brl(excluindo.totalItem)}`}
          confirmarLabel="Excluir item"
          enviando={autorizandoAdmin}
          onAutorizar={confirmarExclusao}
          onCancelar={() => setExcluindo(null)}
        />
      )}

      {/* Cancelar venda (botão ou F9): só conclui com a senha de um administrador */}
      {cancelando && venda && (
        <AutorizacaoAdminModal
          titulo="Cancelar esta venda?"
          subtitulo={`Venda #${venda.id} · ${venda.itens?.length ?? 0} item(ns) · ${brl(venda.total ?? 0)}`}
          descricao="Não dá para desfazer. Um administrador precisa informar o login e a senha para autorizar o cancelamento."
          confirmarLabel="Cancelar venda"
          pedirMotivo
          enviando={autorizandoAdmin}
          onAutorizar={confirmarCancelamento}
          onCancelar={() => setCancelando(false)}
        />
      )}

      {/* Desconto no item */}
      {descontando && (
        <DescontoItemModal
          item={descontando}
          onConfirmar={async valor => {
            await aplicarDescontoItem(descontando.id, valor)
            setDescontando(null)
          }}
          onCancelar={() => setDescontando(null)}
        />
      )}

      {/* Quantidade do item (F7) */}
      {quantificando && (
        <QuantidadeItemModal
          item={quantificando}
          onConfirmar={async quantidade => {
            await alterarQuantidadeItem(quantificando.id, quantidade)
            setQuantificando(null)
          }}
          onCancelar={() => setQuantificando(null)}
        />
      )}

      {/* Peso do produto escolhido na busca */}
      {pesando && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-card border border-border rounded-2xl w-full max-w-sm p-6 space-y-4">
            <div>
              <h2 className="text-lg font-bold">{pesando.nome}</h2>
              {pesando.precoVenda != null && (
                <p className="text-sm text-gray-500">{brl(pesando.precoVenda)} por kg</p>
              )}
            </div>
            <WeightInput
              value={peso}
              onChange={setPeso}
              unit={(pesando.unidadeMedida ?? 'kg').toLowerCase()}
              decimals={casasDaUnidade(pesando.unidadeMedida)}
              autoFocus
              onKeyDown={e => {
                if (e.key === 'Enter') confirmarPeso()
                if (e.key === 'Escape') setPesando(null)
              }}
              className="[&_input]:text-2xl [&_input]:font-bold"
            />
            {pesando.precoVenda != null && peso > 0 && (
              <p className="text-right text-success-700 font-bold tabular-nums">
                Total: {brl(Math.round(peso * pesando.precoVenda * 100) / 100)}
              </p>
            )}
            <div className="flex gap-3">
              <button onClick={() => setPesando(null)}
                className="flex-1 py-3 rounded-xl bg-gray-200 hover:bg-gray-300 text-sm font-medium">
                Cancelar
              </button>
              <button onClick={confirmarPeso} disabled={peso <= 0 || scanLoading}
                className="flex-1 py-3 rounded-xl bg-success-600 hover:bg-success-500 disabled:bg-gray-200 disabled:text-gray-500 font-bold text-sm text-white">
                Adicionar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal de pagamento */}
      {showPagto && (
        <ModalPagamento
          totalVenda={totalVenda}
          vendaId={venda?.id}
          onConfirmar={pagamentos => confirmarPagamento(pagamentos)}
          onCancelar={() => setShowPagto(false)}
          pausado={perguntarCpf !== null}
        />
      )}

      {/* NFC-e: "CPF na nota?" antes de fechar a venda */}
      {perguntarCpf && situacaoNfce && (
        <CpfNaNotaModal
          modo={situacaoNfce.modo}
          documentoInicial={venda?.cliente?.nome !== 'CONSUMIDOR' ? venda?.cliente?.cpfCnpj : undefined}
          onEscolher={escolha => confirmarPagamento(perguntarCpf, escolha)}
          onVoltar={() => setPerguntarCpf(null)}
        />
      )}

      {/* Fiado acima do limite: só um administrador libera */}
      {limitePendente && (
        <AutorizacaoLimiteModal
          info={limitePendente.info}
          enviando={autorizando}
          onCancelar={() => !autorizando && setLimitePendente(null)}
          onAutorizar={async (login, senha) => {
            setAutorizando(true)
            try {
              await finalizarVenda(limitePendente.pagamentos, { login, senha }, limitePendente.escolha)
              setLimitePendente(null)
            } catch {
              /* credencial inválida/bloqueio: o aviso já foi mostrado; o modal fica aberto para tentar de novo */
            } finally {
              setAutorizando(false)
            }
          }}
        />
      )}

      {/* Modal de nova comanda */}
      {showNovaComanda && (
        <NovaComandaModal
          onSelecionar={async (clienteId) => {
            await iniciarVenda(clienteId)
            setShowNovaComanda(false)
          }}
          onFechar={() => setShowNovaComanda(false)}
        />
      )}

      {/* Modal de fechamento de caixa */}
      {showFechar && resumoFechar && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className={`bg-card border border-border shadow-flutuante rounded-2xl w-full p-6 space-y-4 max-h-[92vh] overflow-y-auto
                           ${contarFechamento ? 'max-w-lg' : 'max-w-sm'}`}>
            <h2 className="text-lg font-bold flex items-center gap-2">
              Fechar Caixa
            </h2>
            <div className="space-y-1.5 text-sm">
              {[
                ['Abertura', resumoFechar.valorAbertura],
                ['Vendas em dinheiro', resumoFechar.totalDinheiro],
                ['Suprimento (+)', resumoFechar.totalSuprimento],
                ['Sangria (-)', resumoFechar.totalSangria],
              ].map(([label, val]) => (
                <div key={label as string} className="flex justify-between text-gray-500">
                  <span>{label}</span>
                  <span className="tabular-nums text-gray-700">{brl(val as number)}</span>
                </div>
              ))}
              <div className="flex justify-between pt-2 border-t border-border font-bold">
                <span>Saldo esperado em dinheiro</span>
                <span className="text-success-700 tabular-nums">{brl(resumoFechar.saldoEsperado)}</span>
              </div>
              <p className="text-xs text-gray-500">{resumoFechar.quantidadeVendas} venda(s) no período</p>
            </div>

            <div>
              <label className="text-xs text-gray-500 font-medium block mb-1">Valor contado na gaveta</label>
              <CurrencyInput
                value={valorInformadoFechamento}
                onChange={setValorContado}
                autoFocus={!contarFechamento}
                disabled={contarFechamento}
              />
              <button
                type="button"
                onClick={() => setContarFechamento(v => !v)}
                className="mt-2 inline-flex items-center gap-1.5 text-sm text-primary-600 hover:text-primary-700"
              >
                <Coins size={15} />
                {contarFechamento ? 'Informar só o valor total' : 'Conferir por cédulas e moedas'}
              </button>
              {contarFechamento && (
                <div className="mt-2">
                  <ContagemCedulas contagem={cedulasFechamento} onChange={setCedulasFechamento} />
                </div>
              )}
              {valorInformadoFechamento > 0 && (
                (() => {
                  const diff = valorInformadoFechamento - resumoFechar.saldoEsperado
                  if (Math.abs(diff) < 0.01) return <p className="text-xs text-success-700 mt-1">Confere com o esperado.</p>
                  return (
                    <p className={`text-xs mt-1 ${diff > 0 ? 'text-primary-600' : 'text-danger-600'}`}>
                      {diff > 0 ? 'Sobra' : 'Falta'} de {brl(Math.abs(diff))} em relação ao esperado.
                    </p>
                  )
                })()
              )}
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => { setShowFechar(false); setResumoFechar(null); setValorContado(0); setContarFechamento(false); setCedulasFechamento({}) }}
                className="flex-1 py-2.5 border border-gray-200 rounded-lg text-sm hover:bg-gray-100"
              >
                Cancelar
              </button>
              <button
                onClick={handleConfirmarFechamento}
                disabled={!valorInformadoFechamento || fechando}
                className="flex-1 py-2.5 bg-primary-600 hover:bg-primary-500 rounded-lg text-sm font-medium disabled:opacity-50 text-white"
              >
                {fechando ? 'Fechando...' : 'Confirmar Fechamento'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}