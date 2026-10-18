import { FlaskConical } from 'lucide-react'
import { AMBIENTE, EH_PRODUCAO } from '../../api/ambiente'

/**
 * Faixa fixa no topo quando o build NÃO é de produção (VITE_AMBIENTE), para
 * ninguém confundir o ambiente de testes com o sistema real. Em produção não
 * renderiza nada.
 */
export default function FaixaAmbiente() {
  if (EH_PRODUCAO) return null
  return (
    <div className="bg-amber-400 text-amber-950 text-xs font-semibold text-center py-1 px-3 flex items-center justify-center gap-1.5 shrink-0">
      <FlaskConical size={13} />
      AMBIENTE DE {AMBIENTE === 'development' ? 'DESENVOLVIMENTO' : AMBIENTE.toUpperCase()} — dados de teste, não use para vendas reais
    </div>
  )
}
