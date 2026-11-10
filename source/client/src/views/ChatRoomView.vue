<script setup>
import { ref, watch } from 'vue'
import { apiError } from '../services/api.js'
import { useSessionStore } from '../stores/sessionStore.js'
import { useAuthStore } from '../stores/authStore.js'
import VideoPlayer from '../components/VideoPlayer.vue'
import ChatBox from '../components/ChatBox.vue'

const session = useSessionStore()
const auth = useAuthStore()
const friendNotice = ref('')
const friendBusy = ref(false)
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
    const token = friendGeneration
    if (!session.persistentHistory || !session.partnerId || friendBusy.value) return
    friendBusy.value = true
    try {
        await session.sendFriendRequest(auth.user?.displayName || '')
        if (token === friendGeneration) {
            friendNotice.value = 'Đã gửi lời mời kết bạn.'
        }
    } catch (e) {
        if (token === friendGeneration) friendNotice.value = apiError(e)
    } finally {
        if (token === friendGeneration) friendBusy.value = false
    }
}

async function acceptFriend() {
    const token = friendGeneration
    if (!session.partnerId || friendBusy.value) return
    friendBusy.value = true
    try {
        await session.acceptFriendRequest()
        if (token === friendGeneration) {
            friendNotice.value = 'Hai bạn đã là bạn bè.'
        }
    } catch (e) {
        if (token === friendGeneration) friendNotice.value = apiError(e)
    } finally {
        if (token === friendGeneration) friendBusy.value = false
    }
}

async function declineFriend() {
    const token = friendGeneration
    if (!session.partnerId || friendBusy.value) return
    friendBusy.value = true
    try {
        await session.declineFriendRequest()
        if (token === friendGeneration) {
            friendNotice.value = ''
        }
    } catch (e) {
        if (token === friendGeneration) friendNotice.value = apiError(e)
    } finally {
        if (token === friendGeneration) friendBusy.value = false
    }
}
</script>

<template>
    <!-- Popup thông báo kết bạn ở góc trên bên phải (chấp nhận ngay tại màn hình chat, không chuyển trang) -->
    <teleport to="body">
        <transition name="toast-slide">
            <div
                v-if="session.incomingFriendRequest"
                class="friend-request-toast"
                role="alert"
            >
                <div class="toast-avatar">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
                        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                        <circle cx="9" cy="7" r="4" />
                        <line x1="19" y1="8" x2="19" y2="14" />
                        <line x1="22" y1="11" x2="16" y2="11" />
                    </svg>
                </div>
                <div class="toast-content">
                    <div class="toast-header">
                        <strong>{{ session.incomingFriendRequest.fromUserName || session.partnerName || 'Đối tác' }}</strong>
                        <span class="toast-badge">Lời mời kết bạn</span>
                    </div>
                    <p class="toast-text">Muốn kết bạn với bạn trong cuộc trò chuyện này</p>
                </div>
                <div class="toast-actions">
                    <button class="toast-btn accept" :disabled="friendBusy" @click="acceptFriend">
                        Chấp nhận
                    </button>
                    <button class="toast-btn decline" :disabled="friendBusy" @click="declineFriend">
                        Bỏ qua
                    </button>
                </div>
            </div>
        </transition>
    </teleport>

    <!-- Thanh trạng thái kết bạn phía trên phòng chat (tinh gọn, đẹp mắt) -->
    <div
        v-if="session.roomId && session.persistentHistory"
        class="room-social-bar"
    >
        <div class="partner-info-pill">
            <span class="partner-avatar-dot"></span>
            <span class="partner-display-title">
                Đối phương: <strong>{{ session.partnerName || 'Người bạn mới' }}</strong>
            </span>
        </div>

        <div class="partner-social-action">
            <!-- ĐÃ LÀ BẠN BÈ: Không hiện chữ "Kết bạn với người này", hiện huy hiệu Bạn bè -->
            <div v-if="session.partnerFriendship === 'ACCEPTED'" class="friend-chip accepted">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="16" height="16">
                    <polyline points="20 6 9 17 4 12" />
                </svg>
                <span>Đã là bạn bè</span>
            </div>

            <!-- ĐÃ GỬI LỜI MỜI -->
            <div v-else-if="session.partnerFriendship === 'OUTGOING'" class="friend-chip outgoing">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                </svg>
                <span>Đã gửi lời mời kết bạn</span>
            </div>

            <!-- CÓ LỜI MỜI ĐẾN TỪ ĐỐI PHƯƠNG -->
            <div v-else-if="session.partnerFriendship === 'INCOMING'" class="friend-chip incoming">
                <span>{{ session.partnerName || 'Đối tác' }} đã gửi lời mời</span>
                <button class="chip-btn" :disabled="friendBusy" @click="acceptFriend">Chấp nhận</button>
            </div>

            <!-- CHƯA KẾT BẠN: Hiện nút kết bạn đẹp mắt có icon -->
            <button
                v-else
                class="btn-friend-request"
                :disabled="friendBusy"
                @click="addFriend"
            >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                    <circle cx="9" cy="7" r="4" />
                    <line x1="19" y1="8" x2="19" y2="14" />
                    <line x1="22" y1="11" x2="16" y2="11" />
                </svg>
                <span>Kết bạn với người này</span>
            </button>

            <span v-if="friendNotice" class="friend-notice-msg" role="status">{{ friendNotice }}</span>
        </div>
    </div>

    <div class="workspace">
        <section class="video-stage">
            <!-- Khi đã có phòng: Hiển thị video đối tác với tên thật nếu có -->
            <VideoPlayer
                v-if="session.roomId"
                :stream="session.remoteStream"
                :active="session.partnerCameraEnabled"
                :label="session.partnerName || 'Người bạn mới'"
                :placeholder="`${session.partnerName || 'Người bạn mới'} đang tắt camera`"
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

        <!-- Khung chat bên cạnh kèm nhãn tên người bạn mới -->
        <ChatBox
            :room-id="session.roomId"
            :messages="session.messages"
            :guest-id="session.guestId"
            :peer-label="session.partnerName || 'Người bạn mới'"
            :disabled="!session.roomId"
            @send="session.sendChat"
            @attachment="session.receivedAttachment"
        />
    </div>
</template>
