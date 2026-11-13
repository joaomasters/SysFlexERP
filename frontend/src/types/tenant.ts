export interface Tenant {
  id: number
  codigo: string
  slug: string
  nome: string
  schemaName: string
  ativo: boolean
  /** Módulos industriais liberados pela plataforma */
  moduloIndustrial: boolean
}
