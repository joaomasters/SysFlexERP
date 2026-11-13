import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { Factory } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { PageHeader, FilterTabs, StatusBadge } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import {
  STATUS_FERRAMENTA, TIPOS_FERRAMENTA,
  type Centro, type Ferramenta, type Setor, type StatusFerramenta, type TipoMaquina, type Turno, type Unidade,
} from '@/types/industrial'
import CadastroSimples, { MODULO, numeroOuNulo, paraTexto, textoOuNulo } from './estrutura/CadastroSimples'
import TiposMaquinaAba from './estrutura/TiposMaquinaAba'
import MaquinasAba from './estrutura/MaquinasAba'
import AlocacoesAba from './estrutura/AlocacoesAba'

type Aba = 'unidades' | 'setores' | 'centros' | 'turnos' | 'tipos' | 'maquinas' | 'ferramentas' | 'operadores'

const ABAS: { value: Aba; label: string }[] = [
  { value: 'unidades', label: 'Unidades' },
  { value: 'setores', label: 'Setores' },
  { value: 'centros', label: 'Centros de trabalho' },
  { value: 'turnos', label: 'Turnos' },
  { value: 'tipos', label: 'Tipos de máquina' },
  { value: 'maquinas', label: 'Máquinas' },
  { value: 'ferramentas', label: 'Ferramentas e matrizes' },
  { value: 'operadores', label: 'Operadores' },
]

const brl = (v?: number | null) => (v == null ? '—' : Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }))
const DIAS = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom']
const dias = (d: string) => d.split('').map((c, i) => (c === '1' ? DIAS[i] : null)).filter(Boolean).join(', ')

/**
 * Estrutura industrial: onde e com o que a fábrica produz. A ordem das abas é a ordem de
 * cadastro: unidade → setor → centro de trabalho → turnos → tipos de máquina → máquinas.
 */
export default function EstruturaIndustrialPage() {
  const [params, setParams] = useSearchParams()
  const aba = (ABAS.some(a => a.value === params.get('aba')) ? params.get('aba') : 'unidades') as Aba

  return (
    <div className="p-4 sm:p-6">
      <PageHeader title="Estrutura industrial" icon={<Factory size={22} />}
        subtitle="Unidades, setores, centros de trabalho, turnos, máquinas, ferramentas e operadores" />
      <div className="flex flex-wrap gap-2 mb-5" role="tablist" aria-label="Cadastros da estrutura industrial">
        <FilterTabs options={ABAS} value={aba} onChange={v => setParams({ aba: v }, { replace: true })} />
      </div>
      {aba === 'unidades' && <AbaUnidades />}
      {aba === 'setores' && <AbaSetores />}
      {aba === 'centros' && <AbaCentros />}
      {aba === 'turnos' && <AbaTurnos />}
      {aba === 'tipos' && <TiposMaquinaAba />}
      {aba === 'maquinas' && <MaquinasAba />}
      {aba === 'ferramentas' && <AbaFerramentas />}
      {aba === 'operadores' && <AlocacoesAba />}
    </div>
  )
}

function AbaUnidades() {
  return (
    <CadastroSimples<Unidade>
      nome="unidade" feminino endpoint="/industrial/unidades" queryKey="ind-unidades"
      textoVazio="Nenhuma unidade cadastrada. Comece pela fábrica."
      busca={u => `${u.codigo} ${u.nome} ${u.cidade ?? ''}`}
      colunas={[
        { titulo: 'Código', render: u => u.codigo, className: 'font-mono font-semibold' },
        { titulo: 'Nome', render: u => u.nome, className: 'font-medium' },
        { titulo: 'Cidade', render: u => (u.cidade ? `${u.cidade}${u.uf ? `/${u.uf}` : ''}` : '—'), className: 'text-gray-600' },
      ]}
      campos={[
        { nome: 'codigo', rotulo: 'Código', obrigatorio: true, max: 20 },
        { nome: 'nome', rotulo: 'Nome', obrigatorio: true, max: 120 },
        { nome: 'cidade', rotulo: 'Cidade', max: 60 },
        { nome: 'uf', rotulo: 'UF', max: 2 },
      ]}
      paraForm={u => ({ codigo: u?.codigo ?? '', nome: u?.nome ?? '', cidade: u?.cidade ?? '', uf: u?.uf ?? '' })}
      paraPayload={f => ({ codigo: f.codigo, nome: f.nome, cidade: textoOuNulo(f.cidade), uf: textoOuNulo(f.uf) })}
    />
  )
}

function AbaSetores() {
  const { data: unidades = [] } = useQuery<Unidade[]>({
    queryKey: ['ind-unidades'], queryFn: () => api.get('/industrial/unidades').then(r => r.data),
  })
  return (
    <CadastroSimples<Setor>
      nome="setor" endpoint="/industrial/setores" queryKey="ind-setores"
      busca={s => `${s.codigo} ${s.nome} ${s.unidadeNome}`}
      colunas={[
        { titulo: 'Código', render: s => s.codigo, className: 'font-mono font-semibold' },
        { titulo: 'Nome', render: s => s.nome, className: 'font-medium' },
        { titulo: 'Unidade', render: s => s.unidadeNome, className: 'text-gray-600' },
      ]}
      campos={[
        { nome: 'unidadeId', rotulo: 'Unidade', obrigatorio: true, largo: true,
          opcoes: unidades.filter(u => u.ativo).map(u => ({ valor: u.id, rotulo: `${u.codigo} · ${u.nome}` })) },
        { nome: 'codigo', rotulo: 'Código', obrigatorio: true, max: 20 },
        { nome: 'nome', rotulo: 'Nome', obrigatorio: true, max: 120 },
      ]}
      paraForm={s => ({ unidadeId: paraTexto(s?.unidadeId), codigo: s?.codigo ?? '', nome: s?.nome ?? '' })}
      paraPayload={f => ({ unidadeId: Number(f.unidadeId), codigo: f.codigo, nome: f.nome })}
    />
  )
}

function AbaCentros() {
  const { data: setores = [] } = useQuery<Setor[]>({
    queryKey: ['ind-setores'], queryFn: () => api.get('/industrial/setores').then(r => r.data),
  })
  return (
    <CadastroSimples<Centro>
      nome="centro de trabalho" endpoint="/industrial/centros" queryKey="ind-centros"
      busca={c => `${c.codigo} ${c.nome} ${c.setorNome} ${c.unidadeNome}`}
      colunas={[
        { titulo: 'Código', render: c => c.codigo, className: 'font-mono font-semibold' },
        { titulo: 'Nome', render: c => c.nome, className: 'font-medium' },
        { titulo: 'Setor', render: c => `${c.setorNome} · ${c.unidadeNome}`, className: 'text-gray-600' },
        { titulo: 'Custo/hora', render: c => brl(c.custoHora), className: 'tabular-nums' },
      ]}
      campos={[
        { nome: 'setorId', rotulo: 'Setor', obrigatorio: true, largo: true,
          opcoes: setores.filter(s => s.ativo).map(s => ({ valor: s.id, rotulo: `${s.nome} (${s.unidadeNome})` })) },
        { nome: 'codigo', rotulo: 'Código', obrigatorio: true, max: 20 },
        { nome: 'nome', rotulo: 'Nome', obrigatorio: true, max: 120 },
        { nome: 'custoHora', rotulo: 'Custo por hora (R$)', tipo: 'numero',
          dica: 'Mão de obra e custos indiretos do centro; usado depois no custo das ordens' },
      ]}
      paraForm={c => ({ setorId: paraTexto(c?.setorId), codigo: c?.codigo ?? '', nome: c?.nome ?? '', custoHora: paraTexto(c?.custoHora) })}
      paraPayload={f => ({ setorId: Number(f.setorId), codigo: f.codigo, nome: f.nome, custoHora: numeroOuNulo(f.custoHora) })}
    />
  )
}

function AbaTurnos() {
  return (
    <CadastroSimples<Turno>
      nome="turno" endpoint="/industrial/turnos" queryKey="ind-turnos"
      busca={t => `${t.codigo} ${t.nome}`}
      colunas={[
        { titulo: 'Código', render: t => t.codigo, className: 'font-mono font-semibold' },
        { titulo: 'Nome', render: t => t.nome, className: 'font-medium' },
        { titulo: 'Horário', render: t => `${t.horaInicio.slice(0, 5)} às ${t.horaFim.slice(0, 5)}`, className: 'tabular-nums' },
        { titulo: 'Duração', render: t => `${Math.floor(t.minutos / 60)}h${t.minutos % 60 ? String(t.minutos % 60).padStart(2, '0') : ''}`, className: 'tabular-nums' },
        { titulo: 'Dias', render: t => dias(t.diasSemana), className: 'text-gray-600 text-xs' },
      ]}
      campos={[
        { nome: 'codigo', rotulo: 'Código', obrigatorio: true, max: 20 },
        { nome: 'nome', rotulo: 'Nome', obrigatorio: true, max: 60 },
        { nome: 'horaInicio', rotulo: 'Início', tipo: 'hora', obrigatorio: true },
        { nome: 'horaFim', rotulo: 'Fim', tipo: 'hora', obrigatorio: true, dica: 'Antes do início = turno que vira a meia-noite' },
        { nome: 'diasSemana', rotulo: 'Dias da semana', tipo: 'dias' },
      ]}
      paraForm={t => ({
        codigo: t?.codigo ?? '', nome: t?.nome ?? '', horaInicio: t?.horaInicio?.slice(0, 5) ?? '',
        horaFim: t?.horaFim?.slice(0, 5) ?? '', diasSemana: t?.diasSemana ?? '1111100',
      })}
      paraPayload={f => ({ codigo: f.codigo, nome: f.nome, horaInicio: f.horaInicio, horaFim: f.horaFim, diasSemana: f.diasSemana })}
    />
  )
}

function AbaFerramentas() {
  const qc = useQueryClient()
  const { podeEditar } = usePermissao()
  const { data: tipos = [] } = useQuery<TipoMaquina[]>({
    queryKey: ['ind-tipos-maquina'], queryFn: () => api.get('/industrial/tipos-maquina').then(r => r.data),
  })
  const status = useMutation({
    mutationFn: ({ id, novo }: { id: number; novo: StatusFerramenta }) => api.post(`/industrial/ferramentas/${id}/status`, { status: novo }),
    onSuccess: () => { toast.success('Status alterado.'); qc.invalidateQueries({ queryKey: ['ind-ferramentas'] }) },
  })
  return (
    <CadastroSimples<Ferramenta>
      nome="ferramenta" feminino endpoint="/industrial/ferramentas" queryKey="ind-ferramentas" inativavel={false}
      busca={f => `${f.codigo} ${f.revisao ?? ''} ${f.descricao}`}
      colunas={[
        { titulo: 'Código', render: f => `${f.codigo}${f.revisao ? ` rev. ${f.revisao}` : ''}`, className: 'font-mono font-semibold' },
        { titulo: 'Descrição', render: f => f.descricao, className: 'font-medium' },
        { titulo: 'Tipo', render: f => `${TIPOS_FERRAMENTA[f.tipo]}${f.tipoMaquinaNome ? ` · ${f.tipoMaquinaNome}` : ''}`, className: 'text-gray-600' },
        { titulo: 'Vida útil', className: 'tabular-nums text-gray-600', render: f => f.vidaUtilCiclos
          ? `${f.ciclosUtilizados.toLocaleString('pt-BR')} / ${f.vidaUtilCiclos.toLocaleString('pt-BR')} (${f.percentualVidaUsada}%)`
          : `${f.ciclosUtilizados.toLocaleString('pt-BR')} ciclos` },
        { titulo: 'Status', render: f => <StatusBadge tone={STATUS_FERRAMENTA[f.status].tom}>{STATUS_FERRAMENTA[f.status].rotulo}</StatusBadge> },
      ]}
      acoesExtras={f => podeEditar(MODULO) && f.status !== 'DESCARTADA' ? (
        <select aria-label={`Mudar status de ${f.codigo}`} value="" className="text-xs border border-gray-300 rounded-lg px-2 py-1.5"
          onChange={e => {
            const novo = e.target.value as StatusFerramenta
            if (novo === 'DESCARTADA' && !window.confirm(`Descartar ${f.codigo}? Ferramenta descartada não volta a ser usada.`)) return
            status.mutate({ id: f.id, novo })
          }}>
          <option value="">Mudar status...</option>
          {Object.entries(STATUS_FERRAMENTA).filter(([v]) => v !== f.status).map(([v, s]) => <option key={v} value={v}>{s.rotulo}</option>)}
        </select>
      ) : null}
      campos={[
        { nome: 'codigo', rotulo: 'Código', obrigatorio: true, max: 30 },
        { nome: 'revisao', rotulo: 'Revisão', max: 10, dica: 'Revisão nova da matriz = cadastro novo' },
        { nome: 'descricao', rotulo: 'Descrição', obrigatorio: true, max: 150, largo: true },
        { nome: 'tipo', rotulo: 'Tipo', obrigatorio: true,
          opcoes: Object.entries(TIPOS_FERRAMENTA).map(([v, r]) => ({ valor: v, rotulo: r })) },
        { nome: 'tipoMaquinaId', rotulo: 'Usada em (tipo de máquina)',
          opcoes: tipos.filter(t => t.ativo).map(t => ({ valor: t.id, rotulo: t.nome })) },
        { nome: 'vidaUtilCiclos', rotulo: 'Vida útil (ciclos)', tipo: 'numero' },
        { nome: 'ciclosUtilizados', rotulo: 'Ciclos já utilizados', tipo: 'numero' },
        { nome: 'custoAquisicao', rotulo: 'Custo de aquisição (R$)', tipo: 'numero' },
        { nome: 'observacao', rotulo: 'Observação', tipo: 'area' },
      ]}
      paraForm={f => ({
        codigo: f?.codigo ?? '', revisao: f?.revisao ?? '', descricao: f?.descricao ?? '', tipo: f?.tipo ?? '',
        tipoMaquinaId: paraTexto(f?.tipoMaquinaId), vidaUtilCiclos: paraTexto(f?.vidaUtilCiclos),
        ciclosUtilizados: paraTexto(f?.ciclosUtilizados ?? 0), custoAquisicao: paraTexto(f?.custoAquisicao), observacao: f?.observacao ?? '',
      })}
      paraPayload={f => ({
        codigo: f.codigo, revisao: textoOuNulo(f.revisao), descricao: f.descricao, tipo: f.tipo,
        tipoMaquinaId: numeroOuNulo(f.tipoMaquinaId), vidaUtilCiclos: numeroOuNulo(f.vidaUtilCiclos),
        ciclosUtilizados: numeroOuNulo(f.ciclosUtilizados), custoAquisicao: numeroOuNulo(f.custoAquisicao),
        observacao: textoOuNulo(f.observacao),
      })}
    />
  )
}

