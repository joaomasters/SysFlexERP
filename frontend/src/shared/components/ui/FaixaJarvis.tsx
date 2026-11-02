import { Eye } from 'lucide-react'
import { encerrarVisualizacao, getSessao } from '../../auth'

/** Faixa fixa enquanto o JARVIS visualiza uma empresa. Para sessões normais não renderiza nada. */
export default function FaixaJarvis() {
  const sessao = getSessao()
  if (!sessao?.somenteLeitura) return null

  const voltar = () => {
    encerrarVisualizacao()
    window.location.href = '/jarvis'
  }

  return (
    <div className="bg-violet-700 text-white text-xs font-semibold py-1.5 px-3 flex items-center justify-center gap-2 shrink-0">
      <Eye size={13} />
      JARVIS — somente leitura: {sessao.empresa?.codigo} · {sessao.empresa?.nome}
      <button onClick={voltar} className="ml-2 underline hover:no-underline">
        Voltar ao JARVIS
      </button>
    </div>
  )
}
