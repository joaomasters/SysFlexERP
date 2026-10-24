export interface Fornecedor {
  id: number
  nome: string
  tipoPessoa: 'PF' | 'PJ'
  cpfCnpj?: string | null
  telefone?: string | null
  email?: string | null
  contato?: string | null
  endereco?: string | null
  ativo: boolean
  criadoPorNome?: string   // só vem para administradores
}

/** Item do dropdown de fornecedor no cadastro de produto. */
export interface OpcaoFornecedor {
  id: number
  nome: string
}