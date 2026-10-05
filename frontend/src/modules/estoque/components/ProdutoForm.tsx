import { useEffect } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { Produto } from '@/types/produto'
import { Modal, Button, Field, baseInputClass, CurrencyInput, WeightInput } from '@/shared/components/ui'

interface Props {
  produto: Partial<Produto>
  onClose: () => void
  onSaved: () => void
}

export default function ProdutoForm({ produto, onClose, onSaved }: Props) {
  const isEdicao = Boolean(produto.id)
  const { register, handleSubmit, control, watch, setValue, formState: { errors } } = useForm<Produto>({
    defaultValues: produto,
  })

  const unidadeMedida = watch('unidadeMedida')
  const usaBalanca = unidadeMedida === 'KG' || unidadeMedida === 'G'
  const isUnidade = !usaBalanca

  useEffect(() => {
    if (!usaBalanca) {
      setValue('codigoBalanca', undefined)
    } else {
      setValue('marca', undefined)
      setValue('fornecedor', undefined)
    }
  }, [usaBalanca, setValue])

  const salvar = useMutation({
    mutationFn: (data: Produto) =>
      isEdicao
        ? api.put(`/estoque/produtos/${produto.id}`, data)
        : api.post('/estoque/produtos', data),
    onSuccess: () => {
      toast.success(isEdicao ? 'Produto atualizado!' : 'Produto criado!')
      onSaved()
    },
  })

  return (
    <Modal title={isEdicao ? 'Editar Produto' : 'Novo Produto'} onClose={onClose} maxWidth="lg">
      <form id="produto-form" onSubmit={handleSubmit(d => salvar.mutate(d))} className="space-y-4 -mt-1">
        <div className="grid grid-cols-2 gap-4">
          <Field label="Nome *" error={errors.nome?.message}>
            <input {...register('nome', { required: 'Obrigatório' })} className={baseInputClass} />
          </Field>
          <Field label="Unidade *">
            <select {...register('unidadeMedida', { required: true })} className={baseInputClass}>
              <option value="KG">KG — Quilo</option>
              <option value="UN">UN — Unidade</option>
              <option value="CX">CX — Caixa</option>
              <option value="G">G — Grama</option>
            </select>
          </Field>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Field label="Tipo *">
            <select {...register('tipoProduto', { required: true })} className={baseInputClass}>
              <option value="CORTE">Corte de Carne</option>
              <option value="INDUSTRIALIZADO">Industrializado</option>
              <option value="INSUMO">Insumo</option>
              <option value="SUBPRODUTO">Subproduto (sebo, osso...)</option>
            </select>
          </Field>
          <Field
            label="PLU Balança"
            hint={isUnidade ? 'Não se aplica a produtos vendidos por unidade ou caixa.' : undefined}
          >
            <input
              type="number"
              disabled={isUnidade}
              {...register('codigoBalanca')}
              placeholder="00001"
              className={isUnidade
                ? 'w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-gray-100 text-gray-400 cursor-not-allowed'
                : baseInputClass}
            />
          </Field>
        </div>

        <div className="grid grid-cols-3 gap-4">
          <Field label="Preço Venda (R$) *" error={errors.precoVenda ? 'Obrigatório' : undefined}>
            <Controller
              name="precoVenda"
              control={control}
              defaultValue={produto.precoVenda ?? 0}
              rules={{ required: true, min: 0.01 }}
              render={({ field }) => (
                <CurrencyInput value={field.value ?? 0} onChange={field.onChange} />
              )}
            />
          </Field>
          <Field
            label={isEdicao ? 'Custo (R$) — automático' : 'Custo (R$)'}
            hint={isEdicao ? 'Calculado pelas entradas de estoque.' : undefined}
          >
            <Controller
              name="precoCusto"
              control={control}
              defaultValue={produto.precoCusto ?? 0}
              render={({ field }) => (
                <CurrencyInput value={field.value ?? 0} onChange={field.onChange} disabled={isEdicao} />
              )}
            />
          </Field>
          <Field label="Estoque Mín.">
            <Controller
              name="estoqueMinimo"
              control={control}
              defaultValue={produto.estoqueMinimo ?? 0}
              render={({ field }) => (
                <WeightInput
                  value={field.value ?? 0}
                  onChange={field.onChange}
                  unit={(unidadeMedida ?? 'kg').toLowerCase()}
                  decimals={usaBalanca ? 3 : 0}
                />
              )}
            />
          </Field>
        </div>

        {/* Marca e Fornecedor — só fazem sentido pra produtos vendidos por
            unidade ou caixa (industrializados, insumos). Cortes por KG/G
            normalmente não têm uma marca comercial própria. */}
        {isUnidade && (
          <div className="grid grid-cols-2 gap-4">
            <Field label="Marca">
              <input
                {...register('marca')}
                placeholder="Ex: Sadia, Perdigão..."
                className={baseInputClass}
              />
            </Field>
            <Field label="Fornecedor">
              <input
                {...register('fornecedor')}
                placeholder="Ex: Distribuidora ABC"
                className={baseInputClass}
              />
            </Field>
          </div>
        )}

        <Field
          label="Validade padrão (dias)"
          hint="Opcional. Entradas de estoque sem validade informada (ex: cortes da desossa) geram lote com este prazo — é o que alimenta o alerta de validade."
          error={errors.validadePadraoDias?.message}
        >
          <input
            type="number"
            min={1}
            step={1}
            placeholder="Ex: 5"
            {...register('validadePadraoDias', {
              setValueAs: v => (v === '' || v == null ? null : Number(v)),
              validate: v => v == null || (Number.isInteger(v) && v > 0) || 'Informe um número inteiro maior que zero',
            })}
            className={baseInputClass}
          />
        </Field>

        <Field label="EAN-13 (industrializado)">
          <input
            {...register('ean13')}
            maxLength={13}
            placeholder="0000000000000"
            className={`${baseInputClass} font-mono`}
          />
        </Field>
      </form>

      <div className="flex gap-3 pt-2">
        <Button type="button" variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
        <Button type="submit" form="produto-form" variant="primary" fullWidth loading={salvar.isPending}>
          Salvar Produto
        </Button>
      </div>
    </Modal>
  )
}