<script setup>
import { ref, watch } from 'vue'
import { api, apiError } from '../services/api.js'
import { useSessionStore } from '../stores/sessionStore.js'
import VideoPlayer from '../components/VideoPlayer.vue'
import ChatBox from '../components/ChatBox.vue'

const session = useSessionStore()
const friendNotice = ref(''),
    friendBusy = ref(false)
let friendGeneration = 0

watch(
    () => session.partnerId,
    () => {
        friendGeneration++
        friendNotice.value = ''
        friendBusy.value = false
    },
)

async function addFriend() {
    const token = friendGeneration,
        peer = session.partnerId
    if (!session.persistentHistory || !peer || friendBusy.value) return
    friendBusy.value = true
    try {
        const { data } = await api.post(`/api/social/friends/${peer}`)
        if (token === friendGeneration) {
            friendNotice.value =
                data.relationship === 'ACCEPTED'
                    ? 'Hai bạn đã là bạn bè.'
                    : data.relationship === 'INCOMING'
                      ? 'Người này đã gửi lời mời. Mở trang Bạn bè để chấp nhận.'
                      : 'Đã gửi lời mời kết bạn.'
        }
    } catch (e) {
        if (token === friendGeneration) friendNotice.value = apiError(e)
    } finally {
        if (token === friendGeneration) friendBusy.value = false
    }
}
</script>

<template>
    <div
        v-if="session.roomId && session.persistentHistory"
        class="partner-friend controls"
    >
        <button :disabled="friendBusy" @click="addFriend">
            Kết bạn với người này
        </button>
        <span v-if="friendNotice" role="status">{{ friendNotice }}</span>
    </div>

    <div class="workspace">
        <section class="video-stage">
            <!-- Khi đã có phòng: Hiển thị video đối tác bình thường -->
            <VideoPlayer
                v-if="session.roomId"
                :stream="session.remoteStream"
                :active="session.partnerCameraEnabled"
                label="Người bạn mới"
                placeholder="Người bạn mới đang tắt camera"
            />

            <!-- Khi đang tìm người: Hiển thị hiệu ứng Radar Scanning trực quan -->
            <div
                v-else-if="session.matchStatus === 'WAITING'"
                class="radar-searching-stage"
            >
                <div class="radar-container">
                    <div class="radar-ring ring-1"></div>
                    <div class="radar-ring ring-2"></div>
                    <div class="radar-ring ring-3"></div>
                    <div class="radar-sweep"></div>
                    <div class="radar-center-hub">
                        <svg
                            class="radar-hub-icon"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="2"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            aria-hidden="true"
                        >
                            <path
                                d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"
                            />
                            <circle cx="9" cy="7" r="4" />
                            <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
                            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                        </svg>
                    </div>
                </div>

                <div class="radar-text-content">
                    <h3 class="radar-title">
                        <span>Đang tìm kiếm người bạn mới</span>
                        <span class="radar-dots">
                            <span>.</span><span>.</span><span>.</span>
                        </span>
                    </h3>
                    <p class="radar-subtitle">
                        Đang quét ngẫu nhiên những người dùng trực tuyến trên hệ
                        thống…
                    </p>
                    <button
                        type="button"
                        class="cancel-search-btn"
                        @click="session.stop"
                    >
                        Hủy tìm kiếm
                    </button>
                </div>
            </div>

            <!-- Khung video của chính mình (Picture-in-picture) -->
            <div class="local-preview">
                <VideoPlayer
                    :stream="session.localStream"
                    :active="session.cameraEnabled"
                    muted
                    label="Bạn"
                    placeholder="Camera tắt"
                />
            </div>
        </section>

        <!-- Khung chat bên cạnh -->
        <ChatBox
            :room-id="session.roomId"
            :messages="session.messages"
            :guest-id="session.guestId"
            :disabled="!session.roomId"
            @send="session.sendChat"
            @attachment="session.receivedAttachment"
        />
    </div>
</template>
