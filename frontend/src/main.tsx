import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './styles/index.css'
import App from './App.tsx'
import SiteAccessGate from './components/SiteAccessGate.tsx'

import { AlertProvider } from './context/AlertContext.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SiteAccessGate>
      <BrowserRouter>
        <AlertProvider>
          <App />
        </AlertProvider>
      </BrowserRouter>
    </SiteAccessGate>
  </StrictMode>,
)
