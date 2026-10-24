import { SISTEMA } from '@/config/empresa'
import { casasDaUnidade } from '@/shared/utils/mask'
import type { Venda } from '@/types/venda'
import type { ConfiguracaoComprovante, PagamentoCupom } from '@/types/comprovante'

interface Props {
  venda: Venda
  operador: string
  config: ConfiguracaoComprovante
  /** Usado quando a venda devolvida pelo servidor não traz os pagamentos. */
  pagamentos?: PagamentoCupom[]
}

const num2 = (v: number) => (v ?? 0).toFixed(2).replace('.', ',')

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
 * Só HTML + CSS próprio: é impresso dentro de um iframe isolado (ver
 * imprimirComprovante.ts), sem o CSS do resto do sistema.
 */
export default function ReciboCupom({ venda, operador, config, pagamentos }: Props) {
  const largura = config.larguraPapelMm
  const estreito = largura <= 58
  const dataVenda = venda.dataVenda ? new Date(venda.dataVenda) : new Date()
  const descontoItens = (venda.itens ?? []).reduce((acc, i) => acc + (i.descontoItem ?? 0), 0)
  const pags: PagamentoCupom[] = venda.pagamentos?.length ? venda.pagamentos : (pagamentos ?? [])
  const enderecoLinha2 = [config.bairro, config.cidade].filter(Boolean).join(' - ')

  return (
    <div className="recibo">
      <style>{`
        .recibo {
          width: ${largura}mm;
          box-sizing: border-box;
          font-family: 'Courier New', Courier, monospace;
          font-size: ${estreito ? 9.5 : 11.5}px;
          line-height: 1.3;
          color: #000;
          background: #fff;
          padding: 1.5mm ${estreito ? 2 : 3}mm;
        }
        .recibo p { margin: 0; }
        .recibo .centro { text-align: center; }
        .recibo .separador { border-top: 1px dashed #000; margin: 3px 0; }
        .recibo .linha { display: flex; justify-content: space-between; gap: 4px; }
        .recibo .negrito { font-weight: bold; }
        .recibo .titulo { font-size: ${estreito ? 11.5 : 13.5}px; font-weight: bold; text-align: center; margin: 2px 0; }
        .recibo table { width: 100%; border-collapse: collapse; table-layout: fixed; }
        .recibo th { text-align: left; font-weight: normal; border-bottom: 1px solid #000; padding-bottom: 2px; }
        .recibo th.num, .recibo td.num { text-align: right; }
        .recibo td { overflow-wrap: break-word; vertical-align: top; }
        .recibo .total-grande { font-size: ${estreito ? 13 : 16}px; font-weight: bold; }
        .recibo .rodape { margin-top: 4px; white-space: pre-line; }
      `}</style>

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
            <th style={{ width: '40%' }}>Descrição</th>
            <th className="num">Valor</th>
            <th className="num">Qtde.</th>
            <th className="num">Total</th>
          </tr>
        </thead>
        <tbody>
          {(venda.itens ?? []).flatMap((item, idx) => {
            const casas = casasDaUnidade(item.produto?.unidadeMedida)
            return [
              <tr key={`${item.id}-n`}>
                <td colSpan={4}>{idx + 1} {item.produto?.nome}</td>
              </tr>,
              <tr key={`${item.id}-v`}>
                <td>{(item.produto?.unidadeMedida ?? '').toUpperCase()}</td>
                <td className="num">{num2(item.precoUnitario)}</td>
                <td className="num">{(item.quantidade ?? 0).toFixed(casas).replace('.', ',')}</td>
                <td className="num">{num2(item.totalItem)}</td>
              </tr>,
            ]
          })}
        </tbody>
      </table>

      <div className="separador" />

      <div className="linha"><span>Sub Total:</span><span>{num2(venda.subtotal)}</span></div>
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
