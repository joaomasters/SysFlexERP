/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // Identidade SysFlex ERP — inspirada em equipamento de balcão
        // profissional (aço inox, etiqueta de peso, carimbo de tinta).
        aco: {
          800: '#2C3036',
          900: '#23262B',
        },
        talho: {
          50:  '#FBECE9',
          100: '#F5CFC7',
          400: '#C15A44',
          500: '#B5402F',
          600: '#A32B1E',
          700: '#872418',
        },
        papel: {
          50: '#F1F2EE',
        },
        osso: {
          200: '#EFEEE8',
          300: '#D9D4CB',
        },
        fresco: {
          50:  '#E9F1EC',
          100: '#D2E5DA',
          500: '#3B7D5D',
          600: '#2F6B4F',
          700: '#245640',
        },
        mostarda: {
          50:  '#FBF0DF',
          100: '#F5DFB8',
          500: '#C6860F',
          600: '#B9780F',
          700: '#96600C',
        },

        // ─── Tokens semânticos ────────────────────────────────────────────
        // Camada de significado por cima da identidade visual acima: todo
        // componente/tela deve usar ESTES nomes (bg-primary-600, text-danger-700,
        // etc.) em vez de cores "cruas" do Tailwind (red-600, emerald-600...).
        // Isso garante que trocar a identidade visual no futuro seja uma
        // mudança em um único lugar. Ver DESIGN_SYSTEM.md.
        primary: {
          50: '#FBECE9', 100: '#F5CFC7', 400: '#C15A44',
          500: '#B5402F', 600: '#A32B1E', 700: '#872418',
        },
        success: {
          50: '#E9F1EC', 100: '#D2E5DA', 500: '#3B7D5D', 600: '#2F6B4F', 700: '#245640',
        },
        warning: {
          50: '#FBF0DF', 100: '#F5DFB8', 500: '#C6860F', 600: '#B9780F', 700: '#96600C',
        },
        danger: {
          50: '#FDEDEC', 100: '#FBD5D2', 500: '#D6483A', 600: '#C23A2C', 700: '#9E2E22',
        },
        info: {
          50: '#EAF1FB', 100: '#CFE1F7', 500: '#3E7BC4', 600: '#2E66AC', 700: '#25518A',
        },
      },
      fontFamily: {
        sans: ['"Public Sans"', 'system-ui', 'sans-serif'],
        display: ['"Archivo Expanded"', '"Public Sans"', 'system-ui', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'monospace'],
      },
    },
  },
  plugins: [],
}