<script setup>
import { useSessionStore } from '../stores/sessionStore.js'
const session = useSessionStore()
</script>

<template>
  <div class="controls control-bar">
    <button
      v-if="session.socketStatus === 'CLOSED'"
      type="button"
      class="control-btn reconnect-btn"
      @click="session.connect"
    >
      <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2"/>
      </svg>
      <span>Kết nối lại</span>
    </button>

    <!-- Toggle Microphone -->
    <button
      type="button"
      class="control-btn toggle-btn mic-btn"
      :class="{ 'is-active': session.microphoneEnabled, 'is-muted': !session.microphoneEnabled }"
      :aria-pressed="!session.microphoneEnabled"
      :title="session.microphoneEnabled ? 'Tắt microphone' : 'Bật microphone'"
      @click="session.toggleMic"
    >
      <!-- Mic ON icon -->
      <svg v-if="session.microphoneEnabled" class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/>
        <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
        <line x1="12" x2="12" y1="19" y2="22"/>
      </svg>
      <!-- Mic OFF icon -->
      <svg v-else class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <line x1="2" x2="22" y1="2" y2="22"/>
        <path d="M18.89 13.23A7.12 7.12 0 0 0 19 12v-2"/>
        <path d="M5 10v2a7 7 0 0 0 12 5"/>
        <path d="M15 9.34V5a3 3 0 0 0-5.68-1.33"/>
        <path d="M9 9v3a3 3 0 0 0 5.12 2.12"/>
        <line x1="12" x2="12" y1="19" y2="22"/>
      </svg>
      <span class="btn-text">{{ session.microphoneEnabled ? 'Mic: Bật' : 'Mic: Tắt' }}</span>
      <span class="toggle-pill" :class="{ on: session.microphoneEnabled }"></span>
    </button>

    <!-- Toggle Camera -->
    <button
      type="button"
      class="control-btn toggle-btn cam-btn"
      :class="{ 'is-active': session.cameraEnabled, 'is-off': !session.cameraEnabled }"
      :aria-pressed="!session.cameraEnabled"
      :title="session.cameraEnabled ? 'Tắt camera' : 'Bật camera'"
      @click="session.toggleCamera"
    >
      <!-- Cam ON icon -->
      <svg v-if="session.cameraEnabled" class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="m22 7-6 4v2l6 4V7Z"/>
        <rect width="14" height="12" x="2" y="6" rx="3"/>
      </svg>
      <!-- Cam OFF icon -->
      <svg v-else class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <line x1="2" x2="22" y1="2" y2="22"/>
        <path d="M10.66 6H14a2 2 0 0 1 2 2v2.34l1 1L22 7v10l-3.34-2.23"/>
        <path d="M16 16a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2"/>
      </svg>
      <span class="btn-text">{{ session.cameraEnabled ? 'Camera: Bật' : 'Camera: Tắt' }}</span>
      <span class="toggle-pill" :class="{ on: session.cameraEnabled }"></span>
    </button>

    <!-- Next button -->
    <button
      type="button"
      class="control-btn primary next-btn"
      :disabled="!session.roomId || session.busy"
      title="Chuyển sang người bạn mới"
      @click="session.next"
    >
      <span>Next</span>
      <svg class="btn-icon-right" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M5 12h14M12 5l7 7-7 7"/>
      </svg>
    </button>

    <!-- Stop button -->
    <button
      type="button"
      class="control-btn danger stop-btn"
      title="Dừng phiên trò chuyện"
      @click="session.stop"
    >
      <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <rect x="6" y="6" width="12" height="12" rx="2.5"/>
      </svg>
      <span>Dừng</span>
    </button>
  </div>
</template>
