import { useSearchParams } from 'react-router-dom'
import { ClipboardCheck } from 'lucide-react'
import { PageHeader, FilterTabs } from '@/shared/components/ui'
import OrdensAba from './pcp/OrdensAba'
import CargaAba from './pcp/CargaAba'

type Aba = 'ordens' | 'carga'

const ABAS: { value: Aba; label: string }[] = [
  { value: 'ordens', label: 'Ordens de produção' },
  { value: 'carga', label: 'Carga dos centros' },
]

/** PCP: ordens de produção e a carga que elas colocam nos centros de trabalho. */
export default function OrdensProducaoPage() {
  const [params, setParams] = useSearchParams()
  const aba = (ABAS.some(a => a.value === params.get('aba')) ? params.get('aba') : 'ordens') as Aba

  return (
    <div className="p-4 sm:p-6">
      <PageHeader title="Ordens de produção" icon={<ClipboardCheck size={22} />}
        subtitle="Planejamento, liberação e acompanhamento das OPs" />
      <div className="flex flex-wrap gap-2 mb-5" role="tablist" aria-label="PCP">
        <FilterTabs options={ABAS} value={aba} onChange={v => setParams({ aba: v }, { replace: true })} />
      </div>
      {aba === 'ordens' && <OrdensAba />}
      {aba === 'carga' && <CargaAba />}
    </div>
  )
}
