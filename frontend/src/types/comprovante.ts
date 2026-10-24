export interface ConfiguracaoComprovante {
  nomeFantasia: string
  cnpj?: string | null
  endereco?: string | null
  bairro?: string | null
  cidade?: string | null
  telefone?: string | null
  mensagemRodape?: string | null
  larguraPapelMm: 58 | 80
  /** folga à direita (0–20 mm) para drivers que cortam o fim das linhas */
  margemDireitaMm: number
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
