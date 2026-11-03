<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { health } from '../services/api.js'
import { useSessionStore } from '../stores/sessionStore.js'
import VideoPlayer from '../components/VideoPlayer.vue'
import ChatRoomView from './ChatRoomView.vue'
import ControlBar from '../components/ControlBar.vue'
import ConnectionStatus from '../components/ConnectionStatus.vue'
const session=useSessionStore(),status=ref('Đang kiểm tra server…')
async function checkHealth(){try{status.value=(await health()).status==='UP'?'Server sẵn sàng':'Server lỗi'}catch{status.value='Server chưa khả dụng'}}
function unload(){session.dispose()}
onMounted(()=>{session.connect();checkHealth();window.addEventListener('pagehide',unload)})
onBeforeUnmount(()=>{window.removeEventListener('pagehide',unload);session.dispose()})
</script><template><main><header><RouterLink to="/" class="brand"><span class="brand-icon">↔</span>Random<span>Connect</span></RouterLink><button class="server health-button" @click="checkHealth">{{ status }} ↻</button></header><div class="intro"><span class="eyebrow">MỘT KẾT NỐI MỚI, MỘT CÂU CHUYỆN MỚI</span><h1>Gặp một người.<br><em>Mở một câu chuyện.</em></h1><p>Trò chuyện ngẫu nhiên 1–1. Không tài khoản, không lưu lịch sử.</p></div><ConnectionStatus :status="session.matchStatus"/><ChatRoomView v-if="session.roomId || session.matchStatus==='WAITING'"/><section v-else class="welcome-grid"><VideoPlayer :stream="session.localStream" muted label="Camera của bạn" placeholder="Bản xem trước camera"/><div class="welcome panel"><span class="eyebrow">CHÀO MỪNG BẠN</span><h2>Sẵn sàng nói xin chào?</h2><p>Bật camera và mic để xem trước, rồi nhấn Start. Bạn cũng có thể chủ động chọn chỉ chat chữ.</p><button :disabled="session.busy || !!session.localStream" @click="session.enableMedia">Bật camera &amp; microphone</button><div class="start-actions"><button class="primary" :disabled="session.socketStatus!=='OPEN' || session.busy" @click="session.start(false)">Start · Video &amp; chat</button><button :disabled="session.socketStatus!=='OPEN' || session.busy" @click="session.start(true)">Chỉ chat chữ</button></div><small>Camera/mic chỉ được truy cập sau thao tác của bạn. Đây là bản demo nội bộ dành cho người tham gia đồng ý.</small></div></section><ControlBar/><p v-if="session.roomId" class="room-id">Room: {{ session.roomId }} · WebSocket: {{ session.socketStatus }}</p><p v-if="session.textOnly && session.roomId" class="room-id">Chế độ chỉ chat chữ: camera và mic của bạn không được sử dụng.</p><p v-if="session.error || session.mediaError" class="error" role="alert">{{ session.error || session.mediaError }}</p><footer>Demo nội bộ · Tôn trọng người đối diện · Bạn có thể dừng bất cứ lúc nào.</footer></main></template>
