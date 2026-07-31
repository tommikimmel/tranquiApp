import React, { createContext, useContext, useState } from 'react';

export type AlertType = 'success' | 'error' | 'info' | 'warning';

export interface AlertMessage {
  id: string;
  message: string;
  type: AlertType;
  title?: string;
}

interface AlertContextProps {
  alerts: AlertMessage[];
  showAlert: (message: string, type?: AlertType, title?: string) => void;
  removeAlert: (id: string) => void;
}

const AlertContext = createContext<AlertContextProps | undefined>(undefined);

function ToastIcon({ type, size = 18 }: { type: AlertType; size?: number }) {
  const style = { width: size, height: size };
  switch (type) {
    case 'success':
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={style}>
          <path d="M20 6L9 17l-5-5" />
        </svg>
      );
    case 'error':
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={style}>
          <circle cx="12" cy="12" r="10" />
          <path d="M15 9l-6 6M9 9l6 6" />
        </svg>
      );
    case 'warning':
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={style}>
          <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
          <path d="M12 9v4M12 17h.01" />
        </svg>
      );
    case 'info':
    default:
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={style}>
          <circle cx="12" cy="12" r="10" />
          <path d="M12 16v-4M12 8h.01" />
        </svg>
      );
  }
}

function getDefaultTitle(type: AlertType): string {
  switch (type) {
    case 'success':
      return '¡Operación exitosa!';
    case 'error':
      return 'Atención requerida';
    case 'warning':
      return 'Aviso importante';
    case 'info':
    default:
      return 'Información';
  }
}

export function AlertProvider({ children }: { children: React.ReactNode }) {
  const [alerts, setAlerts] = useState<AlertMessage[]>([]);

  const showAlert = (message: string, type: AlertType = 'success', title?: string) => {
    const id = String(Date.now()) + Math.random().toString(36).substr(2, 5);
    const resolvedTitle = title || getDefaultTitle(type);
    setAlerts((prev) => [...prev, { id, message, type, title: resolvedTitle }]);
    setTimeout(() => {
      removeAlert(id);
    }, 5000);
  };

  const removeAlert = (id: string) => {
    setAlerts((prev) => prev.filter((a) => a.id !== id));
  };

  return (
    <AlertContext.Provider value={{ alerts, showAlert, removeAlert }}>
      {children}
      {/* Redesigned Toast Notification HUD */}
      <div className="tranqui-toast-container" aria-live="polite">
        {alerts.map((a) => (
          <div
            key={a.id}
            className={`tranqui-toast tranqui-toast--${a.type}`}
            role="alert"
          >
            <div className="tranqui-toast__icon-box">
              <ToastIcon type={a.type} size={18} />
            </div>

            <div className="tranqui-toast__content">
              <span className="tranqui-toast__title">{a.title}</span>
              <span className="tranqui-toast__message">{a.message}</span>
            </div>

            <button
              onClick={() => removeAlert(a.id)}
              className="tranqui-toast__close"
              aria-label="Cerrar notificación"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" style={{ width: 14, height: 14 }}>
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>

            <div className="tranqui-toast__progress" />
          </div>
        ))}
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
