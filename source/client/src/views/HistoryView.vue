<script setup>
import { ref, onMounted } from 'vue'
import { api, apiError } from '../services/api.js'
import { useAuthStore } from '../stores/authStore.js'
import ChatBox from '../components/ChatBox.vue'
import ThemeToggle from '../components/ThemeToggle.vue'

const auth = useAuthStore()
const items = ref([])
const page = ref(0)
const totalPages = ref(0)
const selected = ref(null)
const messages = ref([])
const messagePage = ref(0)
const messagePages = ref(0)
const error = ref('')
const busy = ref(false)

async function load(next = 0) {
  busy.value = true
  error.value = ''
  try {
    const { data } = await api.get('/api/conversations', { params: { page: next } })
    items.value = data.items || []
    page.value = next
    totalPages.value = data.totalPages || 0
    if (items.value.length && !selected.value) {
      select(items.value[0])
    }
  } catch (e) {
    error.value = apiError(e)
  } finally {
    busy.value = false
  }
}

async function select(c, older = false) {
  error.value = ''
  try {
    const next = older ? messagePage.value + 1 : 0
    const { data } = await api.get(`/api/conversations/${c.id}/messages`, { params: { page: next } })
    selected.value = c
    const batch = [...(data.items || [])].reverse()
    messages.value = older ? [...batch, ...messages.value] : batch
    messagePage.value = next
    messagePages.value = data.totalPages || 0
  } catch (e) {
    error.value = apiError(e)
  }
}

async function hide(c) {
  error.value = ''
  try {
    await api.delete(`/api/conversations/${c.id}`)
    if (selected.value?.id === c.id) {
      selected.value = null
      messages.value = []
    }
    await load(page.value)
  } catch (e) {
    error.value = apiError(e)
  }
}

function getPeer(c) {
  if (!c?.participants) return null
  return c.participants.find(p => p.id !== auth.user?.id)
}

function getPeerName(c) {
  const peer = getPeer(c)
  return peer?.displayName || peer?.username || (c?.conversationType === 'DIRECT' ? 'Người bạn' : 'Người lạ')
}

function getPeerInitial(c) {
  const name = getPeerName(c)
  return (name || 'U')[0].toUpperCase()
}

function getPeerId(c) {
  return getPeer(c)?.id || ''
}

function formatTime(isoString) {
  if (!isoString) return ''
  try {
    const d = new Date(isoString)
    return d.toLocaleDateString('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return isoString
  }
}

onMounted(() => load())
</script>

<template>
  <main>
    <!-- Header chuẩn thống nhất toàn hệ thống -->
    <header>
      <RouterLink class="brand" to="/" title="Về trang chủ">
        <span class="brand-icon">↔</span>Random<span>Connect</span>
      </RouterLink>
      <div class="header-right">
        <nav class="account-nav">
          <RouterLink to="/" class="nav-link-with-icon" title="Trang chủ">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
            <span>Trang chủ</span>
          </RouterLink>
          <RouterLink to="/friends" class="nav-link-with-icon" title="Bạn bè">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
            <span>Bạn bè</span>
          </RouterLink>
          <RouterLink to="/history" class="nav-link-with-icon router-link-active" title="Lịch sử">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
            <span>Lịch sử</span>
          </RouterLink>
          <RouterLink to="/profile" class="nav-link-with-icon" title="Hồ sơ">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
            <span>{{ auth.user?.displayName || 'Hồ sơ' }}</span>
          </RouterLink>
        </nav>
        <ThemeToggle />
      </div>
    </header>

    <!-- Tiêu đề trang -->
    <div class="page-title-banner">
      <span class="eyebrow">LƯU TRỮ VÀ XEM LẠI</span>
      <h1>Lịch sử cuộc trò chuyện</h1>
      <p>Xem lại nội dung tin nhắn và tệp của các cuộc trò chuyện đã lưu trên tài khoản.</p>
    </div>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <!-- Bố cục 2 cột chuyên nghiệp -->
    <div class="history-layout">
      <!-- Cột trái: Danh sách cuộc trò chuyện -->
      <section class="panel history-sidebar-panel">
        <div class="history-panel-header">
          <h2>Hội thoại đã lưu</h2>
          <span class="badge-count">{{ items.length }}</span>
        </div>

        <p v-if="!items.length && !busy" class="empty-hint">
          Chưa có cuộc trò chuyện nào được lưu.
        </p>

        <div v-else class="history-cards-scroll">
          <article
            v-for="c in items"
            :key="c.id"
            class="history-conv-card"
            :class="{ active: selected?.id === c.id }"
            @click="select(c)"
          >
            <div class="conv-avatar">
              {{ getPeerInitial(c) }}
            </div>
            <div class="conv-info">
              <div class="conv-top-row">
                <strong class="conv-partner-name">{{ getPeerName(c) }}</strong>
                <span
                  class="conv-type-pill"
                  :class="c.conversationType === 'DIRECT' ? 'pill-direct' : 'pill-random'"
                >
                  {{ c.conversationType === 'DIRECT' ? 'Chat riêng' : 'Ngẫu nhiên' }}
                </span>
              </div>
              <div class="conv-bottom-row">
                <span class="conv-time">{{ formatTime(c.startedAt) }}</span>
                <span class="conv-status-text" :class="{ 'is-live': c.status === 'ACTIVE' }">
                  {{ c.status === 'ACTIVE' ? 'Đang diễn ra' : 'Đã kết thúc' }}
                </span>
              </div>
            </div>
            <button
              v-if="c.conversationType !== 'DIRECT' && c.status !== 'ACTIVE'"
              class="conv-hide-btn"
              title="Ẩn khỏi lịch sử của tôi"
              aria-label="Ẩn khỏi lịch sử"
              @click.stop="hide(c)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </article>
        </div>

        <!-- Phân trang -->
        <div class="history-pagination">
          <button
            class="btn-page"
            :disabled="page === 0 || busy"
            @click="load(page - 1)"
          >
            ← Trước
          </button>
          <span class="page-indicator">{{ page + 1 }} / {{ totalPages || 1 }}</span>
          <button
            class="btn-page"
            :disabled="page + 1 >= totalPages || busy"
            @click="load(page + 1)"
          >
            Sau →
          </button>
        </div>
      </section>

      <!-- Cột phải: Khung xem chi tiết tin nhắn -->
      <section class="history-viewer-panel">
        <template v-if="selected">
          <div class="viewer-header panel">
            <div class="viewer-partner-meta">
              <div class="conv-avatar lg">
                {{ getPeerInitial(selected) }}
              </div>
              <div>
                <h2>{{ getPeerName(selected) }}</h2>
                <small class="viewer-time-sub">Bắt đầu lúc {{ formatTime(selected.startedAt) }}</small>
              </div>
            </div>
            <div class="viewer-actions">
              <RouterLink
                v-if="selected.conversationType === 'DIRECT'"
                :to="{ path: '/friends', query: { user: getPeerId(selected) } }"
                class="primary viewer-action-btn"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
                <span>Tiếp tục chat riêng</span>
              </RouterLink>
              <button
                v-else
                class="danger viewer-action-btn"
                :disabled="selected.status === 'ACTIVE'"
                @click="hide(selected)"
              >
                Ẩn khỏi lịch sử
              </button>
            </div>
          </div>

          <div v-if="messagePage + 1 < messagePages" class="older-messages-bar">
            <button class="btn-load-more" @click="select(selected, true)">
              Tải thêm tin nhắn cũ hơn
            </button>
          </div>

          <ChatBox
            :messages="messages"
            :guest-id="auth.user?.id"
            :peer-label="getPeerName(selected)"
            title="Lịch sử tin nhắn đã lưu"
            read-only
          />
        </template>

        <div v-else class="panel viewer-placeholder">
          <div class="placeholder-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="48" height="48">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
          </div>
          <h3>Chưa chọn cuộc trò chuyện</h3>
          <p>Nhấp vào một cuộc trò chuyện từ danh sách bên trái để xem lại nội dung tin nhắn và tệp đính kèm.</p>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.page-title-banner {
  margin: 16px 0 24px 0;
}

.page-title-banner h1 {
  margin: 4px 0 6px 0;
  font-size: 26px;
}

.page-title-banner p {
  color: var(--text-secondary, #94a3b8);
  font-size: 14px;
  margin: 0;
}

.history-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 24px;
  align-items: start;
}

/* Cột trái */
.history-sidebar-panel {
  padding: 16px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color, #26384f);
}

.history-panel-header h2 {
  font-size: 16px;
  margin: 0;
  color: var(--text-main, #f1f5f9);
}

.badge-count {
  font-size: 11.5px;
  font-weight: 700;
  padding: 2px 8px;
  background: var(--bg-card, #1a273c);
  border: 1px solid var(--border-color, #26384f);
  color: var(--text-secondary, #94a3b8);
  border-radius: 999px;
}

.history-cards-scroll {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 520px;
  overflow-y: auto;
}

.history-conv-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--bg-card, #1a273c);
  border: 1px solid var(--border-color, #26384f);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.18s ease;
  position: relative;
}

.history-conv-card:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: var(--accent, #10b981);
}

.history-conv-card.active {
  background: rgba(16, 185, 129, 0.08);
  border-color: var(--accent, #10b981);
  box-shadow: 0 0 0 1px var(--accent, #10b981);
}

.conv-avatar {
  width: 38px;
  height: 38px;
  min-width: 38px;
  border-radius: 50%;
  background: linear-gradient(135deg, #10b981 0%, #0d9488 100%);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.conv-avatar.lg {
  width: 44px;
  height: 44px;
  min-width: 44px;
  font-size: 16px;
}

.conv-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conv-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.conv-partner-name {
  font-size: 13.5px;
  color: var(--text-main, #f1f5f9);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-type-pill {
  font-size: 10.5px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
}

.pill-direct {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
}

.pill-random {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
}

.conv-bottom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: 11.5px;
  color: var(--text-muted, #64748b);
}

.conv-status-text.is-live {
  color: #10b981;
}

.conv-hide-btn {
  background: transparent !important;
  border: none !important;
  padding: 4px !important;
  color: var(--text-muted, #64748b) !important;
  border-radius: 6px;
  cursor: pointer;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.conv-hide-btn:hover {
  color: #ef4444 !important;
  background: rgba(239, 68, 68, 0.15) !important;
}

/* Phân trang */
.history-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid var(--border-color, #26384f);
}

.btn-page {
  padding: 6px 12px !important;
  font-size: 12px !important;
  border-radius: 8px;
}

.page-indicator {
  font-size: 12px;
  color: var(--text-muted, #94a3b8);
  font-weight: 600;
}

/* Cột phải */
.history-viewer-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.viewer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-radius: 14px;
}

.viewer-partner-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.viewer-partner-meta h2 {
  font-size: 17px;
  margin: 0;
  color: var(--text-main, #f1f5f9);
}

.viewer-time-sub {
  font-size: 12px;
  color: var(--text-muted, #94a3b8);
}

.viewer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.viewer-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  font-size: 12.5px;
  border-radius: 8px;
  text-decoration: none;
  font-weight: 600;
}

.older-messages-bar {
  display: flex;
  justify-content: center;
}

.btn-load-more {
  font-size: 12px;
  padding: 6px 14px !important;
  border-radius: 999px;
  background: var(--bg-card, #1a273c) !important;
  border: 1px solid var(--border-color, #26384f) !important;
  color: var(--text-secondary, #94a3b8) !important;
  cursor: pointer;
}

.viewer-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  text-align: center;
  border-radius: 16px;
  color: var(--text-muted, #94a3b8);
  min-height: 480px;
}

.placeholder-icon {
  margin-bottom: 16px;
  color: var(--text-muted, #64748b);
}

.viewer-placeholder h3 {
  font-size: 17px;
  color: var(--text-main, #f1f5f9);
  margin: 0 0 8px 0;
}

.viewer-placeholder p {
  font-size: 13.5px;
  max-width: 360px;
  margin: 0;
  line-height: 1.5;
}

.empty-hint {
  padding: 24px 12px;
  text-align: center;
  color: var(--text-muted, #94a3b8);
  font-size: 13px;
  margin: 0;
}

@media (max-width: 768px) {
  .history-layout {
    grid-template-columns: 1fr;
  }
}
</style>
