<script setup>
import { useSessionStore } from '../stores/sessionStore.js'
const session=useSessionStore()
</script><template><div class="controls"><button v-if="session.socketStatus==='CLOSED'" @click="session.connect">Kết nối lại</button><button :disabled="!session.localStream" :aria-pressed="!session.microphoneEnabled" @click="session.toggleMic">{{ session.microphoneEnabled?'Mic: Bật':'Mic: Tắt' }}</button><button :disabled="!session.localStream" :aria-pressed="!session.cameraEnabled" @click="session.toggleCamera">{{ session.cameraEnabled?'Camera: Bật':'Camera: Tắt' }}</button><button class="primary" :disabled="!session.roomId || session.busy" @click="session.next">Next →</button><button class="danger" @click="session.stop">Stop ■</button></div></template>
