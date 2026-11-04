import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Search, Send, Sparkles, X } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'

interface Mensagem {
  autor: 'usuario' | 'assistente'
  texto: string
  consultas?: string[]
}

interface Resposta {
  resposta: string
  consultas: string[]
}

const SUGESTOES = ['Qual meu lucro hoje?', 'Quais produtos mais venderam este mês?', 'Quais produtos estão com estoque baixo?']

/**
 * Perguntas sobre os dados da empresa. Os números vêm das consultas do backend;
 * o texto da resposta é exibido como texto puro (nunca como HTML).
 */
export default function AssistenteChat() {
  const { sessao } = usePermissao()
  const [aberto, setAberto] = useState(false)
  const [pergunta, setPergunta] = useState('')
  const [mensagens, setMensagens] = useState<Mensagem[]>([])
  const fimRef = useRef<HTMLDivElement>(null)

  const { data: status } = useQuery<{ habilitado: boolean }>({
    queryKey: ['assistente-status'],
    queryFn: () => api.get('/assistente/status').then(r => r.data),
    staleTime: Infinity,
    enabled: !sessao?.somenteLeitura,
  })

  const perguntar = useMutation({
    mutationFn: (texto: string) =>
      api.post<Resposta>('/assistente/perguntar', {
        pergunta: texto,
        historico: mensagens.map(({ autor, texto }) => ({ autor, texto })),
      }, { timeout: 120_000 }).then(r => r.data),
    onSuccess: (r) => setMensagens(m => [...m, { autor: 'assistente', texto: r.resposta, consultas: r.consultas }]),
    // O aviso de erro já sai pelo interceptor; a pergunta sem resposta sai da conversa
    onError: () => setMensagens(m => m.slice(0, -1)),
  })

  useEffect(() => {
    fimRef.current?.scrollIntoView({ block: 'end' })
  }, [mensagens, perguntar.isPending])

  if (!status?.habilitado || sessao?.somenteLeitura) return null

  const enviar = (texto: string) => {
    const limpo = texto.trim()
    if (!limpo || perguntar.isPending) return
    setMensagens(m => [...m, { autor: 'usuario', texto: limpo }])
    setPergunta('')
    perguntar.mutate(limpo)
  }

  return (
    <>
      <button
        onClick={() => setAberto(true)}
        className="mr-3 inline-flex items-center gap-1.5 rounded-lg border border-violet-200 bg-violet-50 px-3 py-1.5 text-sm font-medium text-violet-700 hover:bg-violet-100 transition-colors"
      >
        <Sparkles size={15} />
        Assistente
      </button>

      {aberto && (
        <div className="fixed inset-0 z-40 flex justify-end bg-black/20" onClick={() => setAberto(false)}>
          <aside
            role="dialog"
            aria-label="Assistente"
            onClick={e => e.stopPropagation()}
            onKeyDown={e => { if (e.key === 'Escape') setAberto(false) }}
            className="flex h-full w-full max-w-md flex-col bg-white shadow-2xl"
          >
            <header className="flex items-center justify-between border-b border-gray-100 px-4 py-3">
              <h2 className="flex items-center gap-2 font-semibold text-gray-900">
                <Sparkles size={17} className="text-violet-600" /> Assistente
              </h2>
              <button onClick={() => setAberto(false)} aria-label="Fechar assistente" className="text-gray-400 hover:text-gray-700">
                <X size={18} />
              </button>
            </header>

            <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
              {mensagens.length === 0 && (
                <div className="space-y-2">
                  <p className="text-sm text-gray-600">Pergunte sobre vendas, lucro, custo de produtos e estoque.</p>
                  {SUGESTOES.map(s => (
                    <button
                      key={s}
                      onClick={() => enviar(s)}
                      className="block w-full rounded-lg border border-gray-200 px-3 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
                    >
                      {s}
                    </button>
                  ))}
                </div>
              )}

              {mensagens.map((m, i) => (
                <div key={i} className={m.autor === 'usuario' ? 'flex justify-end' : ''}>
                  <div
                    className={`max-w-[85%] whitespace-pre-wrap rounded-2xl px-3 py-2 text-sm ${
                      m.autor === 'usuario' ? 'bg-violet-600 text-white' : 'bg-gray-100 text-gray-900'
                    }`}
                  >
                    {m.texto}
                  </div>
                  {m.consultas && m.consultas.length > 0 && (
                    <p className="mt-1 flex items-center gap-1 text-xs text-gray-500">
                      <Search size={11} /> Consultou: {m.consultas.join(', ')}
                    </p>
                  )}
                </div>
              ))}

              {perguntar.isPending && <p className="text-sm text-gray-500">Consultando…</p>}
              <div ref={fimRef} />
            </div>

            <form
              onSubmit={e => { e.preventDefault(); enviar(pergunta) }}
              className="border-t border-gray-100 p-3"
            >
              <div className="flex gap-2">
                <input
                  value={pergunta}
                  onChange={e => setPergunta(e.target.value)}
                  maxLength={500}
                  autoFocus
                  placeholder="Ex.: qual o custo da picanha?"
                  className="flex-1 rounded-lg border border-gray-200 px-3 py-2 text-sm focus:border-violet-500 focus:outline-none"
                />
                <button
                  type="submit"
                  disabled={perguntar.isPending || !pergunta.trim()}
                  aria-label="Enviar pergunta"
                  className="rounded-lg bg-violet-600 px-3 text-white hover:bg-violet-700 disabled:opacity-40"
                >
                  <Send size={16} />
                </button>
              </div>
              <p className="mt-2 text-[11px] leading-snug text-gray-500">
                Respostas geradas por IA (Google Gemini) a partir dos dados do sistema; confira valores importantes nos
                relatórios. Não digite dados pessoais de clientes.
              </p>
            </form>
          </aside>
        </div>
      )}
    </>
  )
}
