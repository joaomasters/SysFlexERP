interface FilterTabsProps<T extends string> {
  options: { value: T; label: string }[]
  value: T
  onChange: (value: T) => void
}

/**
 * Abas de filtro em pílula (usadas para filtrar por status em listas).
 * Modelo de referência: filtro de status da tela Contas a Receber.
 */
export default function FilterTabs<T extends string>({ options, value, onChange }: FilterTabsProps<T>) {
  return (
    <>
      {options.map(opt => (
        <button
          key={opt.value}
          type="button"
          onClick={() => onChange(opt.value)}
          className={`px-4 py-1.5 rounded-full text-xs font-medium transition-colors
            ${value === opt.value
              ? 'bg-gray-800 text-white'
              : 'border border-gray-300 text-gray-500 hover:bg-gray-50'}`}
        >
          {opt.label}
        </button>
      ))}
    </>
  )
}
