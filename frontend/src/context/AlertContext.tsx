import React, { createContext, useContext, useState } from 'react';

function AlertIcon({ type, size = 16 }: { type: AlertType; size?: number }) {
  const common = { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, style: { width: size, height: size } };
  switch (type) {
    case 'error':
      return <svg {...common}><circle cx="12" cy="12" r="10" /><line x1="15" y1="9" x2="9" y2="15" /><line x1="9" y1="9" x2="15" y2="15" /></svg>;
    case 'warning':
      return <svg {...common}><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" /><line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" /></svg>;
    case 'info':
      return <svg {...common}><circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" /></svg>;
    default:
      return <svg {...common}><polyline points="20 6 9 17 4 12" /></svg>;
  }
}

function CloseIcon({ size = 12 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" style={{ width: size, height: size }}>
      <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  );
}

export type AlertType = 'success' | 'error' | 'info' | 'warning';

export interface AlertMessage {
  id: string;
  message: string;
  type: AlertType;
}

interface AlertContextProps {
  alerts: AlertMessage[];
  showAlert: (message: string, type?: AlertType) => void;
  removeAlert: (id: string) => void;
}

const AlertContext = createContext<AlertContextProps | undefined>(undefined);

export function AlertProvider({ children }: { children: React.ReactNode }) {
  const [alerts, setAlerts] = useState<AlertMessage[]>([]);

  const showAlert = (message: string, type: AlertType = 'success') => {
    const id = String(Date.now()) + Math.random().toString(36).substr(2, 5);
    setAlerts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      removeAlert(id);
    }, 4500);
  };

  const removeAlert = (id: string) => {
    setAlerts((prev) => prev.filter((a) => a.id !== id));
  };

  return (
    <AlertContext.Provider value={{ alerts, showAlert, removeAlert }}>
      {children}
      {/* Toast Notification HUD */}
      <div style={{
        position: 'fixed',
        bottom: '24px',
        right: '24px',
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
        zIndex: 999999,
        maxWidth: '380px',
        width: '100%'
      }}>
        {alerts.map((a) => {
          const isError = a.type === 'error';
          const isWarning = a.type === 'warning';
          const isInfo = a.type === 'info';
          
          const bg = isError ? '#fef2f2' : isWarning ? '#fffbeb' : isInfo ? '#f0f9ff' : '#f0fdf4';
          const border = isError ? '1px solid #fecaca' : isWarning ? '1px solid #fef3c7' : isInfo ? '1px solid #e0f2fe' : '1px solid #dcfce7';
          const text = isError ? '#991b1b' : isWarning ? '#92400e' : isInfo ? '#075985' : '#166534';

          return (
            <div key={a.id} style={{
              backgroundColor: bg,
              border: border,
              borderRadius: 'var(--radius-lg)',
              padding: 'var(--space-3) var(--space-4)',
              color: text,
              fontSize: 'var(--text-sm)',
              fontWeight: '600',
              boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 'var(--space-2.5)',
              animation: 'slideInRight 0.25s cubic-bezier(0.16, 1, 0.3, 1)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <AlertIcon type={a.type} />
                <span>{a.message}</span>
              </div>
              <button
                onClick={() => removeAlert(a.id)}
                style={{ border: 'none', background: 'none', color: 'inherit', cursor: 'pointer', padding: '0 4px', opacity: 0.6, display: 'flex' }}
              >
                <CloseIcon />
              </button>
            </div>
          );
        })}
      </div>
    </AlertContext.Provider>
  );
}

export function useAlert() {
  const context = useContext(AlertContext);
  if (!context) {
    throw new Error('useAlert must be used within an AlertProvider');
  }
  return context;
}
