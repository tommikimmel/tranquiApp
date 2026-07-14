import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './styles/index.css'
import App from './App.tsx'

import { AlertProvider } from './context/AlertContext.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AlertProvider>
        <App />
      </AlertProvider>
    </BrowserRouter>
  </StrictMode>,
)
