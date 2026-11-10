import { api, apiError } from '../services/api.js'
import { authorization } from '../services/credentials.js'
import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import { SocketClient } from '../services/socketClient.js'
import { WebRtcClient } from '../services/webrtcClient.js'
import { getIceServers } from '../services/iceConfig.js'
import { useMedia } from '../composables/useMedia.js'

export const useSessionStore = defineStore('session', () => {
  const guestId = ref(null), socketStatus = ref('CLOSED'), matchStatus = ref('IDLE'), roomId = ref(null)
  const partnerId = ref(null), partnerName = ref(''), partnerFriendship = ref('NONE'), incomingFriendRequest = ref(null)
  const error = ref(''), messages = ref([]), busy = ref(false)
  const remoteStream = shallowRef(null), retentionNotice = ref(''), persistentHistory = ref(false), expiresAt = ref(null)
  const partnerCameraEnabled = ref(false)

  const media = useMedia()
  let socket, iceServers = [], serverIceServers, action = 0

  const rtc = new WebRtcClient({
    send: (...args) => socket?.send(...args),
    onStream: s => { remoteStream.value = s },
    onState: s => { matchStatus.value = s },
    onData: data => {
      if (data?.type === 'CAMERA_STATE') {
        partnerCameraEnabled.value = !!data.enabled
      }
    },
    onDataChannelOpen: () => {
      rtc.sendData({ type: 'CAMERA_STATE', enabled: media.cameraEnabled.value })
    },
    onError: e => {
      error.value = e
      matchStatus.value = 'ERROR'
    }
  })

  function resetRoom() {
    rtc.close()
    roomId.value = null
    partnerId.value = null
    partnerName.value = ''
    partnerFriendship.value = 'NONE'
    incomingFriendRequest.value = null
    partnerCameraEnabled.value = false
    messages.value = []
  }

  function dispatch(m) {
    if (m.type === 'SESSION_READY') {
      guestId.value = m.payload.guestId
      serverIceServers = m.payload.iceServers
      socketStatus.value = 'OPEN'
    }
    if (m.type === 'WAITING') {
      matchStatus.value = 'WAITING'
      busy.value = false
    }
    if (m.type === 'MATCH_FOUND') {
      if (roomId.value === m.roomId) return
      resetRoom()
      retentionNotice.value = m.payload.retentionNotice || ''
      persistentHistory.value = !!m.payload.persistentHistory
      expiresAt.value = m.payload.expiresAt || null
      roomId.value = m.roomId
      partnerId.value = m.payload.partnerId
      partnerName.value = m.payload.partnerName || ''
      partnerFriendship.value = m.payload.partnerFriendship || 'NONE'
      if (m.payload.partnerFriendship === 'INCOMING') {
        incomingFriendRequest.value = {
          fromUserId: m.payload.partnerId,
          fromUserName: m.payload.partnerName || 'Đối tác'
        }
      }
      partnerCameraEnabled.value = false
      busy.value = false

      try {
        rtc.start({
          roomId: m.roomId,
          initiator: m.payload.initiator,
          stream: media.localStream.value,
          iceServers: (import.meta.env.VITE_ICE_CONFIG_URL || import.meta.env.VITE_ICE_SERVERS)
            ? iceServers
            : (m.payload.iceServers || iceServers)
        })
      } catch {
        error.value = 'Trình duyệt không hỗ trợ WebRTC. Chat chữ vẫn sẵn sàng.'
        matchStatus.value = 'ERROR'
      }
    }
    if (['CHAT_MESSAGE', 'CHAT_FILE', 'CHAT_AUDIO'].includes(m.type) && m.roomId === roomId.value && !messages.value.some(x => x.messageId === m.payload.messageId)) {
      messages.value.push(m.payload)
      if (m.payload.senderId !== guestId.value) {
        socket?.send('MESSAGE_DELIVERED', roomId.value, { messageId: m.payload.messageId })
        if (globalThis.document?.visibilityState === 'visible') {
          socket?.send('MESSAGE_READ', roomId.value, { messageId: m.payload.messageId })
        }
      }
    }
    if (m.type === 'MESSAGE_STATUS' && m.roomId === roomId.value) {
      const message = messages.value.find(x => x.messageId === m.payload.messageId)
      if (message) message.status = m.payload.status
    }
    if (['WEBRTC_OFFER', 'WEBRTC_ANSWER', 'ICE_CANDIDATE'].includes(m.type) && m.roomId === roomId.value) {
      rtc.signal(m)
    }
    if (m.type === 'FRIEND_REQUEST' && m.roomId === roomId.value) {
      partnerFriendship.value = 'INCOMING'
      incomingFriendRequest.value = {
        fromUserId: m.payload.fromUserId || partnerId.value,
        fromUserName: m.payload.fromUserName || partnerName.value || 'Đối tác'
      }
    }
    if (m.type === 'FRIEND_ACCEPT' && m.roomId === roomId.value) {
      partnerFriendship.value = 'ACCEPTED'
      incomingFriendRequest.value = null
    }
    if (m.type === 'FRIEND_DECLINE' && m.roomId === roomId.value) {
      if (partnerFriendship.value === 'OUTGOING') partnerFriendship.value = 'NONE'
      incomingFriendRequest.value = null
    }
    if (m.type === 'PARTNER_LEFT' && m.roomId === roomId.value) {
      resetRoom()
      media.release()
      matchStatus.value = 'PARTNER_LEFT'
      busy.value = false
    }
    if (m.type === 'ROOM_CLOSED' && (!roomId.value || m.roomId === roomId.value)) {
      resetRoom()
      matchStatus.value = 'IDLE'
    }
    if (m.type === 'ERROR') {
      error.value = m.payload.message
      busy.value = false
    }
  }

  async function connect() {
    if (socket || socketStatus.value === 'CONNECTING' || !authorization()) return
    const token = ++action
    socketStatus.value = 'CONNECTING'
    error.value = ''
    try {
      const { data } = await api.post('/api/socket-ticket')
      if (token !== action) return
      const url = new URL(import.meta.env.VITE_WS_URL || 'ws://localhost:8081/ws')
      url.searchParams.set('ticket', data.ticket)
      socket = new SocketClient({
        url: url.toString(),
        onMessage: dispatch,
        onError: e => { error.value = e },
        onClose: () => {
          socket = null
          action++
          socketStatus.value = 'CLOSED'
          resetRoom()
          media.release()
          matchStatus.value = 'ERROR'
          busy.value = false
          error.value = 'Mất kết nối. Nhấn Kết nối lại để bắt đầu phiên mới.'
        }
      })
      socket.connect()
    } catch (e) {
      if (token === action) {
        socketStatus.value = 'CLOSED'
        error.value = apiError(e)
      }
    }
  }

  async function enableMedia() {
    error.value = ''
    try {
      await media.enable()
    } catch {
      error.value = media.mediaError.value
    }
  }

  async function toggleCamera() {
    await media.toggleCamera()
    rtc.sendData({ type: 'CAMERA_STATE', enabled: media.cameraEnabled.value })
  }

  async function start() {
    if (busy.value || roomId.value || matchStatus.value === 'WAITING' || socketStatus.value !== 'OPEN') return
    const token = ++action
    busy.value = true
    error.value = ''
    try {
      const stream = await media.enable()
      if (!stream) return
      iceServers = await getIceServers(serverIceServers)
      if (token !== action) return
      if (!socket?.send('FIND_PARTNER')) throw new Error('Socket closed')
    } catch {
      if (token === action) {
        error.value = media.mediaError.value || 'Không thể bắt đầu. Kiểm tra kết nối và cấu hình ICE.'
        media.release()
      }
    } finally {
      if (token === action && matchStatus.value !== 'WAITING') busy.value = false
    }
  }

  function next() {
    if (!roomId.value || busy.value) return
    const old = roomId.value
    resetRoom()
    error.value = ''
    busy.value = true
    matchStatus.value = 'WAITING'
    if (!socket?.send('NEXT_PARTNER', old)) {
      busy.value = false
      matchStatus.value = 'ERROR'
    }
  }

  function receivedAttachment(message) {
    if (roomId.value && !messages.value.some(m => m.messageId === message.messageId)) {
      messages.value.push(message)
    }
  }

  function sendChat(text) {
    text = text.trim()
    if (!roomId.value || !text || text.length > 2000) return false
    return socket?.send('CHAT_MESSAGE', roomId.value, { text, clientMessageId: crypto.randomUUID() }) || false
  }

  function stop() {
    socket?.send('LEAVE_ROOM', roomId.value)
    dispose()
    matchStatus.value = 'IDLE'
    error.value = ''
  }

  async function sendFriendRequest(fromUserName = '') {
    if (!roomId.value || !partnerId.value) return
    await api.post(`/api/social/friends/${partnerId.value}`)
    partnerFriendship.value = 'OUTGOING'
    socket?.send('FRIEND_REQUEST', roomId.value, {
      fromUserId: guestId.value,
      fromUserName
    })
  }

  async function acceptFriendRequest() {
    if (!partnerId.value) return
    await api.patch(`/api/social/friends/${partnerId.value}`, { accept: true })
    partnerFriendship.value = 'ACCEPTED'
    incomingFriendRequest.value = null
    socket?.send('FRIEND_ACCEPT', roomId.value, {})
  }

  async function declineFriendRequest() {
    if (!partnerId.value) return
    await api.patch(`/api/social/friends/${partnerId.value}`, { accept: false })
    partnerFriendship.value = 'NONE'
    incomingFriendRequest.value = null
    socket?.send('FRIEND_DECLINE', roomId.value, {})
  }

  function dispose() {
    action++
    socket?.close()
    socket = null
    resetRoom()
    media.release()
    socketStatus.value = 'CLOSED'
    guestId.value = null
    busy.value = false
  }

  return {
    expiresAt,
    receivedAttachment,
    retentionNotice,
    persistentHistory,
    guestId,
    socketStatus,
    matchStatus,
    roomId,
    partnerId,
    partnerName,
    partnerFriendship,
    incomingFriendRequest,
    partnerCameraEnabled,
    error,
    messages,
    busy,
    remoteStream,
    ...media,
    toggleCamera,
    connect,
    start,
    next,
    stop,
    sendChat,
    sendFriendRequest,
    acceptFriendRequest,
    declineFriendRequest,
    dispose,
    dispatch,
    enableMedia
  }
})
