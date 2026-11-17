import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { Button, StatusBadge, Table, THead, TH, TBody, TR, TD, LoadingState, baseInputClass } from '@/shared/components/ui'
import {
  SITUACAO_LOTE, STATUS_LOTE_MP,
  type DestinoLote, type LoteResumo, type RastreioLote, type RastreioLoteMp, type ResultadoBusca,
} from '@/types/industrial'
import { dataBR, dataHoraBR, num } from '../comum'

type Alvo = { tipo: 'LOTE'; codigo: string } | { tipo: 'LOTE_MP'; id: number }

/**
 * Rastreabilidade da origem ao cliente: a partir de um lote de produto (para trás até a corrida do tarugo e
 * para frente até o cliente) ou de um lote de matéria-prima (todas as OPs, lotes e clientes que ele alcançou).
 */
export default function RastreabilidadeAba({ codigoInicial }: { codigoInicial?: string }) {
  const [texto, setTexto] = useState(codigoInicial ?? '')
  const [termo, setTermo] = useState('')
  const [alvo, setAlvo] = useState<Alvo | null>(codigoInicial ? { tipo: 'LOTE', codigo: codigoInicial } : null)
  useEffect(() => { if (codigoInicial) { setTexto(codigoInicial); setAlvo({ tipo: 'LOTE', codigo: codigoInicial }) } }, [codigoInicial])

  const { data: resultados, isFetching } = useQuery<ResultadoBusca[]>({
    queryKey: ['ind-rastreio-busca', termo], enabled: termo.length >= 2,
    queryFn: () => api.get('/industrial/rastreabilidade/busca', { params: { texto: termo } }).then(r => r.data),
  })

  return (
    <div className="space-y-4">
      <form className="flex gap-2 max-w-xl" onSubmit={e => { e.preventDefault(); setAlvo(null); setTermo(texto.trim()) }}>
        <input value={texto} onChange={e => setTexto(e.target.value)} placeholder="Código do lote, ou número da corrida do tarugo"
          aria-label="Buscar na rastreabilidade" className={baseInputClass} />
        <Button type="submit"><Search size={15} /> Buscar</Button>
      </form>
      {!alvo && termo && (isFetching ? <LoadingState /> : (
        <div className="bg-white rounded-xl border border-gray-100 divide-y">
          {resultados?.length ? resultados.map(r => (
            <button key={`${r.tipo}-${r.id}`} type="button" className="w-full text-left px-4 py-3 hover:bg-gray-50"
              onClick={() => setAlvo(r.tipo === 'LOTE' ? { tipo: 'LOTE', codigo: r.codigo } : { tipo: 'LOTE_MP', id: r.id })}>
              <StatusBadge tone={r.tipo === 'LOTE' ? 'info' : 'purple'}>{r.tipo === 'LOTE' ? 'Lote de produto' : 'Lote de matéria-prima'}</StatusBadge>
              <span className="ml-2 font-mono font-semibold">{r.codigo}</span> <span className="text-gray-600">{r.descricao}</span>
            </button>
          )) : <p className="px-4 py-3 text-sm text-gray-500">Nada encontrado.</p>}
        </div>
      ))}
      {alvo?.tipo === 'LOTE' && <PorLote codigo={alvo.codigo} onLote={c => setAlvo({ tipo: 'LOTE', codigo: c })} onLoteMp={id => setAlvo({ tipo: 'LOTE_MP', id })} />}
      {alvo?.tipo === 'LOTE_MP' && <PorLoteMp id={alvo.id} onLote={c => setAlvo({ tipo: 'LOTE', codigo: c })} />}
    </div>
  )
}

function PorLote({ codigo, onLote, onLoteMp }: { codigo: string; onLote: (c: string) => void; onLoteMp: (id: number) => void }) {
  const { data: r, isLoading } = useQuery<RastreioLote>({
    queryKey: ['ind-rastreio-lote', codigo], queryFn: () => api.get(`/industrial/rastreabilidade/lote/${encodeURIComponent(codigo)}`).then(x => x.data),
  })
  if (isLoading || !r) return <LoadingState />
  const l = r.lote
  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-100 p-4 flex flex-wrap gap-4 items-center">
        <img className="w-24 h-24" alt={`QR do lote ${l.codigo}`} src={`data:image/svg+xml;base64,${btoa(r.qrSvg)}`} />
        <div className="flex-1 min-w-[220px]">
          <div className="font-mono text-xl font-bold">{l.codigo}</div>
          <div className="font-semibold">{l.produtoNome}</div>
          <div className="text-sm text-gray-600 tabular-nums">{num(l.quantidadeInicial)} {l.unidade} produzidos · saldo {num(l.saldo)}{l.ordemNumero ? ` · ${l.ordemNumero}` : ''} · {dataBR(l.createdAt)}</div>
          {r.medicoesForaDaFaixa > 0 && <div className="text-sm text-danger-700">{r.medicoesForaDaFaixa} medição(ões) de processo fora da faixa nesta OP</div>}
        </div>
        <StatusBadge tone={SITUACAO_LOTE[l.situacao].tom}>{SITUACAO_LOTE[l.situacao].rotulo}</StatusBadge>
      </div>

      <Secao titulo="Origem: matéria-prima consumida">
        {r.origens.length === 0 ? <Vazio texto="Nenhum lote de matéria-prima registrado no consumo." /> : (
          <Table>
            <THead><tr><TH>Corrida / lote</TH><TH>Material · liga</TH><TH>Fornecedor · certificado</TH><TH align="right">Consumido</TH><TH>OP</TH></tr></THead>
            <TBody>{r.origens.map(o => (
              <TR key={`${o.loteMpId}-${o.ordemNumero}`}>
                <TD><button type="button" className="font-mono font-semibold text-primary-700 hover:underline" onClick={() => onLoteMp(o.loteMpId)}>{o.numero}</button></TD>
                <TD>{o.material} · <span className="font-mono">{o.ligaCodigo}</span></TD>
                <TD className="text-sm">{o.fornecedor ?? '—'}{o.certificado ? ` · ${o.certificado}` : ''}</TD>
                <TD align="right" className="tabular-nums">{num(o.quantidade)}</TD>
                <TD className="font-mono text-sm">{o.ordemNumero}</TD>
              </TR>
            ))}</TBody>
          </Table>
        )}
      </Secao>

      {(r.ascendentes.length > 0 || r.descendentes.length > 0) && (
        <Secao titulo="Genealogia (divisões e uniões)">
          <ListaLotes titulo="Veio de" lotes={r.ascendentes} onLote={onLote} />
          <ListaLotes titulo="Deu origem a" lotes={r.descendentes} onLote={onLote} />
        </Secao>
      )}

      <Secao titulo="Destino: clientes"><Destinos destinos={r.destinos} /></Secao>

      <Secao titulo="Qualidade">
        {r.inspecoes.length === 0 && r.naoConformidades.length === 0 ? <Vazio texto="Sem inspeções nem não conformidades." /> : (
          <ul className="text-sm space-y-1">
            {r.inspecoes.map(i => (
              <li key={i.id}><StatusBadge tone={i.resultado === 'APROVADO' ? 'success' : 'danger'}>{i.resultado === 'APROVADO' ? 'Aprovado' : 'Reprovado'}</StatusBadge>
                <span className="ml-2">Inspeção {i.planoCodigo} rev. {i.planoRevisao} · {dataHoraBR(i.createdAt)} · {i.inspetorNome ?? ''}</span></li>
            ))}
            {r.naoConformidades.map(n => (
              <li key={n.id}><StatusBadge tone={n.status === 'ABERTA' ? 'danger' : 'neutral'}>{n.numero}</StatusBadge><span className="ml-2">{n.descricao}</span></li>
            ))}
          </ul>
        )}
      </Secao>

      <Secao titulo="Extrato do lote">
        <ul className="text-sm space-y-0.5">
          {r.movimentos.map((m, i) => (
            <li key={i} className="tabular-nums text-gray-700">{dataHoraBR(m.createdAt)} · {m.tipo.toLowerCase().replace('_', ' ')} · <b>{m.quantidade > 0 ? '+' : ''}{num(m.quantidade)}</b>{m.documento ? ` · ${m.documento}` : ''}{m.usuarioNome ? ` · ${m.usuarioNome}` : ''}</li>
          ))}
        </ul>
      </Secao>
    </div>
  )
}

function PorLoteMp({ id, onLote }: { id: number; onLote: (c: string) => void }) {
  const { data: r, isLoading } = useQuery<RastreioLoteMp>({
    queryKey: ['ind-rastreio-mp', id], queryFn: () => api.get(`/industrial/rastreabilidade/lote-mp/${id}`).then(x => x.data),
  })
  if (isLoading || !r) return <LoadingState />
  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-100 p-4">
        <div className="font-mono text-xl font-bold">{r.numero}</div>
        <div>{r.material} · liga <span className="font-mono">{r.ligaCodigo}</span> <StatusBadge tone={STATUS_LOTE_MP[r.status].tom} className="ml-2">{STATUS_LOTE_MP[r.status].rotulo}</StatusBadge></div>
        <div className="text-sm text-gray-600">{r.fornecedor ?? 'Fornecedor não informado'}{r.certificado ? ` · certificado ${r.certificado}` : ''}</div>
        <div className="text-sm text-gray-600 mt-1">Consumido nas OPs: {r.ordens.length ? r.ordens.join(', ') : 'nenhuma'}</div>
      </div>
      <Secao titulo="Lotes produzidos com esta matéria-prima"><ListaLotes lotes={r.lotesProduzidos} onLote={onLote} /></Secao>
      <Secao titulo="Clientes que receberam"><Destinos destinos={r.destinos} /></Secao>
    </div>
  )
}

function Secao({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return <section className="bg-white rounded-xl border border-gray-100 p-4"><h3 className="text-sm font-semibold text-gray-700 mb-2">{titulo}</h3>{children}</section>
}

function Vazio({ texto }: { texto: string }) {
  return <p className="text-sm text-gray-500">{texto}</p>
}

function ListaLotes({ titulo, lotes, onLote }: { titulo?: string; lotes: LoteResumo[]; onLote: (c: string) => void }) {
  if (!lotes.length) return titulo ? null : <Vazio texto="Nenhum lote." />
  return (
    <div className="mb-2">
      {titulo && <div className="text-xs text-gray-500 mb-1">{titulo}</div>}
      <ul className="text-sm space-y-0.5">
        {lotes.map(l => (
          <li key={l.id} style={{ paddingLeft: (l.nivel - 1) * 16 }}>
            <button type="button" className="font-mono font-semibold text-primary-700 hover:underline" onClick={() => onLote(l.codigo)}>{l.codigo}</button>
            <span className="text-gray-600"> · {l.produtoNome} · {num(l.quantidadeInicial)}{l.ordemNumero ? ` · ${l.ordemNumero}` : ''}</span>
            <StatusBadge tone={SITUACAO_LOTE[l.situacao].tom} className="ml-2">{SITUACAO_LOTE[l.situacao].rotulo}</StatusBadge>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Destinos({ destinos }: { destinos: DestinoLote[] }) {
  if (!destinos.length) return <Vazio texto="Ainda não expedido para nenhum cliente." />
  return (
    <Table>
      <THead><tr><TH>Cliente</TH><TH>Expedição</TH><TH>Lote</TH><TH align="right">Quantidade</TH><TH>Carregado em</TH></tr></THead>
      <TBody>{destinos.map((d, i) => (
        <TR key={i}>
          <TD><div className="font-medium">{d.cliente}</div>{d.clienteDocumento && <div className="text-xs text-gray-500">{d.clienteDocumento}</div>}</TD>
          <TD className="font-mono">{d.expedicaoNumero}</TD>
          <TD className="font-mono">{d.loteCodigo}</TD>
          <TD align="right" className="tabular-nums">{num(d.quantidade)}</TD>
          <TD className="tabular-nums text-sm">{dataHoraBR(d.carregadaEm)}</TD>
        </TR>
      ))}</TBody>
    </Table>
  )
}
