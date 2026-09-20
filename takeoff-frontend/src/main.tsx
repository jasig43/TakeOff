import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { apiClient } from './api/client'
import './index.css'

// Wake a sleeping free-tier API while the visitor is still on the sign-in page, so their first real request is fast.
void apiClient.get('/health').catch(() => undefined)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
