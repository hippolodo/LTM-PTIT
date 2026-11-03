<script setup>
import { useSessionStore } from '../stores/sessionStore.js'
import VideoPlayer from '../components/VideoPlayer.vue'
import ChatBox from '../components/ChatBox.vue'
const session=useSessionStore()
</script><template><div class="workspace"><section class="video-stage"><VideoPlayer :stream="session.remoteStream" label="Người bạn mới" :placeholder="session.matchStatus==='WAITING'?'Đang tìm một người bạn mới…':'Đang chờ media từ đối tác…'"/><div class="local-preview"><VideoPlayer :stream="session.localStream" muted :label="session.cameraEnabled?'Bạn':'Bạn · camera tắt'" :placeholder="session.textOnly?'Chỉ chat chữ':'Camera tắt'"/></div></section><ChatBox :messages="session.messages" :guest-id="session.guestId" :disabled="!session.roomId" @send="session.sendChat"/></div></template>
