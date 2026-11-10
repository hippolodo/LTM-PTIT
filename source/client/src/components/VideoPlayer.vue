<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useVoiceActivity } from '../composables/useVoiceActivity.js'

const props = defineProps({
  stream: Object,
  muted: Boolean,
  label: String,
  placeholder: String,
  mirrored: Boolean,
  active: { type: Boolean, default: undefined },
})

const video = ref(null)
const blocked = ref(false)
const hasVideo = ref(false)

const { isSpeaking } = useVoiceActivity(() => props.stream)

let trackListeners = []

function cleanupListeners() {
  trackListeners.forEach(({ track, event, handler }) => {
    try { track.removeEventListener(event, handler) } catch {}
  })
  trackListeners = []
}

function computeHasVideo() {
  // Nếu có prop active được điều khiển từ bên ngoài (store / data channel signaling)
  if (props.active !== undefined) {
    return props.active
  }

  if (!props.stream) return false
  const tracks = props.stream.getVideoTracks ? props.stream.getVideoTracks() : []
  const live = tracks.find(t => t.readyState === 'live')
  if (!live) return false

  // Đối với camera của chính mình (local stream): kiểm tra trạng thái enabled
  if (props.muted) {
    return live.enabled
  }

  // Đối với camera đối tác (remote stream):
  return live.enabled && !live.muted
}

function updateVideoStatus() {
  cleanupListeners()
  hasVideo.value = computeHasVideo()
  if (!props.stream) return

  const tracks = props.stream.getVideoTracks ? props.stream.getVideoTracks() : []
  tracks.forEach(track => {
    const handler = () => {
      hasVideo.value = computeHasVideo()
    }
    track.addEventListener('mute', handler)
    track.addEventListener('unmute', handler)
    track.addEventListener('ended', handler)
    trackListeners.push({ track, event: 'mute', handler })
    trackListeners.push({ track, event: 'unmute', handler })
    trackListeners.push({ track, event: 'ended', handler })
  })
}

function onVideoMetadata() {
  if (props.active === undefined && !props.muted && video.value) {
    hasVideo.value = computeHasVideo() && (video.value.videoWidth > 0)
  }
}

function onVideoResize() {
  if (props.active === undefined && !props.muted && video.value) {
    hasVideo.value = computeHasVideo() && (video.value.videoWidth > 0)
  }
}

async function play() {
  try {
    await video.value?.play()
    blocked.value = false
  } catch {
    blocked.value = true
  }
}

watch(
  () => props.active,
  (val) => {
    if (val !== undefined) {
      hasVideo.value = val
    }
  },
  { immediate: true }
)

watch(
  [() => props.stream, video],
  async () => {
    updateVideoStatus()
    if (video.value) {
      video.value.srcObject = props.stream || null
      if (props.stream) await play()
    }
  },
  { flush: 'post', immediate: true }
)

onBeforeUnmount(cleanupListeners)
</script>

<template>
  <div class="video-frame" :class="{ 'is-speaking': isSpeaking, 'no-video': !hasVideo }">
    <video
      ref="video"
      autoplay
      playsinline
      :muted="muted"
      :aria-label="label"
      :class="{ 'video-hidden': !hasVideo, 'mirrored': muted || mirrored }"
      @loadedmetadata="onVideoMetadata"
      @resize="onVideoResize"
    ></video>

    <!-- Avatar người dùng tròn hiển thị khi không có video stream hoặc camera tắt -->
    <div v-if="!hasVideo" class="video-empty user-avatar-container">
      <div class="avatar-circle-wrapper" :class="{ 'speaking-pulse': isSpeaking }">
        <div class="avatar-circle">
          <svg class="avatar-user-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z"/>
          </svg>
        </div>
        <div v-if="isSpeaking" class="pulse-ring ring-1"></div>
        <div v-if="isSpeaking" class="pulse-ring ring-2"></div>
      </div>
      <p class="avatar-placeholder">{{ placeholder || (stream ? 'Camera đang tắt' : 'Đang chờ kết nối…') }}</p>
    </div>

    <!-- Nhãn tên và chỉ báo âm thanh -->
    <div class="video-label-bar">
      <span class="video-label">{{ label }}</span>
      <span v-if="isSpeaking" class="speaking-indicator" title="Đang nói">
        <span class="speaking-bar"></span>
        <span class="speaking-bar"></span>
        <span class="speaking-bar"></span>
      </span>
    </div>

    <button v-if="blocked && stream" type="button" class="play-overlay" @click="play">
      Nhấn để phát video / âm thanh
    </button>
  </div>
</template>
