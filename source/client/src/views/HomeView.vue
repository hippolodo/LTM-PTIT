<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { health, apiError } from '../services/api.js'
import { useSessionStore } from '../stores/sessionStore.js'
import { useAuthStore } from '../stores/authStore.js'
import { useCallStore } from '../stores/callStore.js'
import AuthPanel from '../components/AuthPanel.vue'
import VideoPlayer from '../components/VideoPlayer.vue'
import ChatRoomView from './ChatRoomView.vue'
import ControlBar from '../components/ControlBar.vue'
import ConnectionStatus from '../components/ConnectionStatus.vue'
import ThemeToggle from '../components/ThemeToggle.vue'

const calls = useCallStore()
const auth = useAuthStore(),
    session = useSessionStore(),
    status = ref('Đang kiểm tra server…')

async function checkHealth() {
    try {
        status.value =
            (await health()).status === 'UP' ? 'Server sẵn sàng' : 'Server lỗi'
    } catch {
        status.value = 'Server chưa khả dụng'
    }
}

async function signOut() {
    session.stop()
    try {
        await auth.logout()
    } catch (e) {
        session.error = apiError(e)
    }
}

function unload() {
    session.dispose()
}

watch(
    () => auth.user?.id || (auth.ready ? 'guest' : ''),
    () => {
        session.stop()
        if (auth.ready) session.connect()
    },
)

onMounted(async () => {
    checkHealth()
    window.addEventListener('pagehide', unload)
    await auth.restore()
    if (auth.ready) session.connect()
})

onBeforeUnmount(() => {
    window.removeEventListener('pagehide', unload)
    session.dispose()
})
</script>

<template>
    <main>
        <header>
            <RouterLink to="/" class="brand">
                <span class="brand-icon">↔</span>Random<span>Connect</span>
            </RouterLink>
            <div class="header-right">
                <nav v-if="auth.ready" class="account-nav">
                    <RouterLink v-if="auth.user" to="/friends" class="nav-link-with-icon" title="Bạn bè & Chat riêng">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                            <circle cx="9" cy="7" r="4" />
                            <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                        </svg>
                        <span>Bạn bè </span>
                    </RouterLink>
                    <!-- <RouterLink v-if="auth.user" to="/history" class="nav-link-with-icon" title="Lịch sử cuộc trò chuyện">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                            <circle cx="12" cy="12" r="10" />
                            <polyline points="12 6 12 12 16 14" />
                        </svg>
                        <span>Lịch sử</span>
                    </RouterLink> -->
                    <RouterLink v-if="auth.user" to="/profile" class="nav-link-with-icon" title="Hồ sơ cá nhân">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                            <circle cx="12" cy="7" r="4" />
                        </svg>
                        <span>{{ auth.user.displayName }}</span>
                    </RouterLink>
                    <button class="nav-link-with-icon btn-logout" @click="signOut">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                            <polyline points="16 17 21 12 16 7" />
                            <line x1="21" y1="12" x2="9" y2="12" />
                        </svg>
                        <span>{{ auth.user ? 'Đăng xuất' : 'Đổi tài khoản' }}</span>
                    </button>
                </nav>
                <button class="server health-button" @click="checkHealth">
                    {{ status }} ↻
                </button>
                <ThemeToggle />
            </div>
        </header>

        <div class="intro">
            <span class="eyebrow">MỘT KẾT NỐI MỚI, MỘT CÂU CHUYỆN MỚI</span>
            <h1>Gặp một người.<br /><em>Mở một câu chuyện.</em></h1>
        </div>

        <AuthPanel v-if="!auth.ready" />
        <template v-else>
            <ConnectionStatus :status="session.matchStatus" />
            <p
                v-if="session.retentionNotice && session.roomId"
                class="retention-notice"
            >
                {{
                    session.persistentHistory
                        ? 'Cuộc trò chuyện được lưu cho cả hai tài khoản. Bạn có thể ẩn khỏi lịch sử của mình sau khi kết thúc.'
                        : 'Hội thoại có khách: tự hết hạn theo thời hạn hiển thị, không hiển thị trong lịch sử tài khoản.'
                }}<span v-if="!session.persistentHistory && session.expiresAt">
                    Hết hạn:
                    {{ new Date(session.expiresAt).toLocaleString() }}.</span
                >
            </p>
            <ChatRoomView
                v-if="session.roomId || session.matchStatus === 'WAITING'"
            />
            <section v-else class="welcome-grid">
                <VideoPlayer
                    :stream="session.localStream"
                    :active="session.cameraEnabled"
                    muted
                    label="Camera của bạn"
                    placeholder="Bản xem trước camera"
                />
                <div class="welcome panel">
                    <span class="eyebrow">CHÀO MỪNG BẠN</span>
                    <h2>Sẵn sàng nói xin chào?</h2>
                    <p>
                        Thiết lập micro và camera ở thanh điều khiển bên dưới, rồi nhấn Bắt đầu kết nối để tìm bạn trò chuyện.
                    </p>
                    <div class="start-actions">
                        <button
                            class="primary start-btn"
                            :disabled="
                                calls.active ||
                                session.socketStatus !== 'OPEN' ||
                                session.busy
                            "
                            @click="session.start()"
                        >
                            <svg class="start-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                <circle cx="12" cy="12" r="10" />
                                <polygon points="10 8 16 12 10 16 10 8" fill="currentColor" />
                            </svg>
                            <span>Bắt đầu kết nối</span>
                        </button>
                    </div>
                </div>
            </section>
            <p v-if="calls.active" role="status">
                Bạn đang có cuộc gọi riêng. Kết thúc cuộc gọi trước khi ghép
                ngẫu nhiên.
            </p>
            <ControlBar />
            <p v-if="session.roomId" class="room-id">
                Room: {{ session.roomId }} · WebSocket:
                {{ session.socketStatus }}
            </p>
            <p
                v-if="session.error || session.mediaError"
                class="error"
                role="alert"
            >
                {{ session.error || session.mediaError }}
            </p>
        </template>
    </main>
</template>
