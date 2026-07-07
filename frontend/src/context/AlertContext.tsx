import React, { createContext, useContext, useState } from 'react';

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
          const icon = isError ? '❌' : isWarning ? '⚠️' : isInfo ? 'ℹ️' : '✓';

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
                <span style={{ fontSize: '16px' }}>{icon}</span>
                <span>{a.message}</span>
              </div>
              <button 
                onClick={() => removeAlert(a.id)}
                style={{ border: 'none', background: 'none', color: 'inherit', cursor: 'pointer', fontWeight: 'bold', fontSize: '11px', padding: '0 4px', opacity: 0.6 }}
              >
                ✕
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
