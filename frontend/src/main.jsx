import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css' // Tailwind CSS 바인딩 레이어

const apiBase = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
if (apiBase && typeof window !== 'undefined') {
  const originalFetch = window.fetch.bind(window)
  window.fetch = (input, init) => {
    if (typeof input === 'string' && (input.startsWith('/api') || input.startsWith('/uploads'))) {
      return originalFetch(`${apiBase}${input}`, init)
    }
    return originalFetch(input, init)
  }
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
