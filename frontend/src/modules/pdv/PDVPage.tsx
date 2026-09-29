import { useState, useEffect } from 'react'
import { ShoppingBag, Wifi, WifiOff, Trash2, Plus, User, LockOpen, Lock } from 'lucide-react'
import { useBarcodeScan } from '@/shared/hooks/useBarcodeScan'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { getNomeUsuario } from '@/shared/auth'
import { api } from '@/shared/api/axios'
import { usePdv } from './hooks/usePdv'
import ListaItens from './components/ListaItens'
import ModalPagamento from './components/ModalPagamento'
import NovaComandaModal from './components/NovaComandaModal'
import { formatBRL } from '@/shared/utils/mask'
import { CurrencyInput } from '@/shared/components/ui'

// NOTA DE DESIGN: o PDV usa de propósito um tema escuro em tela cheia
// ("console de caixa"), diferente do tema claro do back-office (financeiro,
// estoque, etc.). Isso é um padrão comum em sistemas de ponto-de-venda —
// alto contraste para operação rápida em balcão, teclas de atalho (F10),
// texto grande no total. As cores dentro do PDV seguem seu próprio código
// interno consistente: esmeralda = ação positiva/confirmar, vermelho =
// cancelar/perigo, azul = informativo, âmbar = atenção — e os campos de
// valor usam a MESMA lógica de máscara do resto do sistema
// (CurrencyInput com a variante `dark`), só que com uma pele escura.
const brl = formatBRL

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
    adicionarItem, removerItem, fecharVenda, cancelarVenda,
    iniciarVenda, selecionarComanda, abrirCaixa, fecharCaixa,
  } = usePdv()

  const [showPagto, setShowPagto] = useState(false)
  const [showNovaComanda, setShowNovaComanda] = useState(false)
  const [hora, setHora]           = useState(new Date())
  const [valorAbertura, setValorAbertura] = useState(0)
  const [abrindo, setAbrindo]     = useState(false)

  const [showFechar, setShowFechar]       = useState(false)
  const [resumoFechar, setResumoFechar]   = useState<FechamentoResumo | null>(null)
  const [valorContado, setValorContado]   = useState(0)
  const [fechando, setFechando]           = useState(false)

  const nomeOperador = getNomeUsuario()
  const { podeExcluir } = usePermissao()
  // Cancelar uma venda é restrito a quem tem a permissão de EXCLUIR no
  // módulo PDV — por padrão, só ADMIN e SUPER_ADMIN (ver matriz de perfis).
  // O botão fica visível mas desabilitado pro operador entender que a
  // função existe, só que precisa de um administrador.
  const podeCancelarVenda = podeExcluir('PDV')

  // Relógio
  useEffect(() => {
    const t = setInterval(() => setHora(new Date()), 1000)
    return () => clearInterval(t)
  }, [])

  // Atalhos de teclado (F10 = Finalizar, Escape = Cancelar)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'F10') { e.preventDefault(); setShowPagto(true) }
      if (e.key === 'Escape') { e.preventDefault(); /* abre confirmação */ }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  // Listener global do leitor de código de barras
  useBarcodeScan({
    onScan: adicionarItem,
    enabled: !showPagto && !semCaixa,
  })

  const handleAbrirCaixa = async () => {
    setAbrindo(true)
    try {
      await abrirCaixa(valorAbertura)
      setValorAbertura(0)
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

  const handleConfirmarFechamento = async () => {
    setFechando(true)
    try {
      await fecharCaixa(valorContado)
      setShowFechar(false)
      setResumoFechar(null)
      setValorContado(0)
    } finally {
      setFechando(false)
    }
  }

  return (
    <div className="h-screen flex flex-col bg-gray-950 text-white select-none overflow-hidden">

      {/* ── Topbar ── */}
      <header className="flex items-center justify-between px-6 py-3 bg-gray-900 border-b border-gray-800">
        <div className="flex items-center gap-3">
          <span className="font-bold text-lg">PDV</span>
          {nomeOperador && (
            <span className="flex items-center gap-1.5 text-xs bg-gray-800 text-gray-300 px-2.5 py-1 rounded-full">
              <User size={12} /> {nomeOperador}
            </span>
          )}
          {venda && (
            <span className="text-xs bg-gray-700 text-gray-300 px-2 py-0.5 rounded-full">
              Cupom #{venda.id}
            </span>
          )}
          {scanLoading && (
            <span className="text-xs bg-yellow-900 text-yellow-300 px-2 py-0.5 rounded-full animate-pulse">
              Processando...
            </span>
          )}
        </div>
        <div className="flex items-center gap-4 text-sm text-gray-400">
          {caixaId && (
            <button
              onClick={handleAbrirFechamento}
              className="flex items-center gap-1.5 text-xs bg-gray-800 hover:bg-gray-700 text-gray-300 px-2.5 py-1.5 rounded-lg"
            >
              <Lock size={12} /> Fechar Caixa
            </button>
          )}
          {scanLoading
            ? <WifiOff size={16} className="text-yellow-500 animate-pulse" />
            : <Wifi size={16} className="text-emerald-500" />}
          <span className="tabular-nums">
            {hora.toLocaleTimeString('pt-BR')}
          </span>
          <span>{hora.toLocaleDateString('pt-BR')}</span>
        </div>
      </header>

      {/* ── Sem caixa aberto: bloqueia o PDV até abrir um ── */}
      {semCaixa && (
        <div className="flex-1 flex items-center justify-center">
          <div className="bg-gray-900 border border-gray-800 rounded-2xl p-8 w-full max-w-sm text-center space-y-4">
            <LockOpen size={32} className="mx-auto text-amber-500" />
            <div>
              <h2 className="font-bold text-lg">Nenhum caixa aberto</h2>
              <p className="text-sm text-gray-400 mt-1">
                {nomeOperador ? `${nomeOperador}, abra` : 'Abra'} um caixa informando o valor inicial (fundo de troco).
              </p>
            </div>
            <CurrencyInput
              value={valorAbertura}
              onChange={setValorAbertura}
              dark
              autoFocus
              className="[&_input]:text-lg [&_input]:text-center"
            />
            <button
              onClick={handleAbrirCaixa}
              disabled={!valorAbertura || abrindo}
              className="w-full py-3 bg-emerald-600 hover:bg-emerald-500 rounded-xl font-bold disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {abrindo ? 'Abrindo...' : 'Abrir Caixa'}
            </button>
          </div>
        </div>
      )}

      {/* ── Barra de Comandas ── */}
      {!semCaixa && (
      <div className="flex items-center gap-2 px-4 py-2 bg-gray-900/60 border-b border-gray-800 overflow-x-auto shrink-0">
        <button
          onClick={() => setShowNovaComanda(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
                     bg-talho-600 hover:bg-talho-400 transition-colors shrink-0"
        >
          <Plus size={14} /> Nova Comanda
        </button>

        {comandas.length === 0 && (
          <span className="text-xs text-gray-600 px-2">Nenhuma comanda aberta</span>
        )}

        {comandas.map(c => (
          <button
            key={c.id}
            onClick={() => selecionarComanda(c.id)}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium shrink-0 transition-colors
              ${venda?.id === c.id
                ? 'bg-emerald-700 text-white'
                : 'bg-gray-800 text-gray-300 hover:bg-gray-700'}`}
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
        <div className="flex-1 flex flex-col overflow-hidden border-r border-gray-800">
          <ListaItens
            itens={venda?.itens ?? []}
            onRemover={removerItem}
          />
          {/* Rodapé da lista */}
          <div className="px-4 py-3 bg-gray-900 border-t border-gray-800 flex justify-between text-sm text-gray-400">
            <span>{venda?.itens?.length ?? 0} iten(s)</span>
            {venda?.desconto && venda.desconto > 0 && (
              <span>Desconto: -{brl(venda.desconto)}</span>
            )}
            <span>Subtotal: <span className="text-white font-medium tabular-nums">{brl(venda?.subtotal ?? 0)}</span></span>
          </div>
        </div>

        {/* Coluna direita: totais e ações */}
        <div className="w-72 flex flex-col gap-4 p-5 bg-gray-900">

          {/* Total */}
          <div className="bg-gray-800 rounded-2xl p-6 text-center shadow-inner">
            <p className="text-gray-400 text-xs uppercase tracking-widest mb-2">Total a Pagar</p>
            <p className="text-5xl font-black tabular-nums text-emerald-400 leading-none">
              {brl(totalVenda)}
            </p>
          </div>

          {/* Botões */}
          <button
            onClick={() => setShowPagto(true)}
            disabled={!venda?.itens?.length || loading}
            className="py-5 rounded-2xl text-xl font-bold
                       bg-emerald-600 hover:bg-emerald-500 active:scale-95
                       disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed
                       transition-all duration-150"
          >
            FINALIZAR
            <span className="block text-xs font-normal opacity-70 mt-0.5">[F10]</span>
          </button>

          <button
            onClick={cancelarVenda}
            disabled={!venda || !podeCancelarVenda}
            title={!podeCancelarVenda ? 'Só um administrador pode cancelar uma venda' : undefined}
            className="py-3 rounded-xl text-sm font-medium
                       bg-red-900/60 hover:bg-red-800 text-red-300
                       disabled:bg-gray-800 disabled:text-gray-600 disabled:cursor-not-allowed
                       flex items-center justify-center gap-2 transition-colors"
          >
            <Trash2 size={15} />
            CANCELAR VENDA
          </button>
          {venda && !podeCancelarVenda && (
            <p className="text-center text-[11px] text-gray-600 -mt-2">
              Cancelamento restrito a administradores
            </p>
          )}

          <div className="mt-auto text-center text-xs text-gray-600">
            Leitor de barcode ativo
          </div>
        </div>
      </div>
      )}

      {/* Modal de pagamento */}
      {showPagto && (
        <ModalPagamento
          totalVenda={totalVenda}
          vendaId={venda?.id}
          onConfirmar={async (pagamentos) => {
            await fecharVenda(pagamentos)
            setShowPagto(false)
          }}
          onCancelar={() => setShowPagto(false)}
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
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-gray-900 border border-gray-800 rounded-2xl w-full max-w-sm p-6 space-y-4">
            <h2 className="text-lg font-bold flex items-center gap-2">
              <Lock size={18} /> Fechar Caixa
            </h2>
            <div className="space-y-1.5 text-sm">
              {[
                ['Abertura', resumoFechar.valorAbertura],
                ['Vendas em dinheiro', resumoFechar.totalDinheiro],
                ['Suprimento (+)', resumoFechar.totalSuprimento],
                ['Sangria (-)', resumoFechar.totalSangria],
              ].map(([label, val]) => (
                <div key={label as string} className="flex justify-between text-gray-400">
                  <span>{label}</span>
                  <span className="tabular-nums text-gray-200">{brl(val as number)}</span>
                </div>
              ))}
              <div className="flex justify-between pt-2 border-t border-gray-800 font-bold">
                <span>Saldo esperado em dinheiro</span>
                <span className="text-emerald-400 tabular-nums">{brl(resumoFechar.saldoEsperado)}</span>
              </div>
              <p className="text-xs text-gray-500">{resumoFechar.quantidadeVendas} venda(s) no período</p>
            </div>

            <div>
              <label className="text-xs text-gray-400 font-medium block mb-1">Valor contado na gaveta</label>
              <CurrencyInput value={valorContado} onChange={setValorContado} dark autoFocus />
              {valorContado > 0 && (
                (() => {
                  const diff = valorContado - resumoFechar.saldoEsperado
                  if (Math.abs(diff) < 0.01) return <p className="text-xs text-emerald-400 mt-1">Confere com o esperado.</p>
                  return (
                    <p className={`text-xs mt-1 ${diff > 0 ? 'text-blue-400' : 'text-red-400'}`}>
                      {diff > 0 ? 'Sobra' : 'Falta'} de {brl(Math.abs(diff))} em relação ao esperado.
                    </p>
                  )
                })()
              )}
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => { setShowFechar(false); setResumoFechar(null); setValorContado(0) }}
                className="flex-1 py-2.5 border border-gray-700 rounded-lg text-sm hover:bg-gray-800"
              >
                Cancelar
              </button>
              <button
                onClick={handleConfirmarFechamento}
                disabled={!valorContado || fechando}
                className="flex-1 py-2.5 bg-talho-600 hover:bg-talho-400 rounded-lg text-sm font-medium disabled:opacity-50"
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