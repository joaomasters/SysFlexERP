import { useSearchParams } from 'react-router-dom'
import { Flame } from 'lucide-react'
import { PageHeader, FilterTabs } from '@/shared/components/ui'
import LigasAba from './extrusao/LigasAba'
import LotesAba from './extrusao/LotesAba'
import ReceitasAba from './extrusao/ReceitasAba'
import MedicoesAba from './extrusao/MedicoesAba'

type Aba = 'lotes' | 'ligas' | 'receitas' | 'medicoes'

const ABAS: { value: Aba; label: string }[] = [
  { value: 'lotes', label: 'Lotes de matéria-prima' },
  { value: 'ligas', label: 'Ligas' },
  { value: 'receitas', label: 'Receitas de processo' },
  { value: 'medicoes', label: 'Medições' },
]

/** Extrusão: ligas, lotes de tarugo com certificado, receitas por liga e máquina, e medições de processo. */
export default function ExtrusaoPage() {
  const [params, setParams] = useSearchParams()
  const aba = (ABAS.some(a => a.value === params.get('aba')) ? params.get('aba') : 'lotes') as Aba
  return (
    <div className="p-4 sm:p-6">
      <PageHeader title="Extrusão" icon={<Flame size={22} />} subtitle="Ligas, lotes de matéria-prima, receitas de processo e medições" />
      <div className="flex flex-wrap gap-2 mb-5" role="tablist" aria-label="Extrusão">
        <FilterTabs options={ABAS} value={aba} onChange={v => setParams({ aba: v }, { replace: true })} />
      </div>
      {aba === 'lotes' && <LotesAba />}
      {aba === 'ligas' && <LigasAba />}
      {aba === 'receitas' && <ReceitasAba />}
      {aba === 'medicoes' && <MedicoesAba />}
    </div>
  )
}
