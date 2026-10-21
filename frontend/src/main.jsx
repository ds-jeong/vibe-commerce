import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css' // Tailwind CSS 바인딩 레이어
import { installLoadingFetch } from './utils/loadingBar.js'

const apiBase = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
installLoadingFetch((input) => {
  if (
    apiBase &&
    typeof input === 'string' &&
    (input.startsWith('/api') || input.startsWith('/uploads'))
  ) {
    return `${apiBase}${input}`
  }
  return input
})

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
