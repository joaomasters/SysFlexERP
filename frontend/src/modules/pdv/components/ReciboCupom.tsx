import { EMPRESA, SISTEMA, LARGURA_PAPEL_MM } from '@/config/empresa'
import type { Venda } from '@/types/venda'

interface Props {
  venda: Venda
  operador: string
}

const num2 = (v: number) => v.toFixed(2).replace('.', ',')
const num3 = (v: number) => v.toFixed(3)

const FORMA_PAGAMENTO_LABEL: Record<string, string> = {
  DINHEIRO: 'DINHEIRO',
  CARTAO_DEBITO: 'CARTÃO DÉBITO',
  CARTAO_CREDITO: 'CARTÃO CRÉDITO',
  PIX: 'PIX',
  FIADO: 'FIADO',
  CHEQUE: 'CHEQUE',
}

export default function ReciboCupom({ venda, operador }: Props) {
  const dataVenda = new Date(venda.dataVenda)
  const descontoItens = venda.itens.reduce((acc, i) => acc + (i.descontoItem ?? 0), 0)

  return (
    <div id="recibo-impressao">
      <style>{`
        #recibo-impressao {
          width: ${LARGURA_PAPEL_MM}mm;
          font-family: 'Courier New', Courier, monospace;
          font-size: ${LARGURA_PAPEL_MM <= 58 ? 9.5 : 11}px;
          line-height: 1.3;
          color: #000;
          padding: 1.5mm 2mm;
        }
        #recibo-impressao .centro { text-align: center; }
        #recibo-impressao .separador { border-top: 1px dashed #000; margin: 2px 0; }
        #recibo-impressao .linha { display: flex; justify-content: space-between; gap: 3px; }
        #recibo-impressao .negrito { font-weight: bold; }
        #recibo-impressao table { width: 100%; border-collapse: collapse; table-layout: fixed; }
        #recibo-impressao th { text-align: left; font-weight: normal; border-bottom: 1px solid #000; padding-bottom: 2px; }
        #recibo-impressao th.num, #recibo-impressao td.num { text-align: right; }
        #recibo-impressao td { overflow-wrap: break-word; }
        #recibo-impressao .total-grande { font-size: ${LARGURA_PAPEL_MM <= 58 ? 13 : 15}px; font-weight: bold; }
        @page { size: ${LARGURA_PAPEL_MM}mm auto; margin: 0; }
      `}</style>

      <p className="centro">*** CUPOM PARA SIMPLES CONFERÊNCIA ***</p>
      <p className="centro">*** NÃO É DOCUMENTO FISCAL ***</p>
      <p className="centro negrito">{EMPRESA.nome.toUpperCase()}</p>

      <p>{EMPRESA.endereco}</p>
      <p>{EMPRESA.bairro}</p>
      <p>Fone {EMPRESA.telefone}</p>

      <div className="separador" />

      <div className="linha">
        <span className="negrito">Venda: {venda.id}</span>
        <span>Cupom: {venda.numeroCupom ?? venda.id}</span>
      </div>
      <div className="linha">
        <span>Operador: {operador.toUpperCase()}</span>
        <span>Data: {dataVenda.toLocaleDateString('pt-BR')} {dataVenda.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</span>
      </div>
      {venda.cliente && <p>Cliente: {venda.cliente.nome}</p>}

      <div className="separador" />

      <table>
        <thead>
          <tr>
            <th>Descrição</th>
            <th className="num">Valor</th>
            <th className="num">Qtde.</th>
            <th className="num">Total</th>
          </tr>
        </thead>
        <tbody>
          {venda.itens.flatMap((item, idx) => [
            <tr key={`${item.id}-nome`}>
              <td colSpan={4}>{idx + 1} {item.produto.nome}</td>
            </tr>,
            <tr key={`${item.id}-valores`}>
              <td></td>
              <td className="num">{num2(item.precoUnitario)}</td>
              <td className="num">{num3(item.quantidade)}</td>
              <td className="num">{num2(item.totalItem)}</td>
            </tr>,
          ])}
        </tbody>
      </table>

      <div className="separador" />

      <div className="linha">
        <span>Sub Total:</span>
        <span>{num2(venda.subtotal)}</span>
      </div>
      <div className="linha">
        <span>&nbsp;&nbsp;Desconto nos itens:</span>
        <span>{num2(descontoItens)}</span>
      </div>
      <div className="linha">
        <span>Desconto (-):</span>
        <span>{num2(venda.desconto)}</span>
      </div>
      <div className="linha">
        <span>Acréscimo (+):</span>
        <span>0,00</span>
      </div>

      <div className="separador" />

      <div className="linha total-grande">
        <span>Total:</span>
        <span>{num2(venda.total)}</span>
      </div>

      <div className="separador" />

      {venda.pagamentos.map(p => (
        <div className="linha" key={p.id}>
          <span>{FORMA_PAGAMENTO_LABEL[p.formaPagamento] ?? p.formaPagamento}</span>
          <span>{num2(p.valor)}</span>
        </div>
      ))}
      {venda.troco > 0 && (
        <div className="linha">
          <span>Troco</span>
          <span>{num2(venda.troco)}</span>
        </div>
      )}

      <div className="separador" />
      <div className="separador" />

      <p className="centro">Sistema desenvolvido por {SISTEMA.nome}</p>
      <p className="centro">Fone: {SISTEMA.telefones.join(' / ')}</p>
    </div>
  )
}
