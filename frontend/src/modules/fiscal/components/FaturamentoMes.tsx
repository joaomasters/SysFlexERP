import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { formatBRL } from '@/shared/utils/mask'
import { Card, Button, Field, baseInputClass, Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState } from '@/shared/components/ui'
import { baixarCsv } from '@/modules/produtos/planilhaFiscal'

interface Dia {
  data: string | null
  vendas: number
  total: number
  vendasComNfce: number
  totalComNfce: number
  porForma: Record<string, number>
}

interface Extrato {
  mes: string
  formas: string[]
  dias: Dia[]
  totais: Dia
}

const ROTULO_FORMA: Record<string, string> = {
  DINHEIRO: 'Dinheiro', DEBITO: 'Débito', CREDITO: 'Crédito', PIX: 'Pix', VOUCHER: 'Voucher',
  CHEQUE: 'Cheque', FIADO: 'Fiado', CONVENIO: 'Convênio',
  NAO_INFORMADO: 'Não informado',
}

const rotulo = (f: string) => ROTULO_FORMA[f] ?? f.charAt(0) + f.slice(1).toLowerCase()
const dataBR = (iso: string) => iso.split('-').reverse().join('/')
const num = (v: number) => Number(v).toFixed(2).replace('.', ',')

function mesAtual() {
  const h = new Date()
  return `${h.getFullYear()}-${String(h.getMonth() + 1).padStart(2, '0')}`
}

/**
 * Extrato de faturamento do mês para o contador: vendas fechadas no PDV por dia e forma de
 * pagamento, e quanto saiu com NFC-e autorizada. É a base do PGDAS e da conferência com as maquininhas.
 */
export default function FaturamentoMes() {
  const [mes, setMes] = useState(mesAtual)
  const { data: e, isLoading } = useQuery<Extrato>({
    queryKey: ['fiscal-faturamento-mes', mes],
    queryFn: () => api.get('/fiscal/faturamento-do-mes', { params: { mes } }).then(r => r.data),
    enabled: /^\d{4}-\d{2}$/.test(mes),
  })

  // só as formas que tiveram movimento no mês
  const formas = e ? e.formas.filter(f => Number(e.totais.porForma[f] ?? 0) !== 0) : []

  function baixar() {
    if (!e) return
    const cab = ['dia', 'vendas', 'total', ...formas.map(f => rotulo(f).toLowerCase()), 'vendas_com_nfce', 'total_com_nfce', 'total_sem_nfce']
    const linha = (rot: string, d: Dia) => [
      rot, String(d.vendas), num(d.total), ...formas.map(f => num(d.porForma[f] ?? 0)),
      String(d.vendasComNfce), num(d.totalComNfce), num(d.total - d.totalComNfce),
    ].join(';')
    const linhas = [cab.join(';'), ...e.dias.map(d => linha(dataBR(d.data!), d)), linha('TOTAL', e.totais)]
    baixarCsv('﻿' + linhas.join('\r\n'), `faturamento-${mes}.csv`)
  }

  return (
    <Card padding="none">
      <div className="p-4 flex flex-wrap items-end gap-3 border-b border-gray-100">
        <Field label="Mês" htmlFor="faturamento-mes">
          <input id="faturamento-mes" type="month" value={mes} max={mesAtual()} onChange={ev => setMes(ev.target.value)}
                 className={`${baseInputClass} w-44`} />
        </Field>
        <p className="text-xs text-gray-500 max-w-md">
          Vendas fechadas no PDV (as canceladas ficam de fora). O troco já sai do dinheiro.
          "Sem NFC-e" é o que foi vendido no cupom não fiscal.
        </p>
        <Button variant="primary" className="ml-auto" onClick={baixar} disabled={!e || e.dias.length === 0}>
          <Download size={16} /> Baixar extrato (CSV)
        </Button>
      </div>
      {isLoading || !e ? <LoadingState /> : e.dias.length === 0 ? (
        <EmptyState>Nenhuma venda fechada neste mês.</EmptyState>
      ) : (
        <Table>
            <THead>
              <TH>Dia</TH><TH align="right">Vendas</TH><TH align="right">Total</TH>
              {formas.map(f => <TH key={f} align="right">{rotulo(f)}</TH>)}
              <TH align="right">Com NFC-e</TH><TH align="right">Sem NFC-e</TH>
            </THead>
            <TBody>
              {e.dias.map(d => (
                <TR key={d.data}>
                  <TD>{dataBR(d.data!)}</TD>
                  <TD align="right" className="tabular-nums">{d.vendas}</TD>
                  <TD align="right" className="tabular-nums font-medium">{formatBRL(d.total)}</TD>
                  {formas.map(f => <TD key={f} align="right" className="tabular-nums">{formatBRL(d.porForma[f] ?? 0)}</TD>)}
                  <TD align="right" className="tabular-nums">{formatBRL(d.totalComNfce)}</TD>
                  <TD align="right" className="tabular-nums">{formatBRL(d.total - d.totalComNfce)}</TD>
                </TR>
              ))}
              <TR className="bg-gray-50 font-semibold">
                <TD>Total do mês</TD>
                <TD align="right" className="tabular-nums">{e.totais.vendas}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(e.totais.total)}</TD>
                {formas.map(f => <TD key={f} align="right" className="tabular-nums">{formatBRL(e.totais.porForma[f] ?? 0)}</TD>)}
                <TD align="right" className="tabular-nums">{formatBRL(e.totais.totalComNfce)}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(e.totais.total - e.totais.totalComNfce)}</TD>
              </TR>
            </TBody>
        </Table>
      )}
    </Card>
  )
}
