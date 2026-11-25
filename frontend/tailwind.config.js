/** @type {import('tailwindcss').Config} */
//
// Identidade visual SysFlex ERP — padrão "Dynamics": azul de marca sóbrio,
// neutros frios, cantos discretos e barra lateral azul-marinho.
//
// Duas camadas de cor:
//  1) Tokens de superfície (background, card, border, topbar...): valores em
//     variáveis CSS definidas em src/index.css (formato HSL).
//  2) Escalas semânticas (primary, success, warning, danger, info) com shades
//     50–950: é o que as telas usam (bg-primary-600, text-danger-700...).
//     Trocar a identidade visual no futuro = editar só este arquivo e o index.css.
//
// Nunca usar cores cruas do Tailwind (red-600, emerald-500...) nas telas.
// Ver DESIGN_SYSTEM.md.
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Segoe UI Variable Text', 'Segoe UI', 'Inter', 'system-ui', 'Roboto', 'sans-serif'],
      },
      colors: {
        // ─── Superfícies e texto (variáveis CSS do index.css) ─────────────
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        card: { DEFAULT: 'hsl(var(--card))', foreground: 'hsl(var(--card-foreground))' },
        popover: { DEFAULT: 'hsl(var(--popover))', foreground: 'hsl(var(--popover-foreground))' },
        secondary: { DEFAULT: 'hsl(var(--secondary))', foreground: 'hsl(var(--secondary-foreground))' },
        muted: { DEFAULT: 'hsl(var(--muted))', foreground: 'hsl(var(--muted-foreground))' },
        accent: { DEFAULT: 'hsl(var(--accent))', foreground: 'hsl(var(--accent-foreground))' },
        // Casca da aplicação: barra lateral e telas escuras (login, Jarvis)
        topbar: {
          DEFAULT: 'hsl(var(--topbar))',
          soft: 'hsl(var(--topbar-soft))',
          foreground: 'hsl(var(--topbar-foreground))',
        },

        // Barra lateral do sistema (azul claro). Com <alpha-value> para aceitar /10, /70 etc.
        sidebar: {
          DEFAULT: 'hsl(var(--sidebar) / <alpha-value>)',
          foreground: 'hsl(var(--sidebar-foreground) / <alpha-value>)',
          ativo: 'hsl(var(--sidebar-ativo) / <alpha-value>)',
          hover: 'hsl(var(--sidebar-hover) / <alpha-value>)',
          borda: 'hsl(var(--sidebar-borda) / <alpha-value>)',
        },

        // ─── Neutros frios (substituem o cinza padrão do Tailwind) ────────
        gray: {
          50: '#f8f9fb', 100: '#f1f3f6', 200: '#e1e5ea', 300: '#cdd3da', 400: '#8a94a1',
          500: '#5f6b7a', 600: '#4c5766', 700: '#38414d', 800: '#232b36', 900: '#1b2430', 950: '#0f1620',
        },

        // ─── Escalas semânticas ───────────────────────────────────────────
        // Marca (ações principais, foco, links)
        primary: {
          50: '#ebf3fc', 100: '#cfe4fa', 200: '#b4d6fa', 300: '#77b7f7', 400: '#479ef5', 500: '#2886de',
          600: '#0f6cbd', 700: '#115ea3', 800: '#0f548c', 900: '#0c3b5e', 950: '#082338',
        },
        // Concluído / positivo
        success: {
          50: '#f1faf1', 100: '#dff6dd', 200: '#c3ebc0', 300: '#9fd89f', 400: '#6bc16b', 500: '#2ea32e',
          600: '#107c10', 700: '#0b6a0b', 800: '#0a540a', 900: '#0a420a', 950: '#052b05',
        },
        // Pendente / atenção (âmbar: se distingue do vermelho do perigo)
        warning: {
          50: '#fffbeb', 100: '#fef0c2', 200: '#fde08a', 300: '#fbcb4a', 400: '#f5b400', 500: '#d99a00',
          600: '#9e6000', 700: '#8a5600', 800: '#6b4300', 900: '#4f3200', 950: '#2e1c00',
        },
        // Problema / destrutivo
        danger: {
          50: '#fdf3f4', 100: '#f8d7da', 200: '#f1b0b6', 300: '#e8838b', 400: '#e8707a', 500: '#d13438',
          600: '#c50f1f', 700: '#a10c19', 800: '#7f0914', 900: '#5c060e', 950: '#3d0409',
        },
        // Em andamento (azul-petróleo: se distingue do azul da marca)
        info: {
          50: '#e8f6f8', 100: '#c9ebf0', 200: '#9bd9e2', 300: '#62c0cf', 400: '#2fa5b8', 500: '#0e8a9e',
          600: '#0a7285', 700: '#085b6b', 800: '#064755', 900: '#043541', 950: '#032a33',
        },
      },
      // Movimento sutil dos cartões do fundo do login (respeita prefers-reduced-motion via motion-reduce)
      keyframes: {
        flutuar: { '0%, 100%': { transform: 'translateY(0)' }, '50%': { transform: 'translateY(-7px)' } },
      },
      animation: { flutuar: 'flutuar 9s ease-in-out infinite' },
      boxShadow: {
        card: '0 1.6px 3.6px rgba(0,0,0,0.08), 0 0.3px 0.9px rgba(0,0,0,0.06)',
        flutuante: '0 6.4px 14.4px rgba(0,0,0,0.16), 0 1.2px 3.6px rgba(0,0,0,0.12)',
      },
      // Cantos discretos (padrão Dynamics). xl/2xl acompanham, para cards e modais
      // ficarem coerentes com inputs e botões sem mexer em cada tela.
      borderRadius: {
        sm: 'calc(var(--radius) - 2px)',
        md: 'calc(var(--radius) - 1px)',
        lg: 'var(--radius)',
        xl: 'calc(var(--radius) + 2px)',
        '2xl': 'calc(var(--radius) + 4px)',
      },
    },
  },
  plugins: [],
}
