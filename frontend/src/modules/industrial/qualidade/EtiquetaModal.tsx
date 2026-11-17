import { useQuery } from '@tanstack/react-query'
import { Printer } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { Modal, Button, LoadingState } from '@/shared/components/ui'
import { SITUACAO_LOTE, type Etiqueta } from '@/types/industrial'
import { dataBR, num } from '../comum'

const escapar = (s: string) => s.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!))

/** Etiqueta do lote com QR do código: lida na conferência da expedição e na rastreabilidade. */
export default function EtiquetaModal({ loteId, onClose }: { loteId: number; onClose: () => void }) {
  const { data: e } = useQuery<Etiqueta>({
    queryKey: ['ind-etiqueta', loteId], queryFn: () => api.get(`/industrial/lotes/${loteId}/etiqueta`).then(r => r.data),
  })

  function imprimir() {
    if (!e) return
    const janela = window.open('', '_blank', 'width=420,height=520')
    if (!janela) return
    janela.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${escapar(e.codigo)}</title>
      <style>body{font-family:Arial,sans-serif;margin:8mm}.et{border:1px solid #000;padding:4mm;width:90mm}
      .cod{font-size:20pt;font-weight:bold;font-family:monospace}.qr{width:38mm;height:38mm}p{margin:1mm 0;font-size:10pt}</style>
      </head><body><div class="et"><div class="cod">${escapar(e.codigo)}</div>
      <img class="qr" src="data:image/svg+xml;base64,${btoa(e.qrSvg)}" alt="QR">
      <p><b>${escapar(e.produtoNome)}</b></p><p>Quantidade: ${num(e.quantidade)} ${escapar(e.unidade)}</p>
      <p>${e.ordemNumero ? `OP ${escapar(e.ordemNumero)} · ` : ''}${dataBR(e.data)}</p></div>
      <script>window.onload=()=>{window.print()}</script></body></html>`)
    janela.document.close()
  }

  return (
    <Modal title="Etiqueta do lote" onClose={onClose} maxWidth="md"
      footer={<>
        <Button variant="secondary" fullWidth onClick={onClose}>Fechar</Button>
        <Button fullWidth disabled={!e} onClick={imprimir}><Printer size={15} /> Imprimir</Button>
      </>}>
      {!e ? <LoadingState /> : (
        <div className="border border-gray-300 rounded-lg p-4 text-center">
          <div className="font-mono text-2xl font-bold">{e.codigo}</div>
          <img className="mx-auto my-2 w-40 h-40" alt={`QR do lote ${e.codigo}`} src={`data:image/svg+xml;base64,${btoa(e.qrSvg)}`} />
          <div className="font-semibold">{e.produtoNome}</div>
          <div className="text-sm text-gray-600 tabular-nums">{num(e.quantidade)} {e.unidade}{e.ordemNumero ? ` · ${e.ordemNumero}` : ''} · {dataBR(e.data)}</div>
          <div className="text-xs text-gray-500 mt-1">Situação atual: {SITUACAO_LOTE[e.situacao].rotulo}</div>
        </div>
      )}
    </Modal>
  )
}
