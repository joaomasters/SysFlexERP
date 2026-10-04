import { getSessao } from '../auth'

export type Acao = 'ver' | 'criar' | 'editar' | 'excluir'

/**
 * Hook de permissões — usado pra esconder/desabilitar botões e itens de
 * menu conforme o perfil do usuário logado.
 *
 * IMPORTANTE: isso é só uma camada de experiência (UX). A segurança de
 * verdade acontece no backend (@ExigirPermissao) — mesmo que alguém force
 * a exibição de um botão pelo DevTools, a chamada à API vai ser barrada
 * com 403 do mesmo jeito.
 */
export function usePermissao() {
  const sessao = getSessao()

  function pode(modulo: string, acao: Acao): boolean {
    if (!sessao) return false
    if (sessao.superAdmin) return true
    const permissao = sessao.permissoes.find(p => p.modulo === modulo)
    if (!permissao) return false
    return permissao[acao]
  }

  function podeVer(modulo: string)     { return pode(modulo, 'ver') }
  function podeCriar(modulo: string)   { return pode(modulo, 'criar') }
  function podeEditar(modulo: string)  { return pode(modulo, 'editar') }
  function podeExcluir(modulo: string) { return pode(modulo, 'excluir') }

  /**
   * Quem pode ver QUEM fez cada ação (nome do usuário em Recebimento, Perdas,
   * Inventário, Contas etc.). Espelha UsuarioAutenticado#podeVerIdentificacaoUsuarios
   * no backend: hoje = poder ver a Auditoria (SUPER_ADMIN e ADMIN).
   * Só controla o que é EXIBIDO — o backend já nem envia esses dados a quem não pode.
   */
  const podeVerIdentificacao = pode('AUDITORIA', 'ver')

  return {
    sessao,
    pode,
    podeVerIdentificacao,
    podeVer,
    podeCriar,
    podeEditar,
    podeExcluir,
    isSuperAdmin: sessao?.superAdmin ?? false,
    perfil: sessao?.perfil ?? null,
    nome: sessao?.nome ?? null,
  }
}