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
    if (!disposed && token === searchGeneration) { results.value = data; searched.value = true }
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
      <RouterLink class="brand" to="/">← RandomConnect</RouterLink>
      <div class="header-right">
        <nav class="account-nav"><RouterLink to="/history">Lịch sử</RouterLink><RouterLink to="/profile">Hồ sơ</RouterLink></nav>
        <ThemeToggle />
      </div>
    </header>
    <h1>Bạn bè & chat riêng</h1>
    <p>Tìm tài khoản theo username hoặc tên hiển thị. Bạn có thể nhắn tin trước khi kết bạn; chat riêng được lưu cho cả hai.</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="friends-layout">
      <section class="social-sidebar">
        <section class="panel social-panel">
          <h2>Tìm người trò chuyện</h2>
          <form class="social-search" @submit.prevent="search">
            <label class="sr-only" for="user-search">Username hoặc tên hiển thị</label>
            <input id="user-search" v-model="query" minlength="2" maxlength="80" required placeholder="Nhập ít nhất 2 ký tự…">
            <button :disabled="searching || query.trim().length < 2">{{ searching ? 'Đang tìm…' : 'Tìm' }}</button>
          </form>
          <p v-if="searched && !results.length">Không tìm thấy tài khoản phù hợp.</p>
          <small v-if="results.length">Hiển thị tối đa 20 tài khoản. Tìm cụ thể hơn nếu chưa thấy người cần tìm.</small>
          <article v-for="user in results" :key="user.id" class="social-user">
            <strong>{{ user.displayName }}</strong><small>@{{ user.username }}</small>
            <div class="social-actions">
              <button @click="open(user)">Nhắn tin</button>
              <button v-if="user.relationship === 'NONE'" :disabled="acting" @click="friendship(user, 'request')">Kết bạn</button>
              <span v-else-if="user.relationship === 'ACCEPTED'">Đã là bạn bè</span>
              <span v-else>{{ user.relationship === 'INCOMING' ? 'Có lời mời đang chờ bên dưới' : 'Đã gửi lời mời' }}</span>
            </div>
          </article>
        </section>
        <section class="panel social-panel">
          <h2>Lời mời nhận được ({{ overview.incoming.length }})</h2>
          <p v-if="!overview.incoming.length">Chưa có lời mời mới.</p>
          <article v-for="user in overview.incoming" :key="user.id" class="social-user">
            <strong>{{ user.displayName }}</strong><small>@{{ user.username }}</small>
            <div class="social-actions"><button :disabled="acting" @click="friendship(user, 'accept')">Chấp nhận</button><button :disabled="acting" @click="friendship(user, 'decline')">Từ chối</button><button @click="open(user)">Nhắn tin</button></div>
          </article>
          <h2>Lời mời đã gửi ({{ overview.outgoing.length }})</h2>
          <article v-for="user in overview.outgoing" :key="user.id" class="social-user">
            <strong>{{ user.displayName }}</strong><small>@{{ user.username }}</small>
            <button :disabled="acting" @click="friendship(user, 'remove')">Hủy lời mời</button>
          </article>
        </section>
        <section class="panel social-panel">
          <h2>Bạn bè ({{ overview.friends.length }})</h2>
          <p v-if="!overview.friends.length">Tìm tài khoản và gửi lời mời để thêm bạn.</p>
          <article v-for="user in overview.friends" :key="user.id" class="social-user">
            <strong>{{ user.displayName }}</strong><small>@{{ user.username }}</small>
            <div class="social-actions"><button @click="open(user)">Nhắn tin</button><button :disabled="acting" @click="friendship(user, 'remove')">Hủy kết bạn</button></div>
          </article>
        </section>
        <section class="panel social-panel">
          <h2>Cuộc trò chuyện riêng</h2>
          <p v-if="!overview.threads.length">Chưa có cuộc trò chuyện riêng.</p>
          <article v-for="user in overview.threads" :key="user.id" class="social-user">
            <button @click="open(user)">{{ user.displayName }} · @{{ user.username }}</button>
          </article>
        </section>
      </section>
      <section class="direct-conversation" :aria-busy="chat.opening">
        <p v-if="chat.opening" role="status">Đang mở cuộc trò chuyện…</p>
        <template v-if="chat.selected">
          <div class="direct-heading"><h2>{{ chat.selected.peer.displayName }} · @{{ chat.selected.peer.username }}</h2><div class="social-actions"><button class="primary" :disabled="!calls.ready || calls.active" @click="calls.dial(chat.selected)">Gọi video</button><button @click="chat.close">Đóng</button></div></div>
          <small v-if="!calls.ready">Đang kết nối dịch vụ gọi video…</small>
          <p>Chat riêng · gửi chữ, file và tin nhắn thoại. Người nhận có thể mở cuộc trò chuyện khi đăng nhập lại.</p>
          <button v-if="chat.hasOlder" :disabled="chat.loadingOlder" @click="chat.older">{{ chat.loadingOlder ? 'Đang tải…' : 'Tải tin nhắn cũ hơn' }}</button>
          <ChatBox :messages="chat.messages" :guest-id="auth.user?.id" :peer-label="chat.selected.peer.displayName" title="Chat riêng đã lưu" read-only />
          <MessageComposer :key="chat.selected.id" v-model="chat.draft" :room-id="chat.selected.id" :sending="chat.sending" @send="chat.send" @attachment="chat.receivedAttachment(chat.selected.id, $event)" />
        </template>
        <p v-else-if="!chat.opening" class="panel social-panel">Chọn “Nhắn tin” để bắt đầu hoặc mở lại cuộc trò chuyện.</p>
        <p v-if="chat.error" class="error" role="alert">{{ chat.error }}</p>
      </section>
    </div>
  </main>
</template>
