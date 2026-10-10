export interface PerfilPermissaoItem {
  id?: number
  modulo: string
  podeVer: boolean
  podeCriar: boolean
  podeEditar: boolean
  podeExcluir: boolean
}

export interface Perfil {
  id: number
  nome: string
  descricao?: string
  protegido: boolean
  ativo: boolean
  superAdmin: boolean
  permissoes: PerfilPermissaoItem[]
  createdAt?: string
}

export interface Usuario {
  id: number
  nome: string
  login: string
  perfil: Perfil
  ativo: boolean
  percentualComissao: number
  createdAt?: string
}

export interface ModuloInfo {
  nome: string
  rotulo: string
}

export interface LogAuditoria {
  id: number
  usuarioId: number | null
  nomeUsuario: string
  perfilUsuario: string | null
  modulo: string
  acao: 'CRIAR' | 'EDITAR' | 'EXCLUIR'
  descricao: string | null
  criadoEm: string
}