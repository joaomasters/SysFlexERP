import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { getSessao, isAuthenticated } from '../../auth'

export default function PrivateRoute() {
  const { pathname } = useLocation()
  if (!isAuthenticated()) return <Navigate to="/login" replace />

  // O painel do JARVIS não acessa as telas de empresa: elas abrem pela visualização
  const sessao = getSessao()
  if (sessao?.jarvis && !sessao.somenteLeitura && pathname !== '/jarvis') {
    return <Navigate to="/jarvis" replace />
  }
  return <Outlet />
}
