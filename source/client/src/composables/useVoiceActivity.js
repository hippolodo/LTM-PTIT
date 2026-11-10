import { ref, watch, onBeforeUnmount } from 'vue'

export function useVoiceActivity(streamSource, { threshold = 12, intervalMs = 60, holdMs = 350 } = {}) {
  const isSpeaking = ref(false)
  let audioCtx = null
  let analyser = null
  let source = null
  let timer = null
  let holdTimer = null

  function cleanup() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    if (holdTimer) {
      clearTimeout(holdTimer)
      holdTimer = null
    }
    if (source) {
      try { source.disconnect() } catch {}
      source = null
    }
    if (analyser) {
      try { analyser.disconnect() } catch {}
      analyser = null
    }
    if (audioCtx && audioCtx.state !== 'closed') {
      try { audioCtx.close() } catch {}
      audioCtx = null
    }
    isSpeaking.value = false
  }

  function setup(stream) {
    cleanup()
    if (!stream) return
    const audioTracks = stream.getAudioTracks ? stream.getAudioTracks() : []
    if (!audioTracks.length) return

    try {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext
      if (!AudioContextClass) return
      audioCtx = new AudioContextClass()
      analyser = audioCtx.createAnalyser()
      analyser.fftSize = 256
      analyser.smoothingTimeConstant = 0.3
      source = audioCtx.createMediaStreamSource(stream)
      source.connect(analyser)

      const bufferLength = analyser.frequencyBinCount
      const dataArray = new Uint8Array(bufferLength)

      timer = setInterval(() => {
        if (!analyser) return
        analyser.getByteFrequencyData(dataArray)
        let sum = 0
        for (let i = 0; i < bufferLength; i++) {
          sum += dataArray[i]
        }
        const average = sum / bufferLength

        if (average > threshold) {
          if (holdTimer) {
            clearTimeout(holdTimer)
            holdTimer = null
          }
          isSpeaking.value = true
        } else if (isSpeaking.value && !holdTimer) {
          // Giữ sáng viền 350ms giữa các khoảng ngắt nghỉ âm tiết để viền không bị nhấp nháy giật cục
          holdTimer = setTimeout(() => {
            isSpeaking.value = false
            holdTimer = null
          }, holdMs)
        }
      }, intervalMs)
    } catch (e) {
      isSpeaking.value = false
    }
  }

  watch(
    () => (typeof streamSource === 'function' ? streamSource() : streamSource?.value ?? streamSource),
    (stream) => setup(stream),
    { immediate: true }
  )

  onBeforeUnmount(cleanup)

  return { isSpeaking }
}
