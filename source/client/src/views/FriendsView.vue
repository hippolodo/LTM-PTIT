<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/authStore.js'
import { useDirectChat } from '../composables/useDirectChat.js'
import { useCallStore } from '../stores/callStore.js'
import { api, apiError } from '../services/api.js'
import ChatBox from '../components/ChatBox.vue'
import MessageComposer from '../components/MessageComposer.vue'
import ThemeToggle from '../components/ThemeToggle.vue'

const auth = useAuthStore(), route = useRoute(), chat = reactive(useDirectChat())
const calls = useCallStore()
const query = ref(''), results = ref([]), searched = ref(false), searching = ref(false), error = ref('')
const overview = ref({ friends: [], incoming: [], outgoing: [], threads: [] }), acting = ref(false)
const activeTab = ref('friends') // 'friends', 'incoming', 'search', 'threads'
let disposed = false, searchGeneration = 0, overviewGeneration = 0, timer, searchController

async function load() {
  const token = ++overviewGeneration
  try {
    const { data } = await api.get('/api/social')
    if (!disposed && token === overviewGeneration) overview.value = data
  } catch (e) {
    if (!disposed && token === overviewGeneration) error.value = apiError(e)
  }
}

async function search() {
  const token = ++searchGeneration
  searchController?.abort()
  searchController = new AbortController()
  searching.value = true
  error.value = ''
  try {
    const { data } = await api.get('/api/social/users', { params: { q: query.value.trim() }, signal: searchController.signal })
    if (!disposed && token === searchGeneration) { results.value = data; searched.value = true; activeTab.value = 'search' }
  } catch (e) {
    if (!disposed && token === searchGeneration) error.value = apiError(e)
  } finally {
    if (!disposed && token === searchGeneration) searching.value = false
  }
}

async function friendship(user, action) {
  if (acting.value) return
  acting.value = true
  error.value = ''
  try {
    const url = `/api/social/friends/${user.id}`
    if (action === 'request') await api.post(url)
    else if (action === 'remove') await api.delete(url)
    else await api.patch(url, { accept: action === 'accept' })
    await load()
    if (searched.value && query.value.trim().length >= 2) await search()
  } catch (e) {
    if (!disposed) error.value = apiError(e)
  } finally { if (!disposed) acting.value = false }
}

async function open(user) {
  await chat.open(user)
  if (!disposed) await load()
}

async function openRouteUser() {
  const id = route.query.user
  if (typeof id === 'string') await open({ id })
}

async function refreshLoop() {
  await load()
  if (!disposed) timer = setTimeout(refreshLoop, 5000)
}

watch(() => route.query.user, openRouteUser)
onMounted(() => { refreshLoop(); openRouteUser() })
onBeforeUnmount(() => { disposed = true; searchGeneration++; overviewGeneration++; searchController?.abort(); clearTimeout(timer) })
</script>

<template>
  <main>
    <header>
      <RouterLink class="brand" to="/" title="Về trang chủ">
        <span class="brand-icon">↔</span>Random<span>Connect</span>
      </RouterLink>
      <div class="header-right">
        <nav class="account-nav">
          <!-- <RouterLink to="/" class="nav-link-with-icon" title="Trang chủ">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
            <span>Trang chủ</span>
          </RouterLink> -->
          <!-- <RouterLink to="/history" class="nav-link-with-icon" title="Lịch sử">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
            <span>Lịch sử</span>
          </RouterLink> -->
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

    <div class="social-page-header">
      <div class="social-title-wrap">
        <span class="eyebrow">KẾT NỐI BẠN BÈ</span>
        <h1>Bạn bè & Chat riêng</h1>
        <p>Tìm tài khoản theo username hoặc tên hiển thị. Nhắn tin và gọi video trực tiếp với bạn bè.</p>
      </div>
    </div>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <div class="friends-layout">
      <!-- Cột danh sách xã hội bên trái -->
      <section class="social-sidebar">
        <!-- Khung tìm kiếm hiện đại -->
        <div class="panel social-search-card">
          <form class="social-search-form" @submit.prevent="search">
            <div class="search-input-wrap">
              <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <circle cx="11" cy="11" r="8" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
              <input
                id="user-search"
                v-model="query"
                minlength="2"
                maxlength="80"
                required
                placeholder="Tìm bạn theo tên hoặc @username…"
              />
            </div>
            <button class="primary search-btn" :disabled="searching || query.trim().length < 2">
              {{ searching ? 'Đang tìm…' : 'Tìm kiếm' }}
            </button>
          </form>
        </div>

        <!-- Thanh chuyển tab điều hướng -->
        <nav class="social-tabs-nav" aria-label="Bộ lọc bạn bè">
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'friends' }"
            @click="activeTab = 'friends'"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
            <span>Bạn bè</span>
            <span class="tab-badge">{{ overview.friends.length }}</span>
          </button>

          <button
            class="tab-btn"
            :class="{ active: activeTab === 'incoming' }"
            @click="activeTab = 'incoming'"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <line x1="19" y1="8" x2="19" y2="14" />
              <line x1="22" y1="11" x2="16" y2="11" />
            </svg>
            <span>Lời mời</span>
            <span v-if="overview.incoming.length" class="tab-badge alert">{{ overview.incoming.length }}</span>
            <span v-else class="tab-badge">{{ overview.incoming.length }}</span>
          </button>

          <button
            v-if="searched || results.length"
            class="tab-btn"
            :class="{ active: activeTab === 'search' }"
            @click="activeTab = 'search'"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <span>Kết quả</span>
            <span class="tab-badge">{{ results.length }}</span>
          </button>
        </nav>

        <!-- NỘI DUNG TAB: BẠN BÈ -->
        <section v-if="activeTab === 'friends'" class="panel social-panel">
          <div class="panel-header-row">
            <h2>Danh sách bạn bè ({{ overview.friends.length }})</h2>
          </div>
          <p v-if="!overview.friends.length" class="empty-hint">
            Bạn chưa có người bạn nào. Hãy nhập tên ở thanh tìm kiếm phía trên để kết bạn!
          </p>
          <div v-else class="social-user-list">
            <article v-for="user in overview.friends" :key="user.id" class="social-user-card">
              <div class="user-avatar-circle">
                {{ (user.displayName || user.username || 'U')[0].toUpperCase() }}
              </div>
              <div class="user-meta">
                <strong class="user-display-name">{{ user.displayName || user.username }}</strong>
                <small class="user-handle">@{{ user.username }}</small>
              </div>
              <div class="social-card-actions">
                <button class="primary sm-btn" title="Nhắn tin" @click="open(user)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                  </svg>
                  <span>Nhắn tin</span>
                </button>
                <button class="danger-ghost sm-btn" :disabled="acting" title="Hủy kết bạn" @click="friendship(user, 'remove')">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              </div>
            </article>
          </div>
        </section>

        <!-- NỘI DUNG TAB: LỜI MỜI -->
        <section v-if="activeTab === 'incoming'" class="panel social-panel">
          <div class="panel-header-row">
            <h2>Lời mời nhận được ({{ overview.incoming.length }})</h2>
          </div>
          <p v-if="!overview.incoming.length" class="empty-hint">Không có lời mời kết bạn nào đang chờ.</p>
          <div v-else class="social-user-list">
            <article v-for="user in overview.incoming" :key="user.id" class="social-user-card highlight-card">
              <div class="user-avatar-circle">
                {{ (user.displayName || user.username || 'U')[0].toUpperCase() }}
              </div>
              <div class="user-meta">
                <strong class="user-display-name">{{ user.displayName || user.username }}</strong>
                <small class="user-handle">@{{ user.username }}</small>
              </div>
              <div class="social-card-actions">
                <button class="primary sm-btn" :disabled="acting" @click="friendship(user, 'accept')">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14">
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                  <span>Chấp nhận</span>
                </button>
                <button class="danger-ghost sm-btn" :disabled="acting" @click="friendship(user, 'decline')">
                  <span>Từ chối</span>
                </button>
                <button class="ghost sm-btn" @click="open(user)">Chat</button>
              </div>
            </article>
          </div>

          <div v-if="overview.outgoing.length" class="outgoing-block">
            <h3>Lời mời bạn đã gửi ({{ overview.outgoing.length }})</h3>
            <div class="social-user-list">
              <article v-for="user in overview.outgoing" :key="user.id" class="social-user-card">
                <div class="user-avatar-circle sub">
                  {{ (user.displayName || user.username || 'U')[0].toUpperCase() }}
                </div>
                <div class="user-meta">
                  <strong class="user-display-name">{{ user.displayName }}</strong>
                  <small class="user-handle">@{{ user.username }}</small>
                </div>
                <div class="social-card-actions">
                  <button class="ghost sm-btn" :disabled="acting" @click="friendship(user, 'remove')">
                    Hủy lời mời
                  </button>
                </div>
              </article>
            </div>
          </div>
        </section>

        <!-- NỘI DUNG TAB: KẾT QUẢ TÌM KIẾM -->
        <section v-if="activeTab === 'search'" class="panel social-panel">
          <div class="panel-header-row">
            <h2>Kết quả tìm kiếm ({{ results.length }})</h2>
          </div>
          <p v-if="searched && !results.length" class="empty-hint">Không tìm thấy tài khoản phù hợp với từ khóa.</p>
          <div v-else class="social-user-list">
            <article v-for="user in results" :key="user.id" class="social-user-card">
              <div class="user-avatar-circle">
                {{ (user.displayName || user.username || 'U')[0].toUpperCase() }}
              </div>
              <div class="user-meta">
                <strong class="user-display-name">{{ user.displayName || user.username }}</strong>
                <small class="user-handle">@{{ user.username }}</small>
              </div>
              <div class="social-card-actions">
                <button class="primary sm-btn" @click="open(user)">Nhắn tin</button>
                <button
                  v-if="user.relationship === 'NONE'"
                  class="secondary sm-btn"
                  :disabled="acting"
                  @click="friendship(user, 'request')"
                >
                  + Kết bạn
                </button>
                <span v-else-if="user.relationship === 'ACCEPTED'" class="status-pill friend">Bạn bè</span>
                <span v-else class="status-pill pending">
                  {{ user.relationship === 'INCOMING' ? 'Đã gửi lời mời cho bạn' : 'Đã gửi lời mời' }}
                </span>
              </div>
            </article>
          </div>
        </section>
      </section>

      <!-- Khung chat trực tiếp bên phải -->
      <section class="direct-conversation" :aria-busy="chat.opening">
        <p v-if="chat.opening" role="status" class="opening-status">Đang mở cuộc trò chuyện…</p>
        <template v-if="chat.selected">
          <div class="direct-heading panel">
            <div class="peer-header-info">
              <div class="user-avatar-circle lg">
                {{ (chat.selected.peer.displayName || chat.selected.peer.username || 'U')[0].toUpperCase() }}
              </div>
              <div>
                <h2>{{ chat.selected.peer.displayName }}</h2>
                <small>@{{ chat.selected.peer.username }}</small>
              </div>
            </div>
            <div class="social-actions">
              <button
                class="primary call-btn"
                :disabled="!calls.ready || calls.active"
                @click="calls.dial(chat.selected)"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                  <polygon points="23 7 16 12 23 17 23 7" />
                  <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
                </svg>
                <span>Gọi video</span>
              </button>
              <button class="ghost close-btn" title="Đóng chat" @click="chat.close">✕</button>
            </div>
          </div>
          <small v-if="!calls.ready" class="service-notice">Đang kết nối dịch vụ gọi video…</small>
          <button v-if="chat.hasOlder" class="load-older-btn" :disabled="chat.loadingOlder" @click="chat.older">
            {{ chat.loadingOlder ? 'Đang tải…' : 'Tải tin nhắn cũ hơn' }}
          </button>
          <ChatBox
            :messages="chat.messages"
            :guest-id="auth.user?.id"
            :peer-label="chat.selected.peer.displayName"
            title="Chat riêng đã lưu"
            read-only
          />
          <MessageComposer
            :key="chat.selected.id"
            v-model="chat.draft"
            :room-id="chat.selected.id"
            :sending="chat.sending"
            @send="chat.send"
            @attachment="chat.receivedAttachment(chat.selected.id, $event)"
          />
        </template>
        <div v-else-if="!chat.opening" class="panel direct-placeholder">
          <div class="placeholder-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="48" height="48">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
          </div>
          <h3>Chưa chọn cuộc trò chuyện</h3>
          <p>Nhấp vào nút “Nhắn tin” ở danh sách bên trái để bắt đầu hoặc tiếp tục trò chuyện riêng.</p>
        </div>
        <p v-if="chat.error" class="error" role="alert">{{ chat.error }}</p>
      </section>
    </div>
  </main>
</template>
