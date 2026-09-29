import { X, Printer } from 'lucide-react'
import { formatBRL, formatWeightDisplay } from '@/shared/utils/mask'

interface NotaFiscalItem {
  id: number
  produto: { nome: string } | null
  descricao: string
  quantidade: number
  valorUnitario: number
  valorTotal: number
}

interface NotaFiscal {
  id: number
  numeroNf: string | null
  serieNf: string
  cliente: { id: number; nome: string } | null
  naturezaOperacao: string
  dataEmissao: string
  valorTotal: number
  status: string
  chaveAcesso?: string | null
  itens: NotaFiscalItem[]
}

interface Props {
  nf: NotaFiscal
  onClose: () => void
}

const brl = formatBRL
const qtd  = formatWeightDisplay

/**
 * Impressão simplificada da NF — um resumo legível (cabeçalho, itens,
 * totais), não uma DANFE oficial da SEFAZ. Isso é deixado claro no rodapé
 * do próprio documento, pra não passar a impressão de ser um documento
 * fiscal válido quando o sistema não faz emissão/assinatura real de NF-e.
 * Mesma técnica de "print isolado" usada no relatório de estoque.
 */
export default function ImpressaoNFModal({ nf, onClose }: Props) {
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4 print:static print:bg-white print:p-0">
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #nf-print-area, #nf-print-area * { visibility: visible; }
          #nf-print-area { position: absolute; top: 0; left: 0; width: 100%; }
        }
      `}</style>

      <div className="bg-white rounded-2xl w-full max-w-2xl max-h-[85vh] flex flex-col shadow-xl print:max-h-none print:shadow-none print:rounded-none print:max-w-full">
        <div className="flex items-center justify-between px-6 py-4 border-b print:hidden">
          <h2 className="font-bold text-gray-900">Imprimir Nota Fiscal</h2>
          <div className="flex items-center gap-3">
            <button
              onClick={() => window.print()}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-primary-600 text-white rounded-lg text-sm font-medium hover:bg-primary-700"
            >
              <Printer size={14} /> Imprimir
            </button>
            <button onClick={onClose} aria-label="Fechar">
              <X size={20} className="text-gray-400 hover:text-gray-600" />
            </button>
          </div>
        </div>

        <div id="nf-print-area" className="flex-1 overflow-y-auto p-8 print:overflow-visible">
          <div className="flex items-start justify-between border-b-2 border-gray-800 pb-4 mb-4">
            <div>
              <h1 className="text-lg font-black text-gray-900">SysFlex ERP</h1>
              <p className="text-xs text-gray-500">Documento gerencial de venda</p>
            </div>
            <div className="text-right">
              <p className="text-sm font-bold text-gray-900">
                NF {nf.numeroNf ?? 'S/N'} — Série {nf.serieNf}
              </p>
              <p className="text-xs text-gray-500">
                Emissão: {new Date(nf.dataEmissao).toLocaleDateString('pt-BR')}
              </p>
              <p className="text-xs text-gray-500">Status: {nf.status}</p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 mb-4 text-sm">
            <div>
              <p className="text-[11px] text-gray-400 uppercase tracking-wide">Cliente</p>
              <p className="font-medium text-gray-800">{nf.cliente?.nome ?? 'Consumidor final'}</p>
            </div>
            <div>
              <p className="text-[11px] text-gray-400 uppercase tracking-wide">Natureza da Operação</p>
              <p className="font-medium text-gray-800">{nf.naturezaOperacao}</p>
            </div>
          </div>

          <table className="w-full text-sm mb-4">
            <thead>
              <tr className="border-b-2 border-gray-300 text-xs text-gray-500 uppercase tracking-wide">
                <th className="text-left py-2">Descrição</th>
                <th className="text-right py-2">Qtd</th>
                <th className="text-right py-2">Unit.</th>
                <th className="text-right py-2">Total</th>
              </tr>
            </thead>
            <tbody>
              {nf.itens.map(it => (
                <tr key={it.id} className="border-b border-gray-200">
                  <td className="py-2">{it.produto?.nome ?? it.descricao}</td>
                  <td className="py-2 text-right tabular-nums">{qtd(it.quantidade)}</td>
                  <td className="py-2 text-right tabular-nums">{brl(it.valorUnitario)}</td>
                  <td className="py-2 text-right font-medium tabular-nums">{brl(it.valorTotal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t-2 border-gray-800">
                <td colSpan={3} className="pt-2 text-right text-xs uppercase font-semibold text-gray-500">Total</td>
                <td className="pt-2 text-right font-bold tabular-nums">{brl(nf.valorTotal)}</td>
              </tr>
            </tfoot>
          </table>

          <p className="text-[10px] text-gray-400 border-t pt-3 mt-8">
            Este documento é um resumo gerencial gerado pelo sistema e não substitui a DANFE
            oficial emitida pela SEFAZ. Para o documento fiscal válido, consulte o XML da NF-e
            vinculado a este registro.
          </p>
        </div>
      </div>
    </div>
  )
}