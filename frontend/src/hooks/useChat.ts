import { useEffect, useRef, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { api } from './api'

export interface Message {
  id?: number
  remitenteId?: number
  destinatarioId: number
  contenido: string
  fechaEnvio?: string
}

export function useChat(activeContactId: number | null) {
  const [messages, setMessages] = useState<Message[]>([])
  const [connected, setConnected] = useState(false)
  const stompClientRef = useRef<Client | null>(null)
  const activeContactIdRef = useRef<number | null>(null)

  // Keep ref up to date to avoid closures issues in message handler
  useEffect(() => {
    activeContactIdRef.current = activeContactId
  }, [activeContactId])

  // Load chat history when active contact changes
  useEffect(() => {
    if (activeContactId) {
      setMessages([])
      api.getChatHistorial(activeContactId)
        .then((res: any) => {
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
            setMessages(history)
          }
        })
        .catch((err) => {
          console.error("Error al cargar historial de chat:", err)
        })
    } else {
      setMessages([])
    }
  }, [activeContactId])

  // Connect to STOMP Broker
  useEffect(() => {
    const socket = new SockJS('http://localhost:8081/ws-tranqui')
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
