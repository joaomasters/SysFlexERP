export interface ConfiguracaoComprovante {
  nomeFantasia: string
  cnpj?: string | null
  endereco?: string | null
  bairro?: string | null
  cidade?: string | null
  telefone?: string | null
  mensagemRodape?: string | null
  larguraPapelMm: 58 | 80
  vias: number
  /** false = o PDV pergunta se imprime ao finalizar a venda */
  imprimirAutomatico: boolean
  mostrarAvisoNaoFiscal: boolean
  mostrarOperador: boolean
  mostrarCliente: boolean
}

/** Pagamento como aparece no cupom (vem da venda fechada ou do que o PDV enviou). */
export interface PagamentoCupom {
  formaPagamento: string
  valor: number
  parcelas?: number | null
}
