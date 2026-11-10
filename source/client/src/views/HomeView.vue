<script setup>
import { ref,watch,onMounted,onBeforeUnmount } from 'vue'
import { health,apiError } from '../services/api.js'
import { useSessionStore } from '../stores/sessionStore.js'
import { useAuthStore } from '../stores/authStore.js'
import { useCallStore } from '../stores/callStore.js'
import AuthPanel from '../components/AuthPanel.vue'
import VideoPlayer from '../components/VideoPlayer.vue'
import ChatRoomView from './ChatRoomView.vue'
import ControlBar from '../components/ControlBar.vue'
import ConnectionStatus from '../components/ConnectionStatus.vue'
const calls=useCallStore()
const auth=useAuthStore(),session=useSessionStore(),status=ref('Đang kiểm tra server…')
async function checkHealth(){try{status.value=(await health()).status==='UP'?'Server sẵn sàng':'Server lỗi'}catch{status.value='Server chưa khả dụng'}}
async function signOut(){session.stop();try{await auth.logout()}catch(e){session.error=apiError(e)}}
function unload(){session.dispose()}
watch(()=>auth.user?.id || (auth.ready?'guest':''),()=>{session.stop();if(auth.ready)session.connect()})
onMounted(async()=>{checkHealth();window.addEventListener('pagehide',unload);await auth.restore();if(auth.ready)session.connect()})
onBeforeUnmount(()=>{window.removeEventListener('pagehide',unload);session.dispose()})
</script><template><main><header><RouterLink to="/" class="brand"><span class="brand-icon">↔</span>Random<span>Connect</span></RouterLink><nav v-if="auth.ready" class="account-nav"><RouterLink v-if="auth.user" to="/friends">Bạn bè & chat riêng</RouterLink><RouterLink v-if="auth.user" to="/history">Lịch sử</RouterLink><RouterLink v-if="auth.user" to="/profile">{{ auth.user.displayName }}</RouterLink><button @click="signOut">{{ auth.user?'Đăng xuất':'Đổi tài khoản' }}</button></nav><button class="server health-button" @click="checkHealth">{{ status }} ↻</button></header><div class="intro"><span class="eyebrow">MỘT KẾT NỐI MỚI, MỘT CÂU CHUYỆN MỚI</span><h1>Gặp một người.<br><em>Mở một câu chuyện.</em></h1><p>Trò chuyện ngẫu nhiên 1–1. Vào ngay với tư cách khách hoặc lưu lịch sử bằng tài khoản.</p></div><AuthPanel v-if="!auth.ready"/><template v-else><ConnectionStatus :status="session.matchStatus"/><p v-if="session.retentionNotice && session.roomId" class="retention-notice">{{ session.persistentHistory?'Cuộc trò chuyện được lưu cho cả hai tài khoản. Bạn có thể ẩn khỏi lịch sử của mình sau khi kết thúc.':'Hội thoại có khách: tự hết hạn theo thời hạn hiển thị, không hiển thị trong lịch sử tài khoản.' }}<span v-if="!session.persistentHistory && session.expiresAt"> Hết hạn: {{ new Date(session.expiresAt).toLocaleString() }}.</span></p><ChatRoomView v-if="session.roomId || session.matchStatus==='WAITING'"/><section v-else class="welcome-grid"><VideoPlayer :stream="session.localStream" muted label="Camera của bạn" placeholder="Bản xem trước camera"/><div class="welcome panel"><span class="eyebrow">CHÀO MỪNG BẠN</span><h2>Sẵn sàng nói xin chào?</h2><p>Bật camera và mic để xem trước, rồi nhấn Start để gọi video với người lạ.</p><button :disabled="calls.active || session.busy || !!session.localStream" @click="session.enableMedia">Bật camera &amp; microphone</button><div class="start-actions"><button class="primary" :disabled="calls.active || session.socketStatus!=='OPEN' || session.busy" @click="session.start()">Start · Video &amp; chat</button></div><small>Camera/mic chỉ được truy cập sau thao tác của bạn. Đây là bản demo nội bộ dành cho người tham gia đồng ý.</small></div></section><p v-if="calls.active" role="status">Bạn đang có cuộc gọi riêng. Kết thúc cuộc gọi trước khi ghép ngẫu nhiên.</p><ControlBar/><p v-if="session.roomId" class="room-id">Room: {{ session.roomId }} · WebSocket: {{ session.socketStatus }}</p><p v-if="session.error || session.mediaError" class="error" role="alert">{{ session.error || session.mediaError }}</p></template><footer>Demo nội bộ · Tôn trọng người đối diện · Bạn có thể dừng bất cứ lúc nào.</footer></main></template>
