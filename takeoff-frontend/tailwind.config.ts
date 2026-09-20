import type { Config } from 'tailwindcss'

/**
 * Tailwind CSS v4 is CSS-first: design tokens (colors, radii, shadows) live in
 * `src/index.css` inside `@theme`. This file is loaded from there through
 * `@config` and holds the JS-side theme extensions: fonts and animation keyframes.
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: [
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
        display: [
          'Sora',
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'sans-serif',
        ],
      },
      keyframes: {
        'cta-pulse': {
          '0%, 100%': { boxShadow: '0 0 0 0 rgb(79 140 255 / 0.55)' },
          '50%': { boxShadow: '0 0 0 14px rgb(79 140 255 / 0)' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
      },
      animation: {
        'cta-pulse': 'cta-pulse 2.4s ease-in-out infinite',
        shimmer: 'shimmer 1.6s infinite',
      },
    },
  },
} satisfies Config
