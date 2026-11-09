import { SISTEMA } from '@/config/empresa'
import { casasDaUnidade } from '@/shared/utils/mask'
import type { Venda } from '@/types/venda'
import type { ConfiguracaoComprovante, PagamentoCupom } from '@/types/comprovante'
import type { Nfce } from '@/types/fiscal'

interface Props {
  venda: Venda
  operador: string
  config: ConfiguracaoComprovante
  /** Usado quando a venda devolvida pelo servidor não traz os pagamentos. */
  pagamentos?: PagamentoCupom[]
  /** NFC-e autorizada da venda: o cupom vira o DANFE NFC-e */
  nfce?: Nfce | null
}

const cnpjFormatado = (c?: string | null) =>
  c && c.length === 14 ? `${c.slice(0, 2)}.${c.slice(2, 5)}.${c.slice(5, 8)}/${c.slice(8, 12)}-${c.slice(12)}` : (c ?? '')
const cpfFormatado = (c: string) => `${c.slice(0, 3)}.${c.slice(3, 6)}.${c.slice(6, 9)}-${c.slice(9)}`
const chaveEmBlocos = (c: string) => c.replace(/(.{4})/g, '$1 ').trim()
const numeroNota = (n: number) => String(n).padStart(9, '0').replace(/(\d{3})(\d{3})(\d{3})/, '$1.$2.$3')
const dataHora = (iso?: string | null) => {
  const d = iso ? new Date(iso) : new Date()
  return `${d.toLocaleDateString('pt-BR')} ${d.toLocaleTimeString('pt-BR')}`
}

const num2 = (v: number) => (v ?? 0).toFixed(2).replace('.', ',')

/**
 * Largura que a cabeça térmica imprime: numa bobina de 80 mm são ~72 mm
 * (576 pontos a 203 dpi); na de 58 mm, ~48 mm. É a largura da página.
 */
export const AREA_CABECA_MM: Record<number, number> = { 80: 72, 58: 48 }
export const larguraCabecaMm = (larguraPapelMm: number) => AREA_CABECA_MM[larguraPapelMm] ?? larguraPapelMm - 8

/**
 * Largura do conteúdo: a da cabeça menos a margem configurada. Na prática os
 * drivers imprimem um pouco menos (ou trocam a fonte por uma interna mais
 * larga), e sem essa folga os valores da direita saem cortados.
 */
export const areaImpressaoMm = (config: Pick<ConfiguracaoComprovante, 'larguraPapelMm' | 'margemDireitaMm'>) =>
  larguraCabecaMm(config.larguraPapelMm) - (config.margemDireitaMm ?? 5)

const FORMA_PAGAMENTO: Record<string, string> = {
  DINHEIRO: 'DINHEIRO',
  DEBITO: 'CARTÃO DÉBITO',
  CREDITO: 'CARTÃO CRÉDITO',
  PIX: 'PIX',
  CHEQUE: 'CHEQUE',
  FIADO: 'FIADO',
  CONVENIO: 'CONVÊNIO',
  VOUCHER: 'VOUCHER',
}

/**
 * Cupom não fiscal ("para simples conferência"), no layout de bobina térmica.
 * Com uma NFC-e autorizada, vira o DANFE NFC-e: emitente, chave de acesso,
 * consumidor, número/série, protocolo e QR Code de consulta.
 * Só HTML + CSS próprio: é impresso dentro de um iframe isolado (ver
 * imprimirComprovante.ts), sem o CSS do resto do sistema.
 */
export default function ReciboCupom({ venda, operador, config, pagamentos, nfce }: Props) {
  const fiscal = nfce?.status === 'AUTORIZADA' ? nfce : null
  const largura = areaImpressaoMm(config)
  const estreito = config.larguraPapelMm <= 58
  const dataVenda = venda.dataVenda ? new Date(venda.dataVenda) : new Date()
  const descontoItens = (venda.itens ?? []).reduce((acc, i) => acc + (i.descontoItem ?? 0), 0)
  const pags: PagamentoCupom[] = venda.pagamentos?.length ? venda.pagamentos : (pagamentos ?? [])
  const enderecoLinha2 = [config.bairro, config.cidade].filter(Boolean).join(' - ')

  return (
    <div className="recibo">
      {/* dangerouslySetInnerHTML: como texto, o renderToStaticMarkup troca as aspas por &#x27;
          dentro do <style>, a fonte fica inválida e o cupom sai em Times New Roman */}
      <style dangerouslySetInnerHTML={{ __html: `
        .recibo {
          width: ${largura}mm;
          box-sizing: border-box;
          font-family: Arial, 'Helvetica Neue', Helvetica, sans-serif;
          font-weight: 700;
          font-size: ${estreito ? 10 : 12}px;
          line-height: 1.32;
          color: #000;
          background: #fff;
          padding: 1mm 0.5mm;
          -webkit-print-color-adjust: exact;
          print-color-adjust: exact;
        }
        .recibo p { margin: 0; }
        .recibo .centro { text-align: center; }
        .recibo .separador { border-top: 2px dashed #000; margin: 4px 0; }
        .recibo .linha { display: flex; justify-content: space-between; gap: 4px; }
        .recibo .negrito { font-weight: bold; }
        .recibo .titulo { font-size: ${estreito ? 13 : 16}px; font-weight: 800; text-align: center; margin: 3px 0; }
        .recibo table { width: 100%; border-collapse: collapse; table-layout: fixed; }
        .recibo th { text-align: left; font-weight: 700; border-bottom: 2px solid #000; padding-bottom: 2px; }
        .recibo th.num, .recibo td.num { text-align: right; white-space: nowrap; }
        .recibo td { overflow-wrap: break-word; vertical-align: top; }
        .recibo .total-grande { font-size: ${estreito ? 14 : 17}px; font-weight: 800; }
        .recibo .rodape { margin-top: 4px; white-space: pre-line; }
        .recibo .pequeno { font-size: ${estreito ? 9 : 10}px; }
        .recibo .chave { text-align: center; word-spacing: 2px; }
        .recibo .qrcode { display: block; width: ${estreito ? 30 : 34}mm; height: ${estreito ? 30 : 34}mm; margin: 4px auto; }
      ` }} />

      {fiscal ? (
        <>
          <p className="titulo">{(fiscal.emitente?.razaoSocial ?? config.nomeFantasia).toUpperCase()}</p>
          <p className="centro">
            CNPJ {cnpjFormatado(fiscal.emitente?.cnpj)}{fiscal.emitente?.inscricaoEstadual ? ` IE ${fiscal.emitente.inscricaoEstadual}` : ''}
          </p>
          {fiscal.emitente?.endereco && <p className="centro">{fiscal.emitente.endereco}</p>}
          {fiscal.emitente?.municipio && <p className="centro">{fiscal.emitente.municipio}</p>}
          <div className="separador" />
          <p className="centro">Documento Auxiliar da Nota Fiscal de Consumidor Eletrônica</p>
          {fiscal.ambiente === 'HOMOLOGACAO' && (
            <p className="centro">EMITIDA EM AMBIENTE DE HOMOLOGAÇÃO - SEM VALOR FISCAL</p>
          )}
        </>
      ) : (
        <>
          {config.mostrarAvisoNaoFiscal && (
            <>
              <p className="centro">*** CUPOM PARA SIMPLES CONFERÊNCIA ***</p>
              <p className="centro">*** NÃO É DOCUMENTO FISCAL ***</p>
            </>
          )}
          <p className="titulo">{config.nomeFantasia.toUpperCase()}</p>
          {config.cnpj && <p className="centro">CNPJ {config.cnpj}</p>}
          {config.endereco && <p>{config.endereco}</p>}
          {enderecoLinha2 && <p>{enderecoLinha2}</p>}
          {config.telefone && <p>Fone {config.telefone}</p>}
        </>
      )}

      <div className="separador" />

      <div className="linha">
        <span className="negrito">Venda: {venda.id}</span>
        <span>Cupom: {venda.numeroCupom ?? venda.id}</span>
      </div>
      <div className="linha">
        {config.mostrarOperador ? <span>Operador: {operador.toUpperCase()}</span> : <span />}
        <span>{dataVenda.toLocaleDateString('pt-BR')} {dataVenda.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</span>
      </div>
      {config.mostrarCliente && venda.cliente && venda.cliente.nome !== 'CONSUMIDOR' && (
        <p>Cliente: {venda.cliente.nome}</p>
      )}

      <div className="separador" />

      <table>
        <thead>
          <tr>
            {/* na bobina estreita, o espaço vai para os valores: o nome tem linha própria */}
            <th style={{ width: estreito ? '22%' : '40%' }}>{estreito ? 'Un.' : 'Descrição'}</th>
            <th className="num">Valor</th>
            <th className="num">Qtde.</th>
            <th className="num">Total</th>
          </tr>
        </thead>
        <tbody>
          {(venda.itens ?? []).flatMap((item, idx) => {
            const casas = casasDaUnidade(item.produto?.unidadeMedida)
            const desconto = item.descontoItem ?? 0
            return [
              <tr key={`${item.id}-n`}>
                <td colSpan={4}>{idx + 1} {item.produto?.nome}</td>
              </tr>,
              <tr key={`${item.id}-v`}>
                <td>{(item.produto?.unidadeMedida ?? '').toUpperCase()}</td>
                <td className="num">{num2(item.precoUnitario)}</td>
                <td className="num">{(item.quantidade ?? 0).toFixed(casas).replace('.', ',')}</td>
                <td className="num">{num2(item.totalItem + desconto)}</td>
              </tr>,
              ...(desconto > 0 ? [
                <tr key={`${item.id}-d`}>
                  <td colSpan={3}>Desconto</td>
                  <td className="num">-{num2(desconto)}</td>
                </tr>,
              ] : []),
            ]
          })}
        </tbody>
      </table>

      <div className="separador" />

      {/* venda.subtotal já vem sem os descontos dos itens: o cupom mostra o valor cheio para a conta fechar */}
      <div className="linha"><span>Sub Total:</span><span>{num2(venda.subtotal + descontoItens)}</span></div>
      {descontoItens > 0 && (
        <div className="linha"><span>&nbsp;&nbsp;Desconto nos itens:</span><span>{num2(descontoItens)}</span></div>
      )}
      <div className="linha"><span>Desconto (-):</span><span>{num2(venda.desconto)}</span></div>

      <div className="separador" />

      <div className="linha total-grande"><span>Total:</span><span>{num2(venda.total)}</span></div>

      <div className="separador" />

      {pags.map((p, i) => (
        <div className="linha" key={i}>
          <span>
            {FORMA_PAGAMENTO[p.formaPagamento] ?? p.formaPagamento}
            {p.formaPagamento === 'CREDITO' && (p.parcelas ?? 1) > 1 ? ` ${p.parcelas}x` : ''}
          </span>
          <span>{num2(p.valor)}</span>
        </div>
      ))}
      <div className="linha"><span>Troco</span><span>{num2(venda.troco)}</span></div>

      {fiscal && (
        <>
          <div className="separador" />
          <p className="centro">Consulte pela Chave de Acesso em</p>
          {fiscal.urlConsulta && <p className="centro pequeno">{fiscal.urlConsulta}</p>}
          {fiscal.chave && <p className="chave pequeno">{chaveEmBlocos(fiscal.chave)}</p>}
          <div className="separador" />
          <p className="centro">
            {fiscal.destinatarioDocumento
              ? `CONSUMIDOR - ${fiscal.destinatarioDocumento.length === 11 ? 'CPF ' + cpfFormatado(fiscal.destinatarioDocumento)
                : 'CNPJ ' + cnpjFormatado(fiscal.destinatarioDocumento)}`
              : 'CONSUMIDOR NÃO IDENTIFICADO'}
          </p>
          <div className="separador" />
          <p className="centro">
            NFC-e nº {numeroNota(fiscal.numero)} Série {String(fiscal.serie).padStart(3, '0')} {dataHora(fiscal.emitidaEm)}
          </p>
          {fiscal.protocolo && <p className="centro">Protocolo de autorização: {fiscal.protocolo}</p>}
          <p className="centro">Data de autorização: {dataHora(fiscal.emitidaEm)}</p>
          {fiscal.qrCodeSvg && (
            <img className="qrcode" alt="QR Code de consulta da NFC-e"
                 src={`data:image/svg+xml;base64,${btoa(fiscal.qrCodeSvg)}`} />
          )}
          {fiscal.ambiente === 'HOMOLOGACAO' && (
            <p className="centro">EMITIDA EM AMBIENTE DE HOMOLOGAÇÃO - SEM VALOR FISCAL</p>
          )}
        </>
      )}

      {config.mensagemRodape && (
        <>
          <div className="separador" />
          <p className="centro rodape">{config.mensagemRodape}</p>
        </>
      )}

      <div className="separador" />
      <p className="centro">Sistema {SISTEMA.nome}</p>
    </div>
  )
}
