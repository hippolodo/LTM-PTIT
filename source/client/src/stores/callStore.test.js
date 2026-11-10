import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
const fakes = vi.hoisted(() => ({ socket: null, rtc: null, media: null }))
vi.mock('../services/socketClient.js', () => ({ SocketClient: class {
  constructor(options) { this.options = options; this.send = vi.fn(() => true); this.close = vi.fn(); fakes.socket = this }
  connect() {}
} }))
vi.mock('../services/webrtcClient.js', () => ({ WebRtcClient: class {
  constructor(options) { this.options = options; this.current = null; fakes.rtc = this }
  start = vi.fn(() => { this.current = {} })
  signal = vi.fn()
  close = vi.fn(() => { this.current = null })
} }))
vi.mock('../services/iceConfig.js', () => ({ getIceServers: vi.fn(async servers => servers || []) }))
vi.mock('../services/api.js', () => ({ api: { post: vi.fn(async () => ({ data: { ticket: 'ticket' } })) }, apiError: () => 'Network error' }))
import { currentUser } from '../services/credentials.js'
import { useCallStore } from './callStore.js'
import { getIceServers } from '../services/iceConfig.js'

let calls
beforeEach(async () => {
  vi.useFakeTimers()
  setActivePinia(createPinia())
  currentUser.value = { id: 'me' }
  vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: vi.fn(async () => stream()) } })
  calls = useCallStore()
  await calls.connect('me')
  calls.dispatch({ type: 'SESSION_READY', payload: {} })
  calls.dispatch({ type: 'CALL_READY', payload: {} })
})
afterEach(() => { calls?.disconnect(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers(); currentUser.value = null })

function stream() {
  const audio = { readyState: 'live', enabled: true, stop: vi.fn() }, video = { readyState: 'live', enabled: true, stop: vi.fn() }
  return { getTracks: () => [audio, video], getAudioTracks: () => [audio], getVideoTracks: () => [video] }
}
const incoming = id => ({ type: 'CALL_INCOMING', roomId: id, payload: { conversationId: 'thread', peer: { id: 'peer', displayName: 'Peer' } } })

it('receives a call without requesting camera until explicit acceptance', async () => {
  const devices = navigator.mediaDevices
  calls.dispatch(incoming('one'))
  expect(calls.status).toBe('INCOMING')
  expect(devices.getUserMedia).not.toHaveBeenCalled()
  await calls.answer()
  expect(devices.getUserMedia).toHaveBeenCalledOnce()
  expect(fakes.socket.send).toHaveBeenCalledWith('CALL_ACCEPT', 'one')
  calls.dispatch({ type: 'CALL_ACCEPTED', roomId: 'one', payload: { initiator: false, iceServers: [] } })
  await vi.waitFor(() => expect(fakes.rtc.start).toHaveBeenCalledOnce())
})

it('cancels late camera permission without sending an invitation or leaking tracks', async () => {
  let resolve
  navigator.mediaDevices.getUserMedia.mockImplementationOnce(() => new Promise(r => { resolve = r }))
  const pending = calls.dial({ id: 'thread', peer: { id: 'peer' } })
  calls.hangup()
  const media = stream(); resolve(media); await pending
  expect(media.getTracks()[0].stop).toHaveBeenCalledOnce()
  expect(fakes.socket.send.mock.calls.filter(call => call[0] === 'CALL_INVITE')).toHaveLength(0)
  expect(calls.active).toBe(false)
})

it('buffers signals while asynchronous ICE configuration is loading and ignores stale calls', async () => {
  await calls.dial({ id: 'thread', peer: { id: 'peer' } })
  const id = calls.callId
  let resolveIce
  getIceServers.mockImplementationOnce(() => new Promise(resolve => { resolveIce = resolve }))
  calls.dispatch({ type: 'CALL_ACCEPTED', roomId: id, payload: { initiator: true, iceServers: [] } })
  calls.dispatch({ type: 'CALL_ICE', roomId: id, payload: { candidate: { candidate: 'early' } } })
  calls.dispatch({ type: 'CALL_ENDED', roomId: 'old', payload: { reason: 'ENDED' } })
  expect(calls.active).toBe(true)
  resolveIce([])
  await vi.waitFor(() => expect(fakes.rtc.signal).toHaveBeenCalledWith(expect.objectContaining({ type: 'ICE_CANDIDATE', roomId: id })))
  calls.hangup();expect(calls.localStream).toBeNull()
})

it('rejects another incoming call while busy and handles offline/timeout/disconnect', async () => {
  await calls.dial({ id: 'thread', peer: { id: 'peer' } })
  const id = calls.callId
  calls.dispatch(incoming('other'))
  expect(fakes.socket.send).toHaveBeenCalledWith('CALL_DECLINE', 'other')
  calls.dispatch({ type: 'CALL_ERROR', roomId: id, payload: { code: 'OFFLINE' } })
  expect(calls.error).toContain('chưa trực tuyến');expect(calls.localStream).toBeNull()
  await calls.dial({ id: 'thread', peer: { id: 'peer' } })
  await vi.advanceTimersByTimeAsync(35000)
  expect(calls.active).toBe(false)
  calls.dispatch(incoming('new'))
  fakes.socket.options.onClose()
  expect(calls.active).toBe(false);expect(calls.ready).toBe(false)
})

it('ends rather than declines when cancelling an acceptance already sent to the server', async () => {
  calls.dispatch(incoming('one'))
  await calls.answer()
  expect(calls.status).toBe('ACCEPTING')
  calls.hangup()
  expect(fakes.socket.send).toHaveBeenLastCalledWith('CALL_END', 'one')
  expect(calls.active).toBe(false)
})
