<script setup>
import { useCallStore } from '../stores/callStore.js'
import VideoPlayer from './VideoPlayer.vue'
const calls = useCallStore()
const labels = { PREPARING: 'Đang mở camera & microphone…', OUTGOING: 'Đang đổ chuông…', INCOMING: 'Cuộc gọi video đến', ACCEPTING: 'Đang mở camera & microphone…', CONNECTING: 'Đang kết nối video…', CONNECTED: 'Đang gọi video' }
</script>

<template>
  <aside v-if="calls.active" class="direct-call-panel panel" aria-label="Cuộc gọi video" aria-live="polite">
    <div class="direct-heading"><div><h2>{{ labels[calls.status] || calls.status }}</h2><p>{{ calls.peer?.displayName }} · @{{ calls.peer?.username }}</p></div></div>
    <p v-if="calls.status === 'INCOMING'">Camera và mic chỉ mở sau khi bạn chấp nhận. Cuộc gọi không được ghi lại.</p>
    <div v-if="calls.status !== 'INCOMING'" class="call-videos">
      <VideoPlayer :stream="calls.remoteStream" :label="calls.peer?.displayName || 'Đối tác'" placeholder="Đang chờ video đối tác…" />
      <VideoPlayer :stream="calls.localStream" muted label="Bạn" placeholder="Đang mở camera…" />
    </div>
    <div class="controls">
      <button v-if="calls.status === 'INCOMING'" class="primary" @click="calls.answer">Chấp nhận</button>
      <template v-if="calls.localStream">
        <button :aria-pressed="!calls.microphoneEnabled" @click="calls.toggleMic">{{ calls.microphoneEnabled ? 'Tắt mic' : 'Bật mic' }}</button>
        <button :aria-pressed="!calls.cameraEnabled" @click="calls.toggleCamera">{{ calls.cameraEnabled ? 'Tắt camera' : 'Bật camera' }}</button>
      </template>
      <button class="danger" @click="calls.hangup">{{ calls.status === 'INCOMING' ? 'Từ chối' : calls.status === 'OUTGOING' || calls.status === 'PREPARING' ? 'Hủy gọi' : 'Kết thúc' }}</button>
    </div>
  </aside>
  <div v-if="calls.error || calls.notice" class="call-notice" role="status"><span>{{ calls.error || calls.notice }}</span><button @click="calls.error = ''; calls.notice = ''">Đóng</button></div>
</template>
