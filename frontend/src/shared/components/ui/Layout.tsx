import { useEffect, useRef, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Menu } from 'lucide-react'
import Sidebar from './Sidebar'
import NotificacaoEstoque from './NotificacaoEstoque'
import FaixaAmbiente from './FaixaAmbiente'
import FaixaJarvis from './FaixaJarvis'
import AssistenteChat from './AssistenteChat'
import logo from '../../../assets/sysflex-logo-escuro.png'

export default function Layout() {
  const { pathname } = useLocation()
  const mainRef = useRef<HTMLElement>(null)
  // Só vale abaixo de lg: no desktop o menu lateral fica sempre visível.
  const [menuAberto, setMenuAberto] = useState(false)

  // Ao trocar de tela, volta ao topo e fecha o menu (celular). Só o pathname:
  // mudar filtros na URL da mesma tela não deve tirar o usuário de onde está.
  useEffect(() => {
    mainRef.current?.scrollTo(0, 0)
    setMenuAberto(false)
  }, [pathname])

  return (
    // Altura travada na tela: só o <main> rola, e o menu lateral fica fixo.
    // h-dvh: no Safari do iPhone o 100vh passa por baixo da barra do navegador.
    <div className="flex h-screen h-dvh flex-col overflow-hidden bg-background">
      {/* As faixas de ambiente/Jarvis ocupam a largura toda, acima do menu e do cabeçalho: assim o bloco
          do logo (menu lateral) e o cabeçalho ficam sempre alinhados. */}
      <FaixaAmbiente />
      <FaixaJarvis />
      <div className="flex min-h-0 flex-1">
        <Sidebar aberto={menuAberto} onFechar={() => setMenuAberto(false)} />
        <div className="flex-1 min-w-0 flex flex-col overflow-hidden">
          <header className="h-14 bg-card border-b border-border flex items-center gap-2 px-3 sm:px-5 shrink-0">
            {/* Botão do menu + logo: só no celular/tablet, onde o menu lateral vira gaveta */}
            <button
              onClick={() => setMenuAberto(true)}
              aria-label="Abrir menu"
              className="lg:hidden -ml-1 p-2 rounded-lg text-foreground hover:bg-gray-100"
            >
              <Menu size={22} />
            </button>
            <img src={logo} alt="SysFlex ERP" className="lg:hidden h-6 w-auto" />
            <div className="ml-auto flex items-center">
              <AssistenteChat />
              <NotificacaoEstoque />
            </div>
          </header>
          {/* relative: elementos absolutos das telas (ex.: sr-only, popovers) ficam presos ao
              <main> e rolam com ele. Sem isso eram posicionados pela página inteira, que ficava
              mais alta que a janela: a tela "subia" e sobrava uma faixa vazia embaixo. */}
          <main ref={mainRef} className="relative flex-1 overflow-auto">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  )
}
