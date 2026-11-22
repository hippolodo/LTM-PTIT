import { beforeEach, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
const fakes = vi.hoisted(() => ({ socket: null, peers: [] }))
vi.mock('../services/socketClient.js', () => ({
  SocketClient: class {
    constructor(options) {
      this.options = options
      this.send = vi.fn(() => true)
      this.close = vi.fn()
      fakes.socket = this
    }
    connect() {}
  }
}))
vi.mock('../services/webrtcClient.js', () => ({
  WebRtcClient: class {
    constructor() {
      this.start = vi.fn()
      this.close = vi.fn()
      this.signal = vi.fn()
      fakes.peers.push(this)
    }
  }
}))
vi.mock('../services/api.js', () => ({
  api: { post: vi.fn(async () => ({ data: { ticket: 'test-ticket' } })) },
  apiError: () => ''
}))
import { guestToken } from '../services/credentials.js'
import { useSessionStore } from './sessionStore.js'
beforeEach(() => {
  setActivePinia(createPinia())
  guestToken.value = 'test-guest'
})
it('dispatches room lifecycle, ignores stale messages, Next and Stop clean resources', async () => {
  const s = useSessionStore()
  await s.connect()
  s.dispatch({ type: 'SESSION_READY', payload: { guestId: 'me' } })
  expect(s.socketStatus).toBe('OPEN')
  s.dispatch({ type: 'WAITING' })
  expect(s.matchStatus).toBe('WAITING')
  s.dispatch({
    type: 'MATCH_FOUND',
    roomId: 'one',
    payload: { partnerId: 'peer', initiator: true }
  })
  expect(s.roomId).toBe('one')
  s.dispatch({ type: 'CHAT_MESSAGE', roomId: 'old', payload: { text: 'stale' } })
  expect(s.messages).toHaveLength(0)
  s.dispatch({ type: 'CHAT_MESSAGE', roomId: 'one', payload: { text: 'hello', messageId: '1' } })
  s.dispatch({ type: 'CHAT_MESSAGE', roomId: 'one', payload: { text: 'hello', messageId: '1' } })
  expect(s.messages).toHaveLength(1)
  const peer = fakes.peers.at(-1),
    before = peer.close.mock.calls.length
  s.next()
  expect(peer.close.mock.calls.length).toBeGreaterThan(before)
  expect(fakes.socket.send).toHaveBeenCalledWith('NEXT_PARTNER', 'one')
  expect(s.roomId).toBeNull()
  s.stop()
  expect(fakes.socket.close).toHaveBeenCalledOnce()
  expect(s.matchStatus).toBe('IDLE')
  expect(s.localStream).toBeNull()
})
it('partner leaves without automatic rematching', async () => {
  const s = useSessionStore()
  await s.connect()
  s.dispatch({
    type: 'MATCH_FOUND',
    roomId: 'one',
    payload: { partnerId: 'peer', initiator: false }
  })
  s.dispatch({ type: 'PARTNER_LEFT', roomId: 'old' })
  expect(s.roomId).toBe('one')
  s.dispatch({ type: 'PARTNER_LEFT', roomId: 'one' })
  expect(s.roomId).toBeNull()
  expect(s.matchStatus).toBe('PARTNER_LEFT')
  expect(fakes.socket.send).not.toHaveBeenCalled()
})

it.each(['NEXT', 'STOP'])('automatically queues after partner %s while preserving media and the socket', async (reason) => {
  const s = useSessionStore()
  await s.connect()
  s.dispatch({ type: 'SESSION_READY', payload: { guestId: 'me' } })
  const stopTrack = vi.fn()
  const stream = { getTracks: () => [{ stop: stopTrack }] }
  s.localStream = stream
  s.dispatch({ type: 'MATCH_FOUND', roomId: 'one', payload: { partnerId: 'peer' } })
  s.dispatch({ type: 'PARTNER_LEFT', roomId: 'old', payload: { reason } })
  expect(fakes.socket.send).not.toHaveBeenCalled()
  s.dispatch({ type: 'PARTNER_LEFT', roomId: 'one', payload: { reason } })
  expect(s.roomId).toBeNull()
  expect(s.matchStatus).toBe('WAITING')
  expect(s.localStream).toBe(stream)
  expect(stopTrack).not.toHaveBeenCalled()
  expect(fakes.socket.close).not.toHaveBeenCalled()
  expect(fakes.socket.send).toHaveBeenCalledExactlyOnceWith('FIND_PARTNER')
  s.stop()
  expect(stopTrack).toHaveBeenCalledOnce()
  expect(s.matchStatus).toBe('IDLE')
})

it('does not automatically queue when a partner event arrives after the user stops', async () => {
  const s = useSessionStore()
  await s.connect()
  s.dispatch({ type: 'SESSION_READY', payload: { guestId: 'me' } })
  s.dispatch({ type: 'MATCH_FOUND', roomId: 'one', payload: { partnerId: 'peer' } })
  const socket = fakes.socket
  s.stop()
  socket.send.mockClear()
  s.dispatch({ type: 'PARTNER_LEFT', roomId: 'one', payload: { reason: 'STOP' } })
  expect(socket.send).not.toHaveBeenCalled()
  expect(s.matchStatus).toBe('IDLE')
  expect(s.socketStatus).toBe('CLOSED')
})

it('releases media and reports failure if automatic queueing cannot send', async () => {
  const s = useSessionStore()
  await s.connect()
  s.dispatch({ type: 'SESSION_READY', payload: { guestId: 'me' } })
  s.dispatch({ type: 'MATCH_FOUND', roomId: 'one', payload: { partnerId: 'peer' } })
  fakes.socket.send.mockReturnValue(false)
  s.dispatch({ type: 'PARTNER_LEFT', roomId: 'one', payload: { reason: 'NEXT' } })
  expect(s.matchStatus).toBe('ERROR')
  expect(s.error).not.toBe('')
  expect(s.localStream).toBeNull()
})
