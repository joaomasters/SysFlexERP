import { useSearchParams } from 'react-router-dom'
import { BarChart3 } from 'lucide-react'
import { PageHeader, FilterTabs } from '@/shared/components/ui'
import IndicadoresAba from './gestao/IndicadoresAba'
import CustosAba from './gestao/CustosAba'
import MrpAba from './gestao/MrpAba'

type Aba = 'indicadores' | 'custos' | 'mrp'

const ABAS: { value: Aba; label: string }[] = [
  { value: 'indicadores', label: 'Indicadores' },
  { value: 'custos', label: 'Custos por OP' },
  { value: 'mrp', label: 'MRP' },
]

/** Gestão industrial: indicadores do período, custo estimado × real das OPs e sugestões do MRP. */
export default function GestaoPage() {
  const [params, setParams] = useSearchParams()
  const aba = (ABAS.some(a => a.value === params.get('aba')) ? params.get('aba') : 'indicadores') as Aba
  return (
    <div className="p-4 sm:p-6">
      <PageHeader title="Gestão industrial" icon={<BarChart3 size={22} />} subtitle="Indicadores, custos das OPs e planejamento de materiais (MRP)" />
      <div className="flex flex-wrap gap-2 mb-5" role="tablist" aria-label="Gestão industrial">
        <FilterTabs options={ABAS} value={aba} onChange={v => setParams({ aba: v }, { replace: true })} />
      </div>
      {aba === 'indicadores' && <IndicadoresAba />}
      {aba === 'custos' && <CustosAba />}
      {aba === 'mrp' && <MrpAba />}
    </div>
  )
}
