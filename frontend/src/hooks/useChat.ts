import { useEffect, useRef, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { api } from '../api/api'

export interface Message {
  id?: number
  remitenteId?: number
  destinatarioId: number
  contenido: string
  fechaEnvio?: string
}

export function useChat(activeContactId: number | null, onMessageReceived?: (msg: Message) => void) {
  const [messages, setMessages] = useState<Message[]>([])
  const [connected, setConnected] = useState(false)
  const stompClientRef = useRef<Client | null>(null)
  const activeContactIdRef = useRef<number | null>(null)
  const onMessageReceivedRef = useRef(onMessageReceived)

  useEffect(() => {
    onMessageReceivedRef.current = onMessageReceived
  }, [onMessageReceived])

  // Keep ref up to date to avoid closures issues in message handler
  useEffect(() => {
    activeContactIdRef.current = activeContactId
  }, [activeContactId])

  // Load chat history when active contact changes
  useEffect(() => {
    if (!activeContactId) {
      setMessages([])
      return
    }

    // Guards against out-of-order responses: if the user switches contacts again
    // before this fetch resolves, a stale reply for the *previous* contact must not
    // overwrite the messages of the one now active.
    let cancelled = false
    setMessages([])
    api.getChatHistorial(activeContactId)
      .then((res: any) => {
        if (cancelled) return
        // Page<Mensaje> is returned by the backend, so we look for res.content
        if (res && res.content) {
          // Backend returns messages sorted DESC (latest first) for pagination,
          // so we reverse them to display chronologically (oldest to newest)
          const history = [...res.content].reverse().map((m: any) => ({
            id: m.id,
            remitenteId: m.remitente.id,
            destinatarioId: m.destinatario.id,
            contenido: m.contenido,
            fechaEnvio: m.fechaEnvio
          }))
          // A message can arrive over the WebSocket for this same contact while this
          // request is still in flight (appended optimistically below). Since the DB
          // snapshot this response reflects may predate that arrival, replacing the
          // array outright would make it vanish again — keep any such live arrival
          // that isn't already part of the fetched history.
          setMessages((prev) => {
            const historyIds = new Set(history.map((m) => m.id).filter((id) => id != null))
            const liveOnly = prev.filter((m) => m.id == null || !historyIds.has(m.id))
            return [...history, ...liveOnly]
          })
        }
      })
      .catch((err) => {
        if (!cancelled) console.error("Error al cargar historial de chat:", err)
      })

    return () => { cancelled = true }
  }, [activeContactId])

  // Connect to STOMP Broker
  useEffect(() => {
    const socketUrl = window.location.protocol === 'https:' ? `https://${window.location.host}/ws-tranqui` : `http://${window.location.hostname}:8081/ws-tranqui`;
    const socket = new SockJS(socketUrl, null, { withCredentials: true } as any)
    const client = new Client({
      webSocketFactory: () => socket,
      debug: (str) => {
        console.log('STOMP Debug:', str)
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    })

    client.onConnect = () => {
      setConnected(true)
      console.log('Conectado a WebSocket de Tranqui')

      // Subscribe to private user queue
      client.subscribe('/user/queue/mensajes', (stompMessage) => {
        try {
          const payload = JSON.parse(stompMessage.body)
          console.log('Mensaje WebSocket recibido:', payload)
          
          // Format message to our local interface
          const newMsg: Message = {
            id: payload.id,
            remitenteId: payload.remitenteId,
            destinatarioId: payload.destinatarioId,
            contenido: payload.contenido,
            fechaEnvio: payload.fechaEnvio
          }

          if (onMessageReceivedRef.current) {
            onMessageReceivedRef.current(newMsg)
          }

          // If the message belongs to the currently active conversation (either sent or received),
          // append it to the active messages list
          const activeContact = activeContactIdRef.current
          if (
            activeContact && 
            (newMsg.remitenteId === activeContact || newMsg.destinatarioId === activeContact)
          ) {
            setMessages((prev) => {
              // Deduplicate messages by id if present
              if (newMsg.id && prev.some((m) => m.id === newMsg.id)) {
                return prev
              }
              return [...prev, newMsg]
            })
          }
        } catch (e) {
          console.error('Error al procesar mensaje de WebSocket:', e)
        }
      })
    }

    client.onDisconnect = () => {
      setConnected(false)
      console.log('Desconectado de WebSocket de Tranqui')
    }

    client.onStompError = (frame) => {
      console.error('Error de STOMP Broker:', frame.headers['message'])
    }

    client.activate()
    stompClientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [])

  const sendMessage = useCallback((destId: number, content: string) => {
    const client = stompClientRef.current
    if (client && client.connected) {
      const messageBody = {
        destinatarioId: destId,
        contenido: content
      }
      client.publish({
        destination: '/app/chat.enviar',
        body: JSON.stringify(messageBody)
      })
      return true
    } else {
      console.warn('No se pudo enviar mensaje: WebSocket no conectado.')
      return false
    }
  }, [])

  return {
    messages,
    connected,
    sendMessage
  }
}
