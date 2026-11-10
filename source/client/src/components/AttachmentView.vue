<script setup>
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import { api, apiError } from '../services/api.js'

const props = defineProps({
  attachment: {
    type: Object,
    required: true
  }
})

const url = ref('')
const error = ref('')
const busy = ref(false)
const isPlaying = ref(false)
const currentTime = ref(0)
const audioDuration = ref(props.attachment?.durationSeconds || 0)
const audioEl = ref(null)

let generation = 0

// Waveform bar heights mimicking Messenger voice design
const waveformBars = [8, 14, 20, 10, 16, 24, 18, 12, 22, 14, 18, 10, 16, 22, 12, 8]

const progressPercent = computed(() => {
  const dur = audioDuration.value || 1
  return Math.min(100, Math.max(0, (currentTime.value / dur) * 100))
})

const displayTime = computed(() => {
  const target = isPlaying.value ? currentTime.value : (audioDuration.value || currentTime.value)
  return formatDuration(target)
})

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDuration(sec) {
  if (!sec && sec !== 0) return '0:00'
  const total = Math.floor(sec)
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${m}:${s < 10 ? '0' : ''}${s}`
}

async function fetchUrl() {
  const token = ++generation
  busy.value = true
  error.value = ''
  try {
    const { data } = await api.get(`/api/attachments/${props.attachment.id}/download-url`)
    if (token === generation) {
      url.value = data.url
      return data.url
    }
  } catch (e) {
    if (token === generation) error.value = apiError(e)
  } finally {
    if (token === generation) busy.value = false
  }
  return null
}

async function downloadFile() {
  let targetUrl = url.value
  if (!targetUrl) {
    targetUrl = await fetchUrl()
    if (!targetUrl) return
  }

  try {
    const link = document.createElement('a')
    link.href = targetUrl
    link.download = props.attachment.name || 'file'
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } catch (e) {
    window.open(targetUrl, '_blank')
  }
}

function openImage() {
  if (url.value) {
    window.open(url.value, '_blank', 'noopener,noreferrer')
  } else {
    downloadFile()
  }
}

async function toggleAudio() {
  if (!audioEl.value) return
  if (!url.value) {
    await fetchUrl()
  }
  if (audioEl.value.paused) {
    try {
      await audioEl.value.play()
    } catch (e) {
      // autoplay restriction handled
    }
  } else {
    audioEl.value.pause()
  }
}

function onTimeUpdate() {
  if (audioEl.value) {
    currentTime.value = audioEl.value.currentTime
  }
}

function onLoadedMetadata() {
  if (audioEl.value && audioEl.value.duration && isFinite(audioEl.value.duration)) {
    audioDuration.value = audioEl.value.duration
  }
}

function onEnded() {
  isPlaying.value = false
  currentTime.value = 0
}

watch(() => props.attachment.id, fetchUrl)
onMounted(fetchUrl)
onBeforeUnmount(() => {
  generation++
  if (audioEl.value) {
    audioEl.value.pause()
  }
})
</script>

<template>
  <div class="attachment-clean-view">
    <!-- 1. HÌNH ẢNH: Chỉ hiển thị hình ảnh gọn gàng, click để mở to -->
    <div
      v-if="attachment.mimeType && attachment.mimeType.startsWith('image/')"
      class="clean-image-container"
      title="Nhấn để xem ảnh"
      @click="openImage"
    >
      <img
        v-if="url"
        :src="url"
        :alt="attachment.name"
        class="clean-bubble-img"
        loading="lazy"
        referrerpolicy="no-referrer"
      />
      <div v-else class="img-shimmer-box">
        <span class="loading-spin"></span>
      </div>
    </div>

    <!-- 2. GHI ÂM THOẠI: Thanh hiển thị Messenger Voice sóng âm kèm thời lượng -->
    <div
      v-else-if="attachment.mimeType && attachment.mimeType.startsWith('audio/')"
      class="clean-voice-pill"
      :class="{ playing: isPlaying }"
      title="Nhấn để nghe tin nhắn thoại"
      @click="toggleAudio"
    >
      <audio
        ref="audioEl"
        :src="url"
        preload="metadata"
        @timeupdate="onTimeUpdate"
        @loadedmetadata="onLoadedMetadata"
        @ended="onEnded"
        @pause="isPlaying = false"
        @play="isPlaying = true"
      ></audio>

      <button
        type="button"
        class="voice-play-circle"
        :aria-label="isPlaying ? 'Tạm dừng' : 'Phát ghi âm'"
        @click.stop="toggleAudio"
      >
        <svg v-if="!isPlaying" viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
          <path d="M8 5v14l11-7z" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
          <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
        </svg>
      </button>

      <div class="voice-bars-container">
        <span
          v-for="(h, idx) in waveformBars"
          :key="idx"
          class="waveform-bar"
          :class="{ filled: progressPercent >= (idx / waveformBars.length) * 100 }"
          :style="{ height: `${h}px` }"
        ></span>
      </div>

      <span class="voice-duration-label">{{ displayTime }}</span>
    </div>

    <!-- 3. TỆP ĐÍNH KÈM / PDF: Card Messenger đẹp mắt, click vào là tải về ngay -->
    <div
      v-else
      class="clean-doc-card"
      role="button"
      tabindex="0"
      :title="`Nhấn để tải về: ${attachment.name}`"
      @click="downloadFile"
      @keydown.enter="downloadFile"
    >
      <div class="doc-icon-badge">
        <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
          <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z" />
        </svg>
      </div>

      <div class="doc-meta-info">
        <span class="doc-filename" :title="attachment.name">{{ attachment.name }}</span>
        <span class="doc-filesize">{{ formatSize(attachment.size) }}</span>
      </div>

      <div class="doc-download-action">
        <span v-if="busy" class="doc-spinner"></span>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="16" height="16">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="7 10 12 15 17 10" />
          <line x1="12" y1="15" x2="12" y2="3" />
        </svg>
      </div>
    </div>

    <small v-if="error" class="attachment-error-hint" role="alert">{{ error }}</small>
  </div>
</template>

<style scoped>
.attachment-clean-view {
  display: block;
  max-width: 100%;
  margin-top: 4px;
}

/* 1. Image View */
.clean-image-container {
  display: inline-block;
  cursor: pointer;
  border-radius: 14px;
  overflow: hidden;
  max-width: 280px;
  transition: transform 0.2s ease, opacity 0.2s ease;
  line-height: 0;
}
.clean-image-container:hover {
  opacity: 0.95;
  transform: scale(1.01);
}
.clean-bubble-img {
  max-width: 100%;
  max-height: 260px;
  object-fit: cover;
  border-radius: 14px;
  display: block;
}
.img-shimmer-box {
  width: 200px;
  height: 140px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 2. Voice View (Messenger Blue Pill) */
.clean-voice-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: #0084ff;
  color: #ffffff;
  padding: 6px 14px 6px 8px;
  border-radius: 24px;
  cursor: pointer;
  user-select: none;
  max-width: 260px;
  box-shadow: 0 2px 8px rgba(0, 132, 255, 0.3);
  transition: all 0.2s ease;
}
.clean-voice-pill:hover {
  background: #0073e6;
  transform: translateY(-1px);
}
.voice-play-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #ffffff;
  color: #0084ff;
  border: none;
  padding: 0 !important;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
  transition: transform 0.15s ease;
}
.voice-play-circle:hover {
  transform: scale(1.08);
}
.voice-bars-container {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 24px;
  flex: 1;
}
.waveform-bar {
  width: 3px;
  background: rgba(255, 255, 255, 0.45);
  border-radius: 3px;
  transition: background 0.15s ease;
}
.waveform-bar.filled {
  background: #ffffff;
}
.voice-duration-label {
  font-size: 12px;
  font-weight: 600;
  color: #ffffff;
  white-space: nowrap;
}

/* 3. Doc / PDF Card View */
.clean-doc-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #2f3032;
  color: #ffffff;
  padding: 10px 14px;
  border-radius: 14px;
  cursor: pointer;
  user-select: none;
  max-width: 300px;
  transition: all 0.2s ease;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
}
:root[data-theme='light'] .clean-doc-card {
  background: #f0f2f5;
  color: #050505;
  border-color: rgba(0, 0, 0, 0.1);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.clean-doc-card:hover {
  background: #3a3b3d;
  transform: translateY(-1px);
}
:root[data-theme='light'] .clean-doc-card:hover {
  background: #e4e6eb;
}
.doc-icon-badge {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.15);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
:root[data-theme='light'] .doc-icon-badge {
  background: rgba(0, 0, 0, 0.08);
  color: #0084ff;
}
.doc-meta-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.doc-filename {
  font-size: 13.5px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}
.doc-filesize {
  font-size: 11.5px;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 2px;
}
:root[data-theme='light'] .doc-filesize {
  color: #65676b;
}
.doc-download-action {
  color: rgba(255, 255, 255, 0.75);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
:root[data-theme='light'] .doc-download-action {
  color: #65676b;
}
.clean-doc-card:hover .doc-download-action {
  color: #0084ff;
}
.doc-spinner, .loading-spin {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
}
:root[data-theme='light'] .doc-spinner {
  border-color: rgba(0, 0, 0, 0.2);
  border-top-color: #0084ff;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.attachment-error-hint {
  display: block;
  font-size: 11px;
  color: #ff4d4f;
  margin-top: 4px;
}
</style>