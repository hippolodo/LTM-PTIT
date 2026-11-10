import { shallowRef, ref } from 'vue'

export function useMedia(devices = globalThis.navigator?.mediaDevices) {
  // Mặc định: Mic BẬT (true), Camera TẮT (false) theo mong muốn người dùng
  const localStream = shallowRef(null)
  const microphoneEnabled = ref(true)
  const cameraEnabled = ref(false)
  const mediaError = ref('')

  let generation = 0, pending = null

  function release() {
    generation++
    localStream.value?.getTracks().forEach(t => t.stop())
    localStream.value = null
    pending = null
  }

  async function enable() {
    if (localStream.value?.getTracks().some(t => t.readyState === 'live')) {
      // Đảm bảo các track tuân theo đúng thiết lập hiện tại của người dùng
      localStream.value.getAudioTracks().forEach(t => t.enabled = microphoneEnabled.value)
      localStream.value.getVideoTracks().forEach(t => t.enabled = cameraEnabled.value)
      return localStream.value
    }
    if (pending) return pending

    const token = ++generation
    const task = (async () => {
      try {
        if (!devices?.getUserMedia) throw new Error('INSECURE_CONTEXT')
        let stream
        try {
          stream = await devices.getUserMedia({ video: { facingMode: 'user' }, audio: true })
        } catch {
          stream = await devices.getUserMedia({ video: true, audio: true })
        }
        if (token !== generation) {
          stream.getTracks().forEach(t => t.stop())
          return null
        }

        // Tôn trọng thiết lập: Mic theo microphoneEnabled, Camera theo cameraEnabled (mặc định tắt)
        stream.getAudioTracks().forEach(t => t.enabled = microphoneEnabled.value)
        stream.getVideoTracks().forEach(t => t.enabled = cameraEnabled.value)

        localStream.value = stream
        mediaError.value = ''
        return stream
      } catch (e) {
        if (token !== generation) return null
        mediaError.value = e.name === 'NotAllowedError'
          ? 'Bạn đã từ chối camera/mic. Hãy cấp quyền trong trình duyệt để bắt đầu cuộc gọi.'
          : e.name === 'NotFoundError'
          ? 'Không tìm thấy camera/mic. Hãy kết nối thiết bị để bắt đầu cuộc gọi.'
          : 'Không mở được camera/mic. Cần HTTPS hoặc localhost và thiết bị đang khả dụng.'
        throw e
      } finally {
        if (token === generation) pending = null
      }
    })()

    pending = task
    return task
  }

  async function toggleMic() {
    microphoneEnabled.value = !microphoneEnabled.value
    if (localStream.value) {
      localStream.value.getAudioTracks().forEach(t => t.enabled = microphoneEnabled.value)
    }
  }

  async function toggleCamera() {
    cameraEnabled.value = !cameraEnabled.value
    if (localStream.value) {
      localStream.value.getVideoTracks().forEach(t => t.enabled = cameraEnabled.value)
    } else if (cameraEnabled.value) {
      // Nếu người dùng chủ động bấm Bật camera ở thanh điều khiển, khởi tạo luồng camera xem trước
      try {
        await enable()
      } catch {}
    }
  }

  return {
    localStream,
    microphoneEnabled,
    cameraEnabled,
    mediaError,
    enable,
    release,
    toggleMic,
    toggleCamera
  }
}
