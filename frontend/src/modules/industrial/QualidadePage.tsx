import { useSearchParams } from 'react-router-dom'
import { ShieldCheck } from 'lucide-react'
import { PageHeader, FilterTabs } from '@/shared/components/ui'
import LotesQualidadeAba from './qualidade/LotesQualidadeAba'
import NaoConformidadesAba from './qualidade/NaoConformidadesAba'
import PlanosAba from './qualidade/PlanosAba'
import RastreabilidadeAba from './qualidade/RastreabilidadeAba'

type Aba = 'lotes' | 'ncs' | 'planos' | 'rastreio'

const ABAS: { value: Aba; label: string }[] = [
  { value: 'lotes', label: 'Lotes' },
  { value: 'ncs', label: 'Não conformidades' },
  { value: 'planos', label: 'Planos de inspeção' },
  { value: 'rastreio', label: 'Rastreabilidade' },
]

/** Qualidade e rastreabilidade: situação dos lotes, inspeções, não conformidades e o caminho da origem ao cliente. */
export default function QualidadePage() {
  const [params, setParams] = useSearchParams()
  const aba = (ABAS.some(a => a.value === params.get('aba')) ? params.get('aba') : 'lotes') as Aba
  const lote = params.get('lote') ?? undefined
  return (
    <div className="p-4 sm:p-6">
      <PageHeader title="Qualidade e rastreabilidade" icon={<ShieldCheck size={22} />} subtitle="Lotes, inspeções, não conformidades e rastreio da origem ao cliente" />
      <div className="flex flex-wrap gap-2 mb-5" role="tablist" aria-label="Qualidade">
        <FilterTabs options={ABAS} value={aba} onChange={v => setParams({ aba: v }, { replace: true })} />
      </div>
      {aba === 'lotes' && <LotesQualidadeAba onRastrear={c => setParams({ aba: 'rastreio', lote: c })} />}
      {aba === 'ncs' && <NaoConformidadesAba />}
      {aba === 'planos' && <PlanosAba />}
      {aba === 'rastreio' && <RastreabilidadeAba codigoInicial={lote} />}
    </div>
  )
}
