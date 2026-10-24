import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import ReciboCupom, { areaImpressaoMm } from './components/ReciboCupom'
import type { Venda } from '@/types/venda'
import type { ConfiguracaoComprovante, PagamentoCupom } from '@/types/comprovante'

/**
 * Imprime o cupom num iframe escondido, com só o HTML do cupom: o resto da
 * tela do PDV nunca vai para o papel, e a página tem a largura que a cabeça
 * térmica imprime (72 mm na bobina de 80; 48 mm na de 58), altura automática.
 *
 * Com o Chrome do caixa aberto com --kiosk-printing, sai direto na impressora
 * padrão do Windows, sem a janela de impressão (ver docs/IMPRESSORA.md).
 */
export function imprimirComprovante(
  venda: Venda,
  operador: string,
  config: ConfiguracaoComprovante,
  pagamentos?: PagamentoCupom[],
  vias = config.vias,
) {
  const umaVia = renderToStaticMarkup(createElement(ReciboCupom, { venda, operador, config, pagamentos }))
  const corpo = Array.from({ length: Math.max(1, vias) }, (_, i) =>
    `<div style="${i > 0 ? 'page-break-before: always;' : ''}">${umaVia}</div>`).join('')

  const html = `<!doctype html><html><head><meta charset="utf-8"><title>Cupom ${venda.id}</title>
    <style>
      /* página = área que a cabeça imprime (72 mm na bobina de 80): nada fica fora */
      @page { size: ${areaImpressaoMm(config.larguraPapelMm)}mm auto; margin: 0; }
      html, body { margin: 0; padding: 0; background: #fff; }
    </style></head><body>${corpo}</body></html>`

  const iframe = document.createElement('iframe')
  iframe.setAttribute('aria-hidden', 'true')
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden'
  document.body.appendChild(iframe)

  const doc = iframe.contentDocument
  if (!doc || !iframe.contentWindow) {
    iframe.remove()
    throw new Error('Não foi possível preparar a impressão.')
  }
  doc.open()
  doc.write(html)
  doc.close()

  const janela = iframe.contentWindow
  const remover = () => setTimeout(() => iframe.remove(), 1000)
  janela.onafterprint = remover
  // dá um instante para o layout do iframe ficar pronto antes de imprimir
  setTimeout(() => {
    janela.focus()
    janela.print()
    setTimeout(remover, 60_000) // garante a limpeza mesmo se onafterprint não disparar
  }, 150)
}
