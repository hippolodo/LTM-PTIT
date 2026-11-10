import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'
import { api, apiError } from '../services/api.js'
import { currentUser } from '../services/credentials.js'
import { SocketClient } from '../services/socketClient.js'
import { WebRtcClient } from '../services/webrtcClient.js'
import { getIceServers } from '../services/iceConfig.js'
import { useMedia } from '../composables/useMedia.js'

const signalTypes = { WEBRTC_OFFER: 'CALL_OFFER', WEBRTC_ANSWER: 'CALL_ANSWER', ICE_CANDIDATE: 'CALL_ICE' }
const rtcTypes = { CALL_OFFER: 'WEBRTC_OFFER', CALL_ANSWER: 'WEBRTC_ANSWER', CALL_ICE: 'ICE_CANDIDATE' }
const errors = {
  OFFLINE: 'Người này chưa trực tuyến. Họ cần mở ứng dụng và đăng nhập để nhận cuộc gọi.',
  BUSY: 'Bạn hoặc người nhận đang có cuộc gọi/phòng ghép khác. Hãy thử lại sau.',
  CALL_UNAVAILABLE: 'Không thể gọi tài khoản này. Hãy mở lại cuộc trò chuyện.',
  RATE_LIMITED: 'Bạn thao tác quá nhanh. Hãy đợi rồi thử lại.',
  NOT_IN_CALL: 'Cuộc gọi đã kết thúc.',
  INVALID_CALL_STATE: 'Trạng thái cuộc gọi đã thay đổi. Hãy gọi lại.'
}

export const useCallStore = defineStore('calls', () => {
  const ready = ref(false), status = ref('IDLE'), callId = ref(null), conversationId = ref(null)
  const peer = ref(null), error = ref(''), notice = ref(''), remoteStream = shallowRef(null)
  const active = computed(() => status.value !== 'IDLE')
  const media = useMedia()
  let socket, userId, connectionGeneration = 0, callGeneration = 0, reconnectTimer, callTimer
  let pendingSignals = [], starting = false

  const rtc = new WebRtcClient({
    send: (type, id, payload) => socket?.send(signalTypes[type], id, payload),
    onStream: stream => { remoteStream.value = stream },
    onState: value => { if (callId.value) status.value = value },
    onError: () => {
      error.value = 'Không kết nối được video/âm thanh. Hãy gọi lại; mạng này có thể cần TURN.'
      hangup()
    }
  })

  function reset() {
    callGeneration++
    clearTimeout(callTimer)
    rtc.close()
    media.release()
    callId.value = conversationId.value = peer.value = null
    status.value = 'IDLE'
    pendingSignals = []
    starting = false
  }

  function hangup() {
    if (callId.value) socket?.send(status.value === 'INCOMING' ? 'CALL_DECLINE' : 'CALL_END', callId.value)
    reset()
  }

  function deadline(id, milliseconds) {
    clearTimeout(callTimer)
    callTimer = setTimeout(() => {
      if (callId.value !== id) return
      notice.value = 'Cuộc gọi hết thời gian chờ.'
      hangup()
    }, milliseconds)
  }

  async function startRtc(message) {
    if (starting || rtc.current) return
    const token = callGeneration
    starting = true
    status.value = 'CONNECTING'
    clearTimeout(callTimer)
    try {
      const iceServers = await getIceServers(message.payload.iceServers)
      if (token !== callGeneration) return
      if (!media.localStream.value) throw new Error('Missing local media')
      rtc.start({ roomId: message.roomId, initiator: message.payload.initiator, stream: media.localStream.value, iceServers })
      starting = false
      for (const signal of pendingSignals.splice(0)) rtc.signal(signal)
    } catch {
      if (token === callGeneration) {
        error.value = 'Không khởi tạo được cuộc gọi. Kiểm tra hỗ trợ WebRTC và cấu hình ICE.'
        hangup()
      }
    }
  }

  function dispatch(message) {
    if (message.type === 'SESSION_READY') { socket?.send('CALL_REGISTER'); return }
    if (message.type === 'CALL_READY') { ready.value = true; return }
    if (message.type === 'CALL_INCOMING') {
      if (active.value) { socket?.send('CALL_DECLINE', message.roomId); return }
      reset()
      error.value = notice.value = ''
      callId.value = message.roomId
      conversationId.value = message.payload.conversationId
      peer.value = message.payload.peer
      status.value = 'INCOMING'
      deadline(message.roomId, 35000)
      return
    }
    if (message.roomId && message.roomId !== callId.value) return
    if (message.type === 'CALL_RINGING' && callId.value) {
      status.value = 'OUTGOING'
      peer.value = message.payload.peer
    }
    if (message.type === 'CALL_ACCEPTED' && callId.value) startRtc(message)
    if (rtcTypes[message.type] && callId.value) {
      const signal = { ...message, type: rtcTypes[message.type] }
      if (!rtc.current) {
        if (pendingSignals.length >= 256) { error.value = 'Có quá nhiều tín hiệu cuộc gọi.'; hangup() }
        else pendingSignals.push(signal)
      } else rtc.signal(signal)
    }
    if (message.type === 'CALL_ENDED' && callId.value) {
      notice.value = ({ DECLINED: 'Người nhận đã từ chối cuộc gọi.', NO_ANSWER: 'Không có người trả lời.', DISCONNECTED: 'Đối tác đã mất kết nối.', ENDED: 'Cuộc gọi đã kết thúc.' })[message.payload.reason] || 'Cuộc gọi đã kết thúc.'
      reset()
    }
    if (message.type === 'CALL_ERROR' || message.type === 'ERROR') {
      if (message.type === 'CALL_ERROR' && !callId.value) return
      error.value = errors[message.payload?.code] || 'Không thực hiện được cuộc gọi. Hãy thử lại.'
      if (active.value) hangup()
    }
  }

  async function connect(id = currentUser.value?.id) {
    if (!id || socket || userId) return
    userId = id
    const token = ++connectionGeneration
    try {
      const { data } = await api.post('/api/socket-ticket')
      if (token !== connectionGeneration || currentUser.value?.id !== id) return
      const url = new URL(import.meta.env.VITE_WS_URL || 'ws://localhost:8081/ws')
      url.searchParams.set('ticket', data.ticket)
      socket = new SocketClient({
        url: url.toString(), onMessage: dispatch,
        onError: () => { error.value = 'Không kết nối được dịch vụ gọi video.' },
        onClose: () => {
          if (token !== connectionGeneration) return
          socket = null
          userId = null
          ready.value = false
          if (active.value) { notice.value = 'Cuộc gọi kết thúc vì mất kết nối.'; reset() }
          if (currentUser.value?.id === id) reconnectTimer = setTimeout(() => connect(id), 3000)
        }
      })
      socket.connect()
    } catch (e) {
      if (token === connectionGeneration) {
        userId = null
        error.value = apiError(e)
        if (currentUser.value?.id === id) reconnectTimer = setTimeout(() => connect(id), 3000)
      }
    }
  }

  async function dial(thread) {
    if (!ready.value || active.value || !thread) return
    reset()
    error.value = notice.value = ''
    const token = callGeneration
    callId.value = crypto.randomUUID()
    conversationId.value = thread.id
    peer.value = thread.peer
    status.value = 'PREPARING'
    try {
      const stream = await media.enable()
      if (token !== callGeneration || !stream) return
      status.value = 'OUTGOING'
      if (!socket?.send('CALL_INVITE', callId.value, { conversationId: thread.id })) throw new Error('Disconnected')
      deadline(callId.value, 35000)
    } catch {
      if (token === callGeneration) { error.value = media.mediaError.value || 'Không thể bắt đầu cuộc gọi.'; reset() }
    }
  }

  async function answer() {
    if (status.value !== 'INCOMING') return
    const token = callGeneration
    status.value = 'ACCEPTING'
    try {
      const stream = await media.enable()
      if (token !== callGeneration || !stream) return
      if (!socket?.send('CALL_ACCEPT', callId.value)) throw new Error('Disconnected')
    } catch {
      if (token === callGeneration) { error.value = media.mediaError.value || 'Không mở được camera/mic.'; hangup() }
    }
  }

  function disconnect() {
    hangup()
    connectionGeneration++
    clearTimeout(reconnectTimer)
    socket?.close()
    socket = null
    userId = null
    ready.value = false
  }

  return { ready, status, callId, conversationId, peer, error, notice, active, remoteStream, ...media,
    connect, disconnect, dial, answer, hangup, dispatch }
})
