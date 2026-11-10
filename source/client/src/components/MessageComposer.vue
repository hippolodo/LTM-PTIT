<script setup>
import { ref, reactive, watch, useId, onBeforeUnmount } from 'vue'
import { api, apiError } from '../services/api.js'
import { useVoiceRecorder } from '../composables/useVoiceRecorder.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  roomId: String,
  disabled: Boolean,
  sending: Boolean,
})

const emit = defineEmits(['update:modelValue', 'send', 'attachment'])
const inputId = `message-${useId()}`

const picker = ref(null)
const pendingFile = ref(null)
const pendingPreview = ref('')
const isUploading = ref(false)
const uploadProgress = ref(0)
const uploadError = ref('')

const voice = reactive(useVoiceRecorder())
const recordedAudioFile = ref(null)

let uploadController = null
let uploadGeneration = 0

watch(() => voice.blob, (blob) => {
  if (blob) {
    recordedAudioFile.value = voice.file()
  }
})

function clearPendingFile() {
  if (pendingPreview.value) {
    URL.revokeObjectURL(pendingPreview.value)
    pendingPreview.value = ''
  }
  pendingFile.value = null
  uploadError.value = ''
  uploadProgress.value = 0
}

function clearRecordedAudio() {
  voice.cancel()
  recordedAudioFile.value = null
  uploadError.value = ''
}

function clearAllAttachments() {
  uploadGeneration++
  uploadController?.abort()
  uploadController = null
  isUploading.value = false
  clearPendingFile()
  clearRecordedAudio()
}

function triggerFilePicker() {
  if (props.disabled || isUploading.value) return
  picker.value?.click()
}

function onFileSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  if (file.size > 10485760) {
    uploadError.value = 'File tối đa 10 MB.'
    return
  }

  // Clear recorded audio if selecting a file
  clearRecordedAudio()
  uploadError.value = ''

  pendingFile.value = file
  if (['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
    pendingPreview.value = URL.createObjectURL(file)
  } else {
    pendingPreview.value = ''
  }
}

function toggleVoiceRecord() {
  if (props.disabled || isUploading.value) return
  if (voice.recording) {
    voice.stop()
  } else {
    clearPendingFile()
    clearRecordedAudio()
    uploadError.value = ''
    voice.start()
  }
}

async function uploadAttachment(file, isVoice = false) {
  if (!file || !props.roomId) return null
  const token = ++uploadGeneration
  const room = props.roomId
  isUploading.value = true
  uploadError.value = ''
  uploadController = new AbortController()

  const form = new FormData()
  form.append('file', file)
  form.append('clientMessageId', crypto.randomUUID())
  form.append('voice', String(isVoice))

  try {
    const { data } = await api.post(`/api/conversations/${room}/attachments`, form, {
      signal: uploadController.signal,
      timeout: 60000,
      onUploadProgress: (e) => {
        if (token === uploadGeneration && e.total) {
          uploadProgress.value = Math.round((e.loaded / e.total) * 100)
        }
      }
    })
    if (token === uploadGeneration) {
      emit('attachment', data)
      return data
    }
  } catch (e) {
    if (token === uploadGeneration) {
      uploadError.value = apiError(e)
    }
    throw e
  } finally {
    if (token === uploadGeneration) {
      isUploading.value = false
    }
  }
  return null
}

async function send() {
  if (props.disabled || props.sending || isUploading.value) return

  const text = (props.modelValue || '').trim()
  const fileToUpload = pendingFile.value
  let audioToUpload = recordedAudioFile.value

  // If currently recording voice, stop and get file
  if (voice.recording) {
    voice.stop()
    audioToUpload = voice.file()
  }

  // Check if there is anything to send
  if (!text && !fileToUpload && !audioToUpload) return

  // 1. Send file attachment if present
  if (fileToUpload) {
    try {
      await uploadAttachment(fileToUpload, false)
      clearPendingFile()
    } catch {
      return // Stop if attachment upload fails
    }
  }

  // 2. Send voice recording if present
  if (audioToUpload) {
    try {
      await uploadAttachment(audioToUpload, true)
      clearRecordedAudio()
    } catch {
      return // Stop if voice upload fails
    }
  }

  // 3. Send text message if present
  if (text && text.length <= 2000) {
    emit('send', text)
    emit('update:modelValue', '')
  }
}

watch(() => props.roomId, clearAllAttachments)
onBeforeUnmount(clearAllAttachments)
</script>

<template>
  <div class="unified-composer-container">
    <!-- Preview Drawer (Messenger Style) - Không bao giờ sinh thêm nút gửi thừa -->
    <div
      v-if="pendingFile || recordedAudioFile || voice.recording || voice.requesting || uploadError"
      class="composer-preview-drawer"
    >
      <!-- A. Đang thu âm giọng nói -->
      <div v-if="voice.recording || voice.requesting" class="preview-recording-bar">
        <span class="pulse-rec-dot"></span>
        <span class="rec-status-text">
          {{ voice.requesting ? 'Đang mở micro…' : `Đang ghi âm: ${voice.seconds}s / 120s` }}
        </span>
        <button
          type="button"
          class="preview-stop-btn"
          title="Dừng ghi âm và nghe lại"
          @click="voice.stop"
        >
          ■ Dừng
        </button>
        <button
          type="button"
          class="preview-cancel-btn"
          title="Hủy ghi âm"
          @click="clearRecordedAudio"
        >
          ✕
        </button>
      </div>

      <!-- B. Bản ghi âm đã hoàn thành, sẵn sàng gửi -->
      <div v-else-if="recordedAudioFile" class="preview-audio-chip">
        <span class="chip-media-icon">🎙️</span>
        <audio :src="voice.url" controls class="chip-mini-audio"></audio>
        <button
          type="button"
          class="preview-cancel-btn"
          title="Xóa bản ghi âm"
          @click="clearRecordedAudio"
        >
          ✕
        </button>
      </div>

      <!-- C. Tệp tin đính kèm (Ảnh / PDF / Word / Tệp khác) -->
      <div v-else-if="pendingFile" class="preview-file-chip">
        <img
          v-if="pendingPreview"
          :src="pendingPreview"
          class="chip-thumb-img"
          alt="Xem trước ảnh"
        />
        <div v-else class="chip-doc-icon">
          <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
            <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z" />
          </svg>
        </div>

        <div class="chip-file-meta">
          <strong class="chip-file-title" :title="pendingFile.name">{{ pendingFile.name }}</strong>
          <span class="chip-file-sub">{{ (pendingFile.size / 1024).toFixed(1) }} KB</span>
        </div>

        <button
          type="button"
          class="preview-cancel-btn"
          title="Hủy tệp này"
          @click="clearPendingFile"
        >
          ✕
        </button>
      </div>

      <!-- Thông báo lỗi tải file nếu có -->
      <p v-if="uploadError" class="drawer-error-alert" role="alert">{{ uploadError }}</p>
    </div>

    <!-- Thanh soạn tin nhắn chính với DUY NHẤT 1 NÚT GỬI MÀU XANH -->
    <form class="message-composer unified-composer" aria-label="Soạn tin nhắn" @submit.prevent="send">
      <div class="composer-tools">
        <!-- Nút đính kèm tệp ẩn sau input file -->
        <input
          ref="picker"
          type="file"
          class="sr-only"
          accept=".png,.jpg,.jpeg,.webp,.pdf,.txt,.docx"
          :disabled="disabled || isUploading"
          @change="onFileSelected"
        />
        <button
          type="button"
          class="composer-tool attach-tool"
          :disabled="disabled || isUploading"
          title="Đính kèm tệp / ảnh"
          aria-label="Đính kèm file"
          @click="triggerFilePicker"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m9 13 6-6a3 3 0 0 1 4 4l-8 8a5 5 0 0 1-7-7l8-8" />
            <path d="m7 15 8-8" />
          </svg>
        </button>

        <!-- Nút ghi âm thoại -->
        <button
          type="button"
          class="composer-tool voice-tool"
          :class="{ recording: voice.recording }"
          :disabled="disabled || voice.requesting || isUploading"
          :title="voice.recording ? 'Dừng ghi âm' : 'Ghi tin nhắn thoại'"
          :aria-label="voice.recording ? 'Dừng ghi âm' : 'Ghi tin nhắn thoại'"
          :aria-pressed="voice.recording"
          @click="toggleVoiceRecord"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <rect x="9" y="3" width="6" height="12" rx="3" />
            <path d="M6 11v1a6 6 0 0 0 12 0v-1M12 18v3M9 21h6" />
          </svg>
        </button>
      </div>

      <label class="sr-only" :for="inputId">Tin nhắn</label>
      <input
        :id="inputId"
        class="message-input"
        :value="modelValue"
        :disabled="disabled || sending || isUploading"
        maxlength="2000"
        placeholder="Nhập tin nhắn…"
        autocomplete="off"
        @input="emit('update:modelValue', $event.target.value)"
      />

      <!-- DUY NHẤT 1 NÚT GỬI MÀU XANH ĐỒNG BỘ -->
      <button
        class="message-send primary send-btn"
        :disabled="disabled || sending || isUploading || (!modelValue?.trim() && !pendingFile && !recordedAudioFile && !voice.recording)"
        type="submit"
        :title="isUploading ? `Đang tải ${uploadProgress}%…` : (sending ? 'Đang gửi…' : 'Gửi tin nhắn')"
        aria-label="Gửi tin nhắn"
      >
        <span v-if="isUploading" class="send-spinner"></span>
        <svg v-else class="send-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
        </svg>
        <span class="sr-only">{{ sending || isUploading ? 'Đang gửi…' : 'Gửi ↗' }}</span>
      </button>
    </form>
  </div>
</template>

<style scoped>
.unified-composer-container {
  display: flex;
  flex-direction: column;
  width: 100%;
}

/* Messenger Style Preview Drawer */
.composer-preview-drawer {
  background: var(--bg-card);
  border-top: 1px solid var(--border-color);
  padding: 8px 14px 4px 14px;
}

.preview-recording-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 8px 12px;
  border-radius: 12px;
  color: #ef4444;
}

.pulse-rec-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  animation: pulse 1s infinite alternate;
}

.rec-status-text {
  font-size: 13px;
  font-weight: 600;
  flex: 1;
}

.preview-stop-btn {
  padding: 4px 10px !important;
  font-size: 11.5px;
  border-radius: 8px;
  background: #ef4444;
  color: #ffffff;
  border: none;
  font-weight: 600;
  cursor: pointer;
}

.preview-audio-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  padding: 6px 12px;
  border-radius: 12px;
}

.chip-mini-audio {
  height: 32px;
  flex: 1;
  max-width: 260px;
}

.preview-file-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  padding: 6px 12px;
  border-radius: 12px;
  max-width: fit-content;
}

.chip-thumb-img {
  width: 38px;
  height: 38px;
  object-fit: cover;
  border-radius: 8px;
}

.chip-doc-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: rgba(0, 132, 255, 0.15);
  color: #0084ff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chip-file-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: 200px;
}

.chip-file-title {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-main);
}

.chip-file-sub {
  font-size: 11px;
  color: var(--text-secondary);
}

.preview-cancel-btn {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: none;
  background: rgba(120, 120, 120, 0.2);
  color: var(--text-secondary);
  font-size: 11px;
  padding: 0 !important;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s ease;
}
.preview-cancel-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.drawer-error-alert {
  color: #ef4444;
  font-size: 12px;
  margin: 4px 0 0 0;
}

.send-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
@keyframes pulse {
  from { opacity: 0.4; }
  to { opacity: 1; }
}
</style>