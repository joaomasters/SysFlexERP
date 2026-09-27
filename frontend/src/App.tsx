import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Layout from './shared/components/ui/Layout'
import PrivateRoute from './shared/components/ui/PrivateRoute'
import LoginPage from './modules/auth/LoginPage'
import PDVPage from './modules/pdv/PDVPage'
import SangriaPage from './modules/pdv/SangriaPage'
import ProdutosPage from './modules/estoque/ProdutosPage'
import DesossaPage from './modules/estoque/DesossaPage'
import InventarioPage from './modules/estoque/InventarioPage'
import PerdasPage from './modules/estoque/PerdasPage'
import FaturamentoPage from './modules/financeiro/FaturamentoPage'
import ClientesPage from './modules/financeiro/ClientesPage'
import ContasReceberPage from './modules/financeiro/ContasReceberPage'
import ContasPagarPage from './modules/financeiro/ContasPagarPage'
import DrePage from './modules/financeiro/DrePage'
import RelatoriosPage from './modules/financeiro/RelatoriosPage'
import CargaBalancaPage from './modules/balanca/CargaBalancaPage'
import RecebimentoPage from './modules/estoque/RecebimentoPage'
import FichasDesossaPage from './modules/estoque/FichasDesossaPage'
import NotaFiscalPage from './modules/fiscal/NotaFiscalPage'
import UsuariosPage from './modules/acesso/UsuariosPage'
import AuditoriaPage from './modules/acesso/AuditoriaPage'
import PerfisPage from './modules/acesso/PerfisPage'
import HomePage from './modules/home/HomePage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Rota pública */}
        <Route path="/login" element={<LoginPage />} />

        {/* PDV sem sidebar (autenticado mas sem Layout) */}
        <Route element={<PrivateRoute />}>
          <Route path="/pdv" element={<PDVPage />} />
        </Route>

        {/* Back-office: autenticado + Layout com sidebar */}
        <Route element={<PrivateRoute />}>
          <Route element={<Layout />}>
            <Route index element={<HomePage />} />
            <Route path="/inicio" element={<HomePage />} />
            <Route path="/pdv/sangria"               element={<SangriaPage />} />
            <Route path="/estoque/produtos"          element={<ProdutosPage />} />
            <Route path="/estoque/desossa"           element={<DesossaPage />} />
            <Route path="/estoque/inventario"        element={<InventarioPage />} />
            <Route path="/estoque/perdas"            element={<PerdasPage />} />
            <Route path="/estoque/recebimento"         element={<RecebimentoPage />} />
            <Route path="/estoque/fichas-desossa"     element={<FichasDesossaPage />} />
            <Route path="/fiscal/notas"              element={<NotaFiscalPage />} />
            <Route path="/financeiro/clientes"       element={<ClientesPage />} />
            <Route path="/financeiro/faturamento"    element={<FaturamentoPage />} />
            <Route path="/financeiro/contas-receber" element={<ContasReceberPage />} />
            <Route path="/financeiro/contas-pagar"   element={<ContasPagarPage />} />
            <Route path="/financeiro/dre"            element={<DrePage />} />
            <Route path="/financeiro/relatorios"     element={<RelatoriosPage />} />
            <Route path="/balanca"                   element={<CargaBalancaPage />} />
            <Route path="/acesso/usuarios"           element={<UsuariosPage />} />
            <Route path="/acesso/auditoria"          element={<AuditoriaPage />} />
            <Route path="/acesso/perfis"             element={<PerfisPage />} />
          </Route>
        </Route>

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/inicio" replace />} />
      </Routes>
    </BrowserRouter>
  )
}