import { useEffect, useRef } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import NotificacaoEstoque from './NotificacaoEstoque'
import FaixaAmbiente from './FaixaAmbiente'
import FaixaJarvis from './FaixaJarvis'
import AssistenteChat from './AssistenteChat'

export default function Layout() {
  const { pathname } = useLocation()
  const mainRef = useRef<HTMLElement>(null)

  // Ao trocar de tela, volta ao topo. Só o pathname: mudar filtros na URL da
  // mesma tela não deve tirar o usuário de onde está.
  useEffect(() => {
    mainRef.current?.scrollTo(0, 0)
  }, [pathname])

  return (
    // Altura travada na tela: só o <main> rola, e o menu lateral fica fixo.
    <div className="flex h-screen overflow-hidden bg-papel-50">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <FaixaAmbiente />
        <FaixaJarvis />
        <header className="h-14 bg-white border-b border-osso-300 flex items-center justify-end px-5 shrink-0">
          <AssistenteChat />
          <NotificacaoEstoque />
        </header>
        <main ref={mainRef} className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
