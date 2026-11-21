import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { ArrowLeft } from 'lucide-react'
import PageHeader from './PageHeader'
import Card from './Card'
import Button from './Button'

interface FormularioPaginaProps {
  title: string
  subtitle?: string
  children: ReactNode
  /** Volta para a listagem. É o handler do botão "← Cancelar" (sempre exibido, como em Produto). */
  onClose: () => void
  /** Ação(ões) principal(is), à direita de Cancelar (ex.: Salvar). */
  actions?: ReactNode
}

/**
 * Formulário em tela cheia (largura total), idêntico em estrutura à tela de Cadastro de Produto:
 * título + subtítulo à esquerda, "← Cancelar" e a ação principal à direita, e o
 * formulário num Card logo abaixo. Substitui os modais de cadastro/lançamento.
 *
 * Uso: quando o formulário está aberto, a página devolve este componente no lugar
 * da listagem (`if (form) return <FormularioPagina ...>`). Os handlers são os mesmos
 * de antes — só mudam de lugar.
 *
 * Não usa <form>: o Enter dentro dos campos continua não enviando nada (importante
 * onde há leitor de código de barras), exatamente como era no modal.
 */
export default function FormularioPagina({ title, subtitle, children, onClose, actions }: FormularioPaginaProps) {
  const topo = useRef<HTMLDivElement>(null)

  // Ao abrir, começa do topo (a listagem pode estar rolada)
  useEffect(() => {
    topo.current?.scrollIntoView({ block: 'start' })
  }, [])

  return (
    <div ref={topo} className="p-4 sm:p-6">
      <PageHeader
        title={title}
        subtitle={subtitle}
        actions={
          <>
            <Button variant="secondary" onClick={onClose}>
              <ArrowLeft size={16} aria-hidden="true" /> Cancelar
            </Button>
            {actions}
          </>
        }
      />
      <Card padding="none">
        <div className="p-4 sm:p-6 space-y-4">{children}</div>
      </Card>
    </div>
  )
}
