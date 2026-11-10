import { ref, onMounted } from 'vue'

const currentTheme = ref('dark')

function applyTheme(theme) {
  currentTheme.value = theme
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('data-theme', theme)
    try {
      localStorage.setItem('randomconnect-theme', theme)
    } catch {}
  }
}

export function useTheme() {
  function initTheme() {
    let saved = null
    try {
      saved = localStorage.getItem('randomconnect-theme')
    } catch {}
    if (saved === 'light' || saved === 'dark') {
      applyTheme(saved)
    } else if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: light)').matches) {
      applyTheme('light')
    } else {
      applyTheme('dark')
    }
  }

  function toggleTheme() {
    applyTheme(currentTheme.value === 'dark' ? 'light' : 'dark')
  }

  return {
    theme: currentTheme,
    toggleTheme,
    initTheme,
  }
}
