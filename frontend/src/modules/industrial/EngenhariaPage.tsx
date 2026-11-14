import { useSearchParams } from 'react-router-dom'
import { Layers } from 'lucide-react'
import { PageHeader, FilterTabs } from '@/shared/components/ui'
import EstruturasAba from './engenharia/EstruturasAba'
import RoteirosAba from './engenharia/RoteirosAba'
import FichasAba from './engenharia/FichasAba'

type Aba = 'estruturas' | 'roteiros' | 'fichas'

const ABAS: { value: Aba; label: string }[] = [
  { value: 'estruturas', label: 'Estruturas (BOM)' },
  { value: 'roteiros', label: 'Roteiros' },
  { value: 'fichas', label: 'Fichas técnicas' },
]

/**
 * Engenharia de produto: o que entra (estrutura), como se fabrica (roteiro) e os dados técnicos (ficha).
 * Estrutura e roteiro são versionados: só o rascunho é editado, e a versão ativa é a que a OP usa na liberação.
 */
export default function EngenhariaPage() {
  const [params, setParams] = useSearchParams()
  const aba = (ABAS.some(a => a.value === params.get('aba')) ? params.get('aba') : 'estruturas') as Aba

  return (
    <div className="p-4 sm:p-6">
      <PageHeader title="Engenharia de produto" icon={<Layers size={22} />}
        subtitle="Estruturas de produto, roteiros de fabricação e fichas técnicas" />
      <div className="flex flex-wrap gap-2 mb-5" role="tablist" aria-label="Engenharia de produto">
        <FilterTabs options={ABAS} value={aba} onChange={v => setParams({ aba: v }, { replace: true })} />
      </div>
      {aba === 'estruturas' && <EstruturasAba />}
      {aba === 'roteiros' && <RoteirosAba />}
      {aba === 'fichas' && <FichasAba />}
    </div>
  )
}
