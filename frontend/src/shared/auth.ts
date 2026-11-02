export interface PermissaoModulo {
  modulo: string
  rotulo: string
  ver: boolean
  criar: boolean
  editar: boolean
  excluir: boolean
}

export interface SessaoUsuario {
  token: string
  usuarioId: number
  username: string
  nome: string
  perfil: string
  superAdmin: boolean
  /** Super admin da empresa principal — único que gerencia empresas. Ausente em sessões antigas (= false). */
  administradorPlataforma?: boolean
  permissoes: PermissaoModulo[]
  /** Sessão do JARVIS: sem somenteLeitura é o painel; com ele, a visualização de uma empresa. */
  jarvis?: boolean
  somenteLeitura?: boolean
  empresa?: { codigo: string; nome: string }
}

const SESSAO_KEY = 'acougue_sessao'
// Fica na aba: o painel continua aberto e cada aba pode ver uma empresa diferente
const VISUALIZACAO_KEY = 'jarvis_visualizacao'

export const getSessao = (): SessaoUsuario | null => {
  const raw = sessionStorage.getItem(VISUALIZACAO_KEY) ?? localStorage.getItem(SESSAO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as SessaoUsuario
  } catch {
    return null
  }
}

export const setSessao = (sessao: SessaoUsuario): void => {
  sessionStorage.removeItem(VISUALIZACAO_KEY)
  localStorage.setItem(SESSAO_KEY, JSON.stringify(sessao))
}

export const removeSessao = (): void => {
  sessionStorage.removeItem(VISUALIZACAO_KEY)
  localStorage.removeItem(SESSAO_KEY)
}

export const iniciarVisualizacao = (sessao: SessaoUsuario): void => {
  sessionStorage.setItem(VISUALIZACAO_KEY, JSON.stringify(sessao))
}

export const encerrarVisualizacao = (): void => {
  sessionStorage.removeItem(VISUALIZACAO_KEY)
}

// A sessão é compartilhada entre as abas: login ou logout em uma recarrega as outras,
// para nenhuma ficar mostrando uma empresa enquanto grava em outra.
window.addEventListener('storage', (e) => {
  if (e.key === SESSAO_KEY || e.key === null) window.location.reload()
})

// Mantidos por compatibilidade com código existente que só precisa do token
export const getToken = (): string | null => getSessao()?.token ?? null
export const removeToken = (): void => removeSessao()
export const isAuthenticated = (): boolean => !!getToken()

// Usado em qualquer fluxo que precise atrelar uma ação ao operador logado
// (abrir/fechar caixa, sangria, venda) — evita operadorId fixo no código.
export const getUsuarioId = (): number | null => getSessao()?.usuarioId ?? null
export const getNomeUsuario = (): string | null => getSessao()?.nome ?? null