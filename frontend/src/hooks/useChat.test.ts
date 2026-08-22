import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useChat } from './useChat'
import { api } from '../api/api'

// ── Mock @stomp/stompjs ─────────────────────────────────────────────────────
// A minimal fake STOMP Client that records what useChat does with it (activate/deactivate,
// subscribe callback, published frames) and lets tests manually fire onConnect/onDisconnect
// to simulate the broker's lifecycle, since there is no real WebSocket server in jsdom.
class MockClient {
  static instances: MockClient[] = []
  config: any
  connected = false
  onConnect: (() => void) | null = null
  onDisconnect: (() => void) | null = null
  onStompError: ((frame: any) => void) | null = null
  activateCalls = 0
  deactivateCalls = 0
  publishCalls: Array<{ destination: string; body: string }> = []
  subscriptions = new Map<string, (msg: { body: string }) => void>()

  constructor(config: any) {
    this.config = config
    MockClient.instances.push(this)
  }

  activate() {
    this.activateCalls++
  }

  deactivate() {
    this.deactivateCalls++
  }

  subscribe(destination: string, cb: (msg: { body: string }) => void) {
    this.subscriptions.set(destination, cb)
    return { unsubscribe: () => {} }
  }

  publish(frame: { destination: string; body: string }) {
    this.publishCalls.push(frame)
  }

  // Test helper: simulate the broker completing the CONNECT handshake.
  simulateConnect() {
    this.connected = true
    this.onConnect?.()
  }

  // Test helper: simulate a message arriving on /user/queue/mensajes.
  simulateIncoming(payload: any) {
    this.subscriptions.get('/user/queue/mensajes')?.({ body: JSON.stringify(payload) })
  }
}

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation(function MockClientCtor(this: any, config: any) {
    return new MockClient(config)
  }),
}))

vi.mock('sockjs-client', () => ({
  default: vi.fn().mockImplementation(function MockSockJS() { return {} as any }),
}))

vi.mock('../api/api', () => ({
  api: {
    getChatHistorial: vi.fn(),
  },
  getBackendOrigin: () => 'http://localhost:8081',
}))

const mockedApi = vi.mocked(api)

function lastClient(): MockClient {
  return MockClient.instances[MockClient.instances.length - 1]
}

function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: any) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

describe('useChat', () => {
  beforeEach(() => {
    MockClient.instances.length = 0
    vi.clearAllMocks()
    mockedApi.getChatHistorial.mockResolvedValue({ content: [] })
  })

  it('activates a STOMP client on mount and reflects connected state once onConnect fires', async () => {
    const { result } = renderHook(() => useChat(null))

    expect(lastClient().activateCalls).toBe(1)
    expect(result.current.connected).toBe(false)

    act(() => { lastClient().simulateConnect() })

    expect(result.current.connected).toBe(true)
  })

  it('deactivates the STOMP client on unmount', () => {
    const { unmount } = renderHook(() => useChat(null))
    const client = lastClient()
    unmount()
    expect(client.deactivateCalls).toBe(1)
  })

  it('sets connected back to false when onDisconnect fires', () => {
    const { result } = renderHook(() => useChat(null))
    act(() => { lastClient().simulateConnect() })
    expect(result.current.connected).toBe(true)

    act(() => { lastClient().onDisconnect?.() })
    expect(result.current.connected).toBe(false)
  })

  it('appends an incoming message addressed to the active contact', () => {
    const { result } = renderHook(() => useChat(42))
    act(() => { lastClient().simulateConnect() })

    act(() => {
      lastClient().simulateIncoming({ id: 1, remitenteId: 42, destinatarioId: 99, contenido: 'Hola', fechaEnvio: '2026-01-01T10:00:00' })
    })

    expect(result.current.messages).toHaveLength(1)
    expect(result.current.messages[0].contenido).toBe('Hola')
  })

  it('appends a message where the active contact is the sender (destinatarioId matches)', () => {
    const { result } = renderHook(() => useChat(42))
    act(() => { lastClient().simulateConnect() })

    act(() => {
      lastClient().simulateIncoming({ id: 1, remitenteId: 99, destinatarioId: 42, contenido: 'Respuesta', fechaEnvio: '2026-01-01T10:01:00' })
    })

    expect(result.current.messages).toHaveLength(1)
  })

  it('ignores an incoming message unrelated to the active contact', () => {
    const { result } = renderHook(() => useChat(42))
    act(() => { lastClient().simulateConnect() })

    act(() => {
      lastClient().simulateIncoming({ id: 1, remitenteId: 7, destinatarioId: 8, contenido: 'No es para vos', fechaEnvio: '2026-01-01T10:00:00' })
    })

    expect(result.current.messages).toHaveLength(0)
  })

  it('does not append anything when there is no active contact at all', () => {
    const { result } = renderHook(() => useChat(null))
    act(() => { lastClient().simulateConnect() })

    act(() => {
      lastClient().simulateIncoming({ id: 1, remitenteId: 7, destinatarioId: 8, contenido: 'x', fechaEnvio: '2026-01-01T10:00:00' })
    })

    expect(result.current.messages).toHaveLength(0)
  })

  it('deduplicates an incoming message that already has the same id in state', () => {
    const { result } = renderHook(() => useChat(42))
    act(() => { lastClient().simulateConnect() })

    const payload = { id: 5, remitenteId: 42, destinatarioId: 99, contenido: 'Hola', fechaEnvio: '2026-01-01T10:00:00' }
    act(() => { lastClient().simulateIncoming(payload) })
    act(() => { lastClient().simulateIncoming(payload) })

    expect(result.current.messages).toHaveLength(1)
  })

  it('always invokes the onMessageReceived callback, even for a message outside the active conversation', () => {
    const onMessageReceived = vi.fn()
    renderHook(() => useChat(42, onMessageReceived))
    act(() => { lastClient().simulateConnect() })

    act(() => {
      lastClient().simulateIncoming({ id: 1, remitenteId: 1, destinatarioId: 2, contenido: 'otro chat', fechaEnvio: '2026-01-01T10:00:00' })
    })

    expect(onMessageReceived).toHaveBeenCalledTimes(1)
    expect(onMessageReceived).toHaveBeenCalledWith(expect.objectContaining({ contenido: 'otro chat' }))
  })

  it('picks up a changed onMessageReceived callback without needing to reconnect', () => {
    const first = vi.fn()
    const second = vi.fn()
    const { rerender } = renderHook(({ cb }) => useChat(42, cb), { initialProps: { cb: first } })
    act(() => { lastClient().simulateConnect() })

    rerender({ cb: second })

    act(() => {
      lastClient().simulateIncoming({ id: 1, remitenteId: 42, destinatarioId: 99, contenido: 'x', fechaEnvio: '2026-01-01' })
    })

    expect(first).not.toHaveBeenCalled()
    expect(second).toHaveBeenCalledTimes(1)
    // Only a single Client should have been created — swapping the callback must not reconnect.
    expect(MockClient.instances).toHaveLength(1)
  })

  it('logs STOMP errors reported via onStompError without throwing', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    renderHook(() => useChat(null))

    expect(() => {
      lastClient().onStompError?.({ headers: { message: 'broker unreachable' } })
    }).not.toThrow()
    expect(consoleSpy).toHaveBeenCalledWith('Error de STOMP Broker:', 'broker unreachable')
    consoleSpy.mockRestore()
  })

  it('wires up a debug callback that logs STOMP frames', () => {
    const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    renderHook(() => useChat(null))

    lastClient().config.debug('CONNECT frame')

    expect(consoleSpy).toHaveBeenCalledWith('STOMP Debug:', 'CONNECT frame')
    consoleSpy.mockRestore()
  })

  it('silently swallows a malformed WebSocket payload instead of throwing', () => {
    renderHook(() => useChat(42))
    act(() => { lastClient().simulateConnect() })

    expect(() => {
      act(() => {
        lastClient().subscriptions.get('/user/queue/mensajes')?.({ body: 'not-json' })
      })
    }).not.toThrow()
  })

  describe('sendMessage', () => {
    it('publishes to /app/chat.enviar and returns true when connected', () => {
      const { result } = renderHook(() => useChat(42))
      const client = lastClient()
      act(() => { client.simulateConnect() })

      let sent: boolean = false
      act(() => { sent = result.current.sendMessage(42, 'Hola doctor') })

      expect(sent).toBe(true)
      expect(client.publishCalls).toHaveLength(1)
      expect(client.publishCalls[0].destination).toBe('/app/chat.enviar')
      expect(JSON.parse(client.publishCalls[0].body)).toEqual({ destinatarioId: 42, contenido: 'Hola doctor' })
    })

    it('returns false and does not publish when the client is not connected', () => {
      const { result } = renderHook(() => useChat(42))
      const client = lastClient()
      // Note: simulateConnect() not called, so client.connected stays false.

      let sent: boolean = true
      act(() => { sent = result.current.sendMessage(42, 'Hola') })

      expect(sent).toBe(false)
      expect(client.publishCalls).toHaveLength(0)
    })
  })

  describe('chat history loading', () => {
    it('fetches and reverses history (backend returns DESC) into chronological order on contact change', async () => {
      mockedApi.getChatHistorial.mockResolvedValueOnce({
        content: [
          { id: 2, remitente: { id: 99 }, destinatario: { id: 42 }, contenido: 'segundo', fechaEnvio: '2026-01-01T10:01:00' },
          { id: 1, remitente: { id: 42 }, destinatario: { id: 99 }, contenido: 'primero', fechaEnvio: '2026-01-01T10:00:00' },
        ],
      })

      const { result } = renderHook(() => useChat(42))

      await waitFor(() => expect(result.current.messages).toHaveLength(2))
      expect(result.current.messages.map(m => m.contenido)).toEqual(['primero', 'segundo'])
      expect(mockedApi.getChatHistorial).toHaveBeenCalledWith(42)
    })

    it('clears messages when activeContactId becomes null', async () => {
      mockedApi.getChatHistorial.mockResolvedValueOnce({
        content: [{ id: 1, remitente: { id: 42 }, destinatario: { id: 99 }, contenido: 'hola', fechaEnvio: '2026-01-01' }],
      })
      const { result, rerender } = renderHook(({ id }) => useChat(id), { initialProps: { id: 42 as number | null } })
      await waitFor(() => expect(result.current.messages).toHaveLength(1))

      rerender({ id: null })
      expect(result.current.messages).toHaveLength(0)
    })

    it('discards a stale history response for a contact the user has since navigated away from', async () => {
      const firstFetch = deferred<any>()
      const secondFetch = deferred<any>()
      mockedApi.getChatHistorial.mockImplementationOnce(() => firstFetch.promise)

      const { result, rerender } = renderHook(({ id }) => useChat(id), { initialProps: { id: 1 as number | null } })

      mockedApi.getChatHistorial.mockImplementationOnce(() => secondFetch.promise)
      rerender({ id: 2 })

      // The contact-1 request resolves *after* the switch to contact 2 — its (now stale) data
      // must not clobber the state now that contact 2 is active.
      await act(async () => {
        firstFetch.resolve({ content: [{ id: 101, remitente: { id: 1 }, destinatario: { id: 9 }, contenido: 'viejo', fechaEnvio: '2026-01-01' }] })
        await Promise.resolve()
      })
      expect(result.current.messages).toHaveLength(0)

      await act(async () => {
        secondFetch.resolve({ content: [{ id: 202, remitente: { id: 2 }, destinatario: { id: 9 }, contenido: 'nuevo', fechaEnvio: '2026-01-02' }] })
        await Promise.resolve()
      })
      await waitFor(() => expect(result.current.messages).toHaveLength(1))
      expect(result.current.messages[0].contenido).toBe('nuevo')
    })

    it('keeps a live-arrived message (not yet in the DB snapshot) when the history fetch resolves', async () => {
      const historyFetch = deferred<any>()
      mockedApi.getChatHistorial.mockImplementationOnce(() => historyFetch.promise)

      const { result } = renderHook(() => useChat(42))
      act(() => { lastClient().simulateConnect() })

      // A message arrives over the socket while the historial GET is still in flight.
      act(() => {
        lastClient().simulateIncoming({ id: 999, remitenteId: 42, destinatarioId: 9, contenido: 'llega en vivo', fechaEnvio: '2026-01-01T12:00:00' })
      })
      expect(result.current.messages).toHaveLength(1)

      await act(async () => {
        historyFetch.resolve({
          content: [{ id: 1, remitente: { id: 42 }, destinatario: { id: 9 }, contenido: 'historico', fechaEnvio: '2026-01-01T10:00:00' }],
        })
        await Promise.resolve()
      })

      expect(result.current.messages.map(m => m.contenido)).toEqual(['historico', 'llega en vivo'])
    })

    it('logs but does not throw when the history fetch rejects', async () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      mockedApi.getChatHistorial.mockRejectedValueOnce(new Error('boom'))

      const { result } = renderHook(() => useChat(42))

      await waitFor(() => expect(consoleSpy).toHaveBeenCalled())
      expect(result.current.messages).toHaveLength(0)
      consoleSpy.mockRestore()
    })
  })
})
