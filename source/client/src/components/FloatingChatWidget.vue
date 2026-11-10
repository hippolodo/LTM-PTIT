<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/authStore.js'
import { useDirectChat } from '../composables/useDirectChat.js'
import { useVoiceRecorder } from '../composables/useVoiceRecorder.js'
import { useCallStore } from '../stores/callStore.js'
import { api, apiError } from '../services/api.js'
import AttachmentView from './AttachmentView.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const calls = useCallStore()
const chat = reactive(useDirectChat())
const voice = reactive(useVoiceRecorder())

const isOpen = ref(false)
const isMinimized = ref(false)
const searchQuery = ref('')
const overview = ref({ friends: [], incoming: [], outgoing: [], threads: [] })
const loadingOverview = ref(false)
const messagesContainer = ref(null)

// Instant view transition
const activeFriend = ref(null)
const loadingThread = ref(false)
const isSending = ref(false)

// Unified attachment state
const filePicker = ref(null)
const pendingFile = ref(null)
const pendingFilePreview = ref('')
const recordedAudioFile = ref(null)

let overviewTimer = null
let disposed = false

async function loadSocialOverview() {
  if (!auth.user) return
  try {
    loadingOverview.value = true
    const { data } = await api.get('/api/social')
    if (!disposed) {
      overview.value = data || { friends: [], incoming: [], outgoing: [], threads: [] }
    }
  } catch (e) {
    // Silent fail for polling
  } finally {
    if (!disposed) loadingOverview.value = false
  }
}

function startPolling() {
  stopPolling()
  loadSocialOverview()
  overviewTimer = setInterval(loadSocialOverview, 8000)
}

function stopPolling() {
  if (overviewTimer) {
    clearInterval(overviewTimer)
    overviewTimer = null
  }
}

const filteredFriends = computed(() => {
  const list = overview.value.friends || []
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return list
  return list.filter(f =>
    (f.displayName && f.displayName.toLowerCase().includes(q)) ||
    (f.username && f.username.toLowerCase().includes(q))
  )
})

const pendingCount = computed(() => {
  return (overview.value.incoming || []).length
})

function toggleWidget() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    isMinimized.value = false
    loadSocialOverview()
  }
}

// 1. Instant open: Chuyển màn hình ngay lập tức 0ms, không bắt người dùng chờ
async function selectFriend(friend) {
  activeFriend.value = friend
  isMinimized.value = false
  loadingThread.value = true
  clearPendingAttachments()

  try {
    await chat.open(friend)
  } finally {
    loadingThread.value = false
    await nextTick()
    scrollToBottom()
  }
}

function closeChatThread() {
  activeFriend.value = null
  clearPendingAttachments()
  chat.close()
}

function closeAll() {
  isOpen.value = false
  isMinimized.value = false
  activeFriend.value = null
  clearPendingAttachments()
  chat.close()
}

function minimize() {
  isMinimized.value = !isMinimized.value
}

function scrollToBottom() {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

function formatMessageTime(isoString) {
  if (!isoString) return ''
  try {
    const d = new Date(isoString)
    const now = new Date()
    const isToday = d.toDateString() === now.toDateString()
    const timeStr = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    if (isToday) return timeStr
    return `${d.getDate()}/${d.getMonth() + 1} ${timeStr}`
  } catch {
    return ''
  }
}

// ==========================================================================
// UNIFIED ATTACHMENTS (Đồng bộ file & ghi âm thành 1 nút gửi duy nhất)
// ==========================================================================
function triggerFilePicker() {
  if (isSending.value) return
  filePicker.value?.click()
}

function onFileSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  if (file.size > 10485760) {
    chat.error = 'File tối đa 10 MB.'
    return
  }

  // Clear previous voice if any
  clearRecordedAudio()

  pendingFile.value = file
  if (['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
    pendingFilePreview.value = URL.createObjectURL(file)
  } else {
    pendingFilePreview.value = ''
  }
}

function clearPendingFile() {
  if (pendingFilePreview.value) {
    URL.revokeObjectURL(pendingFilePreview.value)
    pendingFilePreview.value = ''
  }
  pendingFile.value = null
}

function toggleVoiceRecord() {
  if (voice.recording) {
    voice.stop()
  } else {
    clearPendingFile()
    clearRecordedAudio()
    voice.start()
  }
}

watch(() => voice.blob, (blob) => {
  if (blob) {
    recordedAudioFile.value = voice.file()
  }
})

function clearRecordedAudio() {
  voice.cancel()
  recordedAudioFile.value = null
}

function clearPendingAttachments() {
  clearPendingFile()
  clearRecordedAudio()
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

// 2. Gửi tin nhắn siêu nhanh (Optimistic UI Update) + gửi tệp/âm thanh đồng bộ
async function handleSend() {
  const text = chat.draft.trim()
  const file = pendingFile.value
  const voiceFile = recordedAudioFile.value
  const roomId = chat.selected?.id

  if ((!text && !file && !voiceFile) || isSending.value || !roomId) return

  isSending.value = true
  chat.error = ''

  try {
    // A. Gửi tin nhắn văn bản (hiện ngay lập tức 0ms trên màn hình)
    if (text) {
      chat.draft = ''
      const tempId = `temp-${Date.now()}`
      const optimisticMsg = {
        messageId: tempId,
        text,
        senderId: auth.user.id,
        sentAt: new Date().toISOString(),
        status: 'SENT'
      }
      chat.messages.push(optimisticMsg)
      await nextTick()
      scrollToBottom()

      // Gửi ngầm tới server
      api.post(`/api/social/direct/${roomId}/messages`, {
        text,
        clientMessageId: crypto.randomUUID()
      }).then(({ data }) => {
        const idx = chat.messages.findIndex(m => m.messageId === tempId)
        if (idx !== -1) chat.messages.splice(idx, 1, data)
      }).catch(e => {
        const idx = chat.messages.findIndex(m => m.messageId === tempId)
        if (idx !== -1) chat.messages.splice(idx, 1)
        chat.draft = text
        chat.error = apiError(e)
      })
    }

    // B. Gửi tệp đính kèm (nếu có)
    if (file) {
      const form = new FormData()
      form.append('file', file)
      form.append('clientMessageId', crypto.randomUUID())
      form.append('voice', 'false')
      clearPendingFile()

      const { data } = await api.post(`/api/conversations/${roomId}/attachments`, form, {
        timeout: 60000
      })
      chat.receivedAttachment(roomId, data)
    }

    // C. Gửi tin nhắn thoại (nếu có)
    if (voiceFile) {
      const form = new FormData()
      form.append('file', voiceFile)
      form.append('clientMessageId', crypto.randomUUID())
      form.append('voice', 'true')
      clearRecordedAudio()

      const { data } = await api.post(`/api/conversations/${roomId}/attachments`, form, {
        timeout: 60000
      })
      chat.receivedAttachment(roomId, data)
    }
  } catch (e) {
    chat.error = apiError(e)
  } finally {
    isSending.value = false
    await nextTick()
    scrollToBottom()
  }
}

function startVideoCall() {
  if (chat.selected && calls.ready && !calls.active) {
    calls.dial(chat.selected)
  }
}

function navigateToFriends() {
  isOpen.value = false
  router.push('/friends')
}

watch(() => chat.messages.length, async () => {
  await nextTick()
  scrollToBottom()
})

watch(() => auth.user?.id, (newId) => {
  if (newId) {
    startPolling()
  } else {
    stopPolling()
    closeAll()
  }
}, { immediate: true })

onMounted(() => {
  if (auth.user) startPolling()
})

onBeforeUnmount(() => {
  disposed = true
  stopPolling()
  clearPendingAttachments()
})
</script>

<template>
  <div class="floating-chat-container" :class="{ 'is-open': isOpen, 'is-minimized': isMinimized }">
    
    <!-- Hidden File Picker Input -->
    <input
      ref="filePicker"
      type="file"
      class="sr-only-picker"
      accept=".png,.jpg,.jpeg,.webp,.pdf,.txt,.docx"
      @change="onFileSelected"
    />

    <!-- Messenger Popup Window -->
    <section
      v-if="isOpen && !isMinimized"
      class="messenger-window"
      aria-label="Cửa sổ chat tin nhắn"
    >
      <!-- STATE 1: ĐANG CHAT VỚI 1 NGƯỜI (GIAO DIỆN CHAT 1-1 CHUẨN MESSENGER) -->
      <template v-if="activeFriend">
        <!-- Messenger Chat Header -->
        <header class="messenger-header active-thread-header">
          <div class="thread-user-info">
            <!-- Nút quay lại góc trên bên trái -->
            <button
              class="header-icon-btn back-btn"
              type="button"
              title="Quay lại danh sách"
              aria-label="Quay lại danh sách"
              @click="closeChatThread"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="20" height="20">
                <path d="M19 12H5M12 19l-7-7 7-7"/>
              </svg>
            </button>
            <div class="messenger-avatar">
              {{ (activeFriend.displayName || activeFriend.username || 'U')[0].toUpperCase() }}
            </div>
            <div class="peer-text">
              <span class="peer-name">{{ activeFriend.displayName }}</span>
              <small class="peer-sub">@{{ activeFriend.username }}</small>
            </div>
          </div>

          <div class="header-actions">
            <button
              class="header-icon-btn"
              type="button"
              title="Gọi video"
              :disabled="!calls.ready || calls.active || !chat.selected"
              aria-label="Gọi video"
              @click="startVideoCall"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <polygon points="23 7 16 12 23 17 23 7" />
                <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
              </svg>
            </button>
            <button
              class="header-icon-btn"
              type="button"
              title="Thu nhỏ"
              aria-label="Thu nhỏ cửa sổ"
              @click="minimize"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="18" height="18">
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </button>
            <button
              class="header-icon-btn close-btn"
              type="button"
              title="Đóng chat"
              aria-label="Đóng chat"
              @click="closeAll"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="18" height="18">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
        </header>

        <!-- Message Body -->
        <div ref="messagesContainer" class="messenger-body" aria-live="polite">
          <!-- Hiển thị khi đang tải tin nhắn -->
          <div v-if="loadingThread" class="loading-thread-indicator">
            <span class="spinner-dot"></span>
            <span>Đang mở cuộc trò chuyện…</span>
          </div>

          <div v-if="chat.hasOlder" class="older-loader">
            <button
              class="btn-load-older"
              type="button"
              :disabled="chat.loadingOlder"
              @click="chat.older"
            >
              {{ chat.loadingOlder ? 'Đang tải…' : 'Tải tin nhắn cũ hơn' }}
            </button>
          </div>

          <div v-if="!loadingThread && !chat.messages.length" class="empty-conversation">
            <div class="empty-avatar-lg">
              {{ (activeFriend.displayName || 'U')[0].toUpperCase() }}
            </div>
            <h4>{{ activeFriend.displayName }}</h4>
            <p>Các bạn đã được kết nối! Hãy gửi tin nhắn đầu tiên để bắt đầu trò chuyện.</p>
          </div>

          <!-- Message Bubbles -->
          <div
            v-for="msg in chat.messages"
            :key="msg.messageId"
            class="msg-row"
            :class="{ 'is-me': msg.senderId === auth.user?.id, 'is-partner': msg.senderId !== auth.user?.id }"
          >
            <!-- Avatar tiny on partner message -->
            <div v-if="msg.senderId !== auth.user?.id" class="partner-avatar-tiny">
              {{ (activeFriend.displayName || 'U')[0].toUpperCase() }}
            </div>

            <div class="msg-bubble-wrap">
              <div class="msg-bubble">
                <p v-if="msg.text" class="msg-text">{{ msg.text }}</p>
                <AttachmentView v-if="msg.attachment" :attachment="msg.attachment" />
              </div>
              <span class="msg-time">{{ formatMessageTime(msg.sentAt) }}</span>
            </div>
          </div>
        </div>

        <p v-if="chat.error" class="error-banner" role="alert">{{ chat.error }}</p>

        <!-- Messenger Unified Footer -->
        <footer class="messenger-footer">
          <!-- Attachment Preview Drawer (Đồng bộ không sinh thêm nút gửi thừa) -->
          <div v-if="pendingFile || recordedAudioFile || voice.recording || voice.requesting" class="pending-drawer">
            <!-- Đang ghi âm mic -->
            <div v-if="voice.recording || voice.requesting" class="recording-bar">
              <span class="rec-dot"></span>
              <span class="rec-text">{{ voice.requesting ? 'Đang mở micro…' : `Đang ghi âm: ${voice.seconds}s / 120s` }}</span>
              <button class="chip-action-btn" type="button" @click="voice.stop">Dừng</button>
              <button class="chip-cancel-btn" type="button" @click="clearRecordedAudio">✕</button>
            </div>

            <!-- Bản ghi âm đã thu xong -->
            <div v-else-if="recordedAudioFile" class="audio-chip">
              <span class="chip-icon">🎙️</span>
              <audio :src="voice.url" controls class="mini-audio-player"></audio>
              <button class="chip-cancel-btn" type="button" title="Hủy ghi âm" @click="clearRecordedAudio">✕</button>
            </div>

            <!-- Tệp đính kèm đã chọn (PDF, Image, Docx) -->
            <div v-else-if="pendingFile" class="file-chip">
              <img v-if="pendingFilePreview" :src="pendingFilePreview" class="chip-img-preview" alt="Preview" />
              <span v-else class="chip-icon">📄</span>
              <div class="chip-info">
                <strong class="chip-filename">{{ pendingFile.name }}</strong>
                <small>{{ (pendingFile.size / 1024).toFixed(1) }} KB</small>
              </div>
              <button class="chip-cancel-btn" type="button" title="Hủy tệp" @click="clearPendingFile">✕</button>
            </div>
          </div>

          <div class="footer-composer">
            <div class="footer-tools">
              <!-- Nút đính kèm tệp -->
              <button
                class="composer-tool-btn"
                type="button"
                title="Đính kèm tệp / ảnh"
                :disabled="isSending"
                aria-label="Đính kèm tệp"
                @click="triggerFilePicker"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="19" height="19">
                  <path d="m9 13 6-6a3 3 0 0 1 4 4l-8 8a5 5 0 0 1-7-7l8-8"/>
                  <path d="m7 15 8-8"/>
                </svg>
              </button>

              <!-- Nút ghi âm thoại -->
              <button
                class="composer-tool-btn"
                :class="{ 'is-recording': voice.recording }"
                type="button"
                :title="voice.recording ? 'Dừng ghi âm' : 'Ghi tin nhắn thoại'"
                :disabled="isSending"
                aria-label="Ghi âm"
                @click="toggleVoiceRecord"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="19" height="19">
                  <rect x="9" y="3" width="6" height="12" rx="3"/>
                  <path d="M6 11v1a6 6 0 0 0 12 0v-1M12 18v3M9 21h6"/>
                </svg>
              </button>
            </div>

            <input
              v-model="chat.draft"
              class="footer-input"
              type="text"
              maxlength="2000"
              placeholder="Aa (Nhập tin nhắn…)"
              autocomplete="off"
              :disabled="isSending"
              @keydown="handleKeydown"
            />

            <!-- NÚT GỬI DUY NHẤT ĐỒNG BỘ Ở GÓC PHẢI DƯỚI -->
            <button
              class="footer-send-btn"
              type="button"
              :disabled="isSending || (!chat.draft.trim() && !pendingFile && !recordedAudioFile)"
              title="Gửi tin nhắn (Enter)"
              aria-label="Gửi tin nhắn"
              @click="handleSend"
            >
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
              </svg>
            </button>
          </div>
        </footer>
      </template>

      <!-- STATE 2: DANH SÁCH BẠN BÈ (KHI CHƯA CHỌN AI) -->
      <template v-else>
        <header class="messenger-header contact-list-header">
          <div class="contact-header-title">
            <span class="chat-title-main">Đoạn chat</span>
            <span v-if="overview.friends.length" class="friends-count-pill">
              {{ overview.friends.length }} bạn bè
            </span>
          </div>
          <div class="header-actions">
            <button
              class="header-icon-btn"
              type="button"
              title="Mở rộng trang Bạn bè"
              aria-label="Đến trang Quản lý Bạn bè"
              @click="navigateToFriends"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
              </svg>
            </button>
            <button
              class="header-icon-btn close-btn"
              type="button"
              title="Đóng"
              aria-label="Đóng cửa sổ chat"
              @click="toggleWidget"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="18" height="18">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
        </header>

        <!-- Search Bar -->
        <div class="messenger-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            v-model="searchQuery"
            type="search"
            placeholder="Tìm kiếm bạn bè trên Messenger…"
            autocomplete="off"
          />
        </div>

        <!-- Contact list body -->
        <div class="messenger-contact-body">
          <!-- Pending Requests notification if any -->
          <div
            v-if="pendingCount > 0"
            class="pending-alert-item"
            @click="navigateToFriends"
          >
            <span class="pending-badge">{{ pendingCount }}</span>
            <div class="pending-text">
              <strong>Lời mời kết bạn mới</strong>
              <small>Nhấn để xem và chấp nhận</small>
            </div>
            <span class="chevron">›</span>
          </div>

          <!-- Section Label: Bạn bè (không ghi trực tuyến sai) -->
          <div class="section-label">
            <span>DANH SÁCH BẠN BÈ ({{ filteredFriends.length }})</span>
          </div>

          <!-- Danh sách bạn bè: Bỏ nút icon xanh thừa ở ngoài -->
          <div v-if="filteredFriends.length" class="contacts-scroll">
            <div
              v-for="friend in filteredFriends"
              :key="friend.id"
              class="contact-item"
              @click="selectFriend(friend)"
            >
              <div class="messenger-avatar">
                {{ (friend.displayName || friend.username || 'U')[0].toUpperCase() }}
              </div>
              <div class="contact-details">
                <span class="contact-name">{{ friend.displayName }}</span>
                <span class="contact-sub">@{{ friend.username }}</span>
              </div>
            </div>
          </div>

          <!-- Empty list message -->
          <div v-else class="empty-contacts">
            <div class="empty-icon-wrap">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="36" height="36">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </div>
            <p v-if="searchQuery">Không tìm thấy bạn bè nào phù hợp với "{{ searchQuery }}".</p>
            <p v-else>Bạn chưa có bạn bè nào trong danh sách.</p>
            <button class="primary find-friends-btn" type="button" @click="navigateToFriends">
              Tìm kiếm và kết bạn ngay
            </button>
          </div>
        </div>
      </template>
    </section>

    <!-- Minimized pill bar when minimized -->
    <div
      v-if="isOpen && isMinimized"
      class="minimized-pill"
      @click="minimize"
    >
      <div class="messenger-avatar sm">
        {{ activeFriend ? (activeFriend.displayName || 'U')[0].toUpperCase() : '💬' }}
      </div>
      <span class="min-name">{{ activeFriend ? activeFriend.displayName : 'Đoạn chat' }}</span>
      <button class="min-close-btn" type="button" title="Mở lại" @click.stop="minimize">▲</button>
      <button class="min-close-btn" type="button" title="Đóng" @click.stop="closeAll">✕</button>
    </div>

    <!-- MAIN FLOATING ACTION BUTTON (Nút Chat nổi ở góc phải) -->
    <button
      v-if="!isOpen || isMinimized"
      class="floating-chat-fab"
      type="button"
      :class="{ 'has-badge': pendingCount > 0 }"
      title="Mở Messenger Chat"
      aria-label="Mở Messenger Chat"
      @click="toggleWidget"
    >
      <div class="fab-icon-wrap">
        <svg viewBox="0 0 24 24" fill="currentColor" width="26" height="26">
          <path d="M12 2C6.477 2 2 6.145 2 11.258c0 2.91 1.454 5.518 3.734 7.185v3.557l3.418-1.875c.91.252 1.868.39 2.848.39 5.523 0 10-4.145 10-9.257C22 6.145 17.523 2 12 2zm1.02 12.443l-2.584-2.756-5.045 2.756 5.548-5.89 2.65 2.756 4.978-2.756-5.547 5.89z"/>
        </svg>
      </div>
      <span class="fab-text">Chat</span>
      <span v-if="pendingCount > 0" class="fab-badge" :title="`${pendingCount} lời mời kết bạn`">
        {{ pendingCount }}
      </span>
    </button>
  </div>
</template>

<style scoped>
.sr-only-picker {
  display: none !important;
}

.floating-chat-container {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 1050;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  pointer-events: none;
}

.floating-chat-container * {
  pointer-events: auto;
  box-sizing: border-box;
}

/* ==========================================================================
   FLOATING ACTION BUTTON (FAB)
   ========================================================================== */
.floating-chat-fab {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  height: 52px;
  padding: 0 20px 0 16px !important;
  border-radius: 999px;
  background: linear-gradient(135deg, #10b981 0%, #0d9488 100%) !important;
  color: #ffffff !important;
  border: none !important;
  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.4), 0 2px 6px rgba(0, 0, 0, 0.2);
  cursor: pointer;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.3px;
  position: relative;
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.floating-chat-fab:hover {
  transform: translateY(-3px) scale(1.03);
  box-shadow: 0 12px 30px rgba(16, 185, 129, 0.55), 0 4px 10px rgba(0, 0, 0, 0.3);
  background: linear-gradient(135deg, #059669 0%, #0f766e 100%) !important;
}

.floating-chat-fab:active {
  transform: translateY(1px) scale(0.98);
}

.fab-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
}

.fab-text {
  user-select: none;
}

.fab-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  min-width: 20px;
  height: 20px;
  line-height: 20px;
  border-radius: 999px;
  text-align: center;
  padding: 0 5px;
  border: 2px solid var(--bg-panel, #0b111e);
  box-shadow: 0 2px 5px rgba(0,0,0,0.3);
}

/* ==========================================================================
   MINIMIZED PILL
   ========================================================================== */
.minimized-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: var(--bg-panel, #131d2e);
  border: 1px solid var(--border-color, #26384f);
  border-radius: 999px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  cursor: pointer;
  color: var(--text-main, #f1f5f9);
  transition: all 0.2s ease;
}

.minimized-pill:hover {
  border-color: var(--accent, #10b981);
  transform: translateY(-2px);
}

.min-name {
  font-size: 13.5px;
  font-weight: 600;
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.min-close-btn {
  background: transparent !important;
  border: none !important;
  padding: 2px 6px !important;
  color: var(--text-muted, #94a3b8);
  font-size: 12px;
  border-radius: 4px;
  cursor: pointer;
}

.min-close-btn:hover {
  color: var(--text-main, #f1f5f9);
  background: rgba(255, 255, 255, 0.1) !important;
}

/* ==========================================================================
   MESSENGER POPUP WINDOW (360px)
   ========================================================================== */
.messenger-window {
  width: 360px;
  max-width: calc(100vw - 32px);
  height: 520px;
  max-height: calc(100vh - 100px);
  background: var(--bg-panel, #131d2e);
  border: 1px solid var(--border-color, #26384f);
  border-radius: 16px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.4), 0 2px 10px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: messengerSlideUp 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes messengerSlideUp {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* Header */
.messenger-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--bg-card, #1a273c);
  border-bottom: 1px solid var(--border-color, #26384f);
  user-select: none;
  min-height: 54px;
}

.contact-header-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-title-main {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-main, #f1f5f9);
}

.friends-count-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
}

.thread-user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.peer-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.peer-name {
  font-size: 14.5px;
  font-weight: 700;
  color: var(--text-main, #f1f5f9);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}

.peer-sub {
  font-size: 11px;
  color: var(--text-muted, #94a3b8);
  font-weight: 500;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

/* Các nút icon ở header: reset padding từ global button */
.header-icon-btn {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  width: 32px !important;
  height: 32px !important;
  min-width: 32px !important;
  min-height: 32px !important;
  padding: 0 !important;
  border-radius: 50% !important;
  background: transparent !important;
  border: none !important;
  color: var(--text-secondary, #94a3b8) !important;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.header-icon-btn svg {
  display: block;
  flex-shrink: 0;
}

.header-icon-btn:hover {
  background: rgba(255, 255, 255, 0.12) !important;
  color: var(--text-main, #f1f5f9) !important;
}

.header-icon-btn.close-btn:hover {
  color: #ef4444 !important;
  background: rgba(239, 68, 68, 0.15) !important;
}

/* Nút quay lại: nổi bật hơn để không bao giờ bị chìm */
.header-icon-btn.back-btn {
  background: rgba(255, 255, 255, 0.08) !important;
  color: var(--text-main, #f1f5f9) !important;
  margin-right: 2px;
}

.header-icon-btn.back-btn:hover {
  background: rgba(255, 255, 255, 0.18) !important;
}

/* Avatars */
.messenger-avatar {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.messenger-avatar.sm {
  width: 28px;
  height: 28px;
  min-width: 28px;
  font-size: 12px;
}

/* Search bar */
.messenger-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin: 10px 14px 4px 14px;
  background: var(--bg-input, #0e1726);
  border: 1px solid var(--border-color, #26384f);
  border-radius: 20px;
  color: var(--text-muted, #64748b);
}

.messenger-search input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-main, #f1f5f9);
  font-size: 13px;
}

/* Contact List Body */
.messenger-contact-body {
  flex: 1;
  overflow-y: auto;
  padding: 6px 10px 12px 10px;
}

.pending-alert-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin: 6px 4px 10px 4px;
  background: rgba(245, 158, 11, 0.12);
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.pending-alert-item:hover {
  background: rgba(245, 158, 11, 0.2);
}

.pending-badge {
  background: #f59e0b;
  color: #000;
  font-size: 11px;
  font-weight: 800;
  padding: 2px 7px;
  border-radius: 999px;
}

.pending-text {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.pending-text strong {
  font-size: 12.5px;
  color: var(--text-main, #f1f5f9);
}

.pending-text small {
  font-size: 11px;
  color: var(--text-muted, #94a3b8);
}

.chevron {
  font-size: 16px;
  color: var(--text-muted, #94a3b8);
}

.section-label {
  padding: 10px 8px 6px 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--text-muted, #64748b);
}

.contacts-scroll {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.contact-item:hover {
  background: var(--bg-card, #1a273c);
}

.contact-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.contact-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main, #f1f5f9);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.contact-sub {
  font-size: 12px;
  color: var(--text-muted, #94a3b8);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-contacts {
  padding: 30px 16px;
  text-align: center;
  color: var(--text-muted, #94a3b8);
}

.empty-icon-wrap {
  margin-bottom: 12px;
  color: var(--text-muted, #64748b);
}

.empty-contacts p {
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 14px;
}

.find-friends-btn {
  padding: 8px 16px !important;
  font-size: 12.5px;
  border-radius: 8px;
}

/* ==========================================================================
   MESSENGER CONVERSATION BODY (TIN NHẮN CHAT)
   ========================================================================== */
.messenger-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--bg-panel, #131d2e);
}

.loading-thread-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 0;
  font-size: 12.5px;
  color: var(--text-muted, #94a3b8);
}

.spinner-dot {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(16, 185, 129, 0.3);
  border-top-color: #10b981;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.older-loader {
  display: flex;
  justify-content: center;
  padding-bottom: 6px;
}

.btn-load-older {
  font-size: 11.5px;
  padding: 4px 12px !important;
  background: var(--bg-card, #1a273c) !important;
  border: 1px solid var(--border-color, #26384f) !important;
  color: var(--text-secondary, #94a3b8) !important;
  border-radius: 999px;
  cursor: pointer;
}

.empty-conversation {
  margin: auto;
  text-align: center;
  padding: 24px 16px;
  color: var(--text-muted, #94a3b8);
}

.empty-avatar-lg {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 12px auto;
}

.empty-conversation h4 {
  font-size: 15px;
  color: var(--text-main, #f1f5f9);
  margin-bottom: 6px;
}

.empty-conversation p {
  font-size: 12.5px;
  line-height: 1.4;
}

/* Row messages */
.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  width: 100%;
}

.msg-row.is-me {
  justify-content: flex-end;
}

.msg-row.is-partner {
  justify-content: flex-start;
}

.partner-avatar-tiny {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-bottom: 16px;
}

.msg-bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: 76%;
}

.is-me .msg-bubble-wrap {
  align-items: flex-end;
}

.is-partner .msg-bubble-wrap {
  align-items: flex-start;
}

.msg-bubble {
  padding: 9px 13px;
  border-radius: 18px;
  word-break: break-word;
  font-size: 13.5px;
  line-height: 1.4;
}

.is-me .msg-bubble {
  background: linear-gradient(135deg, #059669 0%, #0d9488 100%);
  color: #ffffff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.is-partner .msg-bubble {
  background: var(--bg-card, #1e2c40);
  color: var(--text-main, #f1f5f9);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}

.msg-text {
  margin: 0;
  white-space: pre-wrap;
}

.msg-time {
  font-size: 10.5px;
  color: var(--text-muted, #64748b);
  margin-top: 3px;
  padding: 0 4px;
}

.error-banner {
  padding: 6px 12px;
  background: rgba(239, 68, 68, 0.15);
  border-top: 1px solid rgba(239, 68, 68, 0.3);
  color: #ef4444;
  font-size: 12px;
  margin: 0;
}

/* ==========================================================================
   UNIFIED MESSENGER FOOTER (Đồng bộ mọi tệp gửi qua 1 nút duy nhất)
   ========================================================================== */
.messenger-footer {
  padding: 8px 10px;
  background: var(--bg-card, #1a273c);
  border-top: 1px solid var(--border-color, #26384f);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* Drawer xem trước tệp/ghi âm (nằm gọn trên thanh nhập) */
.pending-drawer {
  background: var(--bg-input, #0e1726);
  border: 1px solid var(--border-color, #26384f);
  border-radius: 12px;
  padding: 6px 10px;
}

.file-chip,
.audio-chip,
.recording-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chip-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.chip-img-preview {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
}

.chip-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chip-filename {
  font-size: 12px;
  color: var(--text-main, #f1f5f9);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chip-info small {
  font-size: 10.5px;
  color: var(--text-muted, #94a3b8);
}

.mini-audio-player {
  height: 28px;
  flex: 1;
}

.rec-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  animation: pulseRec 1s infinite alternate;
  flex-shrink: 0;
}

@keyframes pulseRec {
  from { opacity: 0.4; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1.1); }
}

.rec-text {
  flex: 1;
  font-size: 12px;
  color: #ef4444;
  font-weight: 600;
}

.chip-action-btn {
  padding: 4px 8px !important;
  font-size: 11.5px;
  background: var(--accent, #10b981) !important;
  color: #fff !important;
  border: none !important;
  border-radius: 6px;
  cursor: pointer;
}

.chip-cancel-btn {
  width: 22px !important;
  height: 22px !important;
  min-width: 22px !important;
  min-height: 22px !important;
  padding: 0 !important;
  border-radius: 50% !important;
  background: rgba(255, 255, 255, 0.12) !important;
  border: none !important;
  color: var(--text-muted, #94a3b8) !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
}

.chip-cancel-btn:hover {
  background: rgba(239, 68, 68, 0.25) !important;
  color: #ef4444 !important;
}

.footer-composer {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}

.footer-tools {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.composer-tool-btn {
  width: 32px !important;
  height: 32px !important;
  min-width: 32px !important;
  min-height: 32px !important;
  padding: 0 !important;
  border-radius: 50% !important;
  background: transparent !important;
  border: none !important;
  color: var(--text-secondary, #94a3b8) !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.composer-tool-btn:hover {
  background: rgba(255, 255, 255, 0.1) !important;
  color: var(--text-main, #f1f5f9) !important;
}

.composer-tool-btn.is-recording {
  color: #ef4444 !important;
  background: rgba(239, 68, 68, 0.18) !important;
  animation: pulse 1s infinite alternate;
}

.footer-input {
  flex: 1;
  min-width: 0;
  height: 38px;
  padding: 0 14px;
  background: var(--bg-input, #0e1726);
  border: 1px solid var(--border-color, #26384f);
  border-radius: 999px;
  color: var(--text-main, #f1f5f9);
  font-size: 13.5px;
  outline: none;
  transition: border-color 0.15s ease;
}

.footer-input:focus {
  border-color: var(--accent, #10b981);
}

/* Nút gửi duy nhất ở góc phải dưới */
.footer-send-btn {
  width: 36px !important;
  height: 36px !important;
  min-width: 36px !important;
  min-height: 36px !important;
  padding: 0 !important;
  border-radius: 50% !important;
  background: linear-gradient(135deg, #10b981 0%, #0d9488 100%) !important;
  color: #ffffff !important;
  border: none !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  cursor: pointer;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3);
  transition: all 0.15s ease;
}

.footer-send-btn svg {
  display: block;
  fill: currentColor;
  margin-left: 2px;
}

.footer-send-btn:hover:not(:disabled) {
  transform: scale(1.08);
  background: linear-gradient(135deg, #059669 0%, #0f766e 100%) !important;
}

.footer-send-btn:disabled {
  opacity: 0.35 !important;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* ==========================================================================
   MOBILE RESPONSIVENESS
   ========================================================================== */
@media (max-width: 480px) {
  .floating-chat-container {
    right: 14px;
    bottom: 14px;
  }

  .messenger-window {
    width: calc(100vw - 28px);
    height: 480px;
    max-height: calc(100vh - 80px);
  }

  .floating-chat-fab {
    height: 48px;
    padding: 0 16px 0 14px !important;
    font-size: 14px;
  }
}
</style>
