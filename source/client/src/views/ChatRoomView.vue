<script setup>
import { ref, watch } from 'vue'
import { api, apiError } from '../services/api.js'
import { useSessionStore } from '../stores/sessionStore.js'
import VideoPlayer from '../components/VideoPlayer.vue'
import ChatBox from '../components/ChatBox.vue'
const session=useSessionStore()
const friendNotice=ref(''),friendBusy=ref(false)
let friendGeneration=0
watch(()=>session.partnerId,()=>{friendGeneration++;friendNotice.value='';friendBusy.value=false})
async function addFriend(){
 const token=friendGeneration,peer=session.partnerId
 if(!session.persistentHistory || !peer || friendBusy.value)return
 friendBusy.value=true
 try{const {data}=await api.post(`/api/social/friends/${peer}`);if(token===friendGeneration)friendNotice.value=data.relationship==='ACCEPTED'?'Hai bạn đã là bạn bè.':data.relationship==='INCOMING'?'Người này đã gửi lời mời. Mở trang Bạn bè để chấp nhận.':'Đã gửi lời mời kết bạn.'}
 catch(e){if(token===friendGeneration)friendNotice.value=apiError(e)}
 finally{if(token===friendGeneration)friendBusy.value=false}
}
</script><template><div v-if="session.roomId && session.persistentHistory" class="partner-friend controls"><button :disabled="friendBusy" @click="addFriend">Kết bạn với người này</button><span v-if="friendNotice" role="status">{{ friendNotice }}</span></div><div class="workspace"><section class="video-stage"><VideoPlayer :stream="session.remoteStream" label="Người bạn mới" :placeholder="session.matchStatus==='WAITING'?'Đang tìm một người bạn mới…':'Đang chờ media từ đối tác…'"/><div class="local-preview"><VideoPlayer :stream="session.localStream" muted :label="session.cameraEnabled?'Bạn':'Bạn · camera tắt'" placeholder="Camera tắt"/></div></section><ChatBox :room-id="session.roomId" :messages="session.messages" :guest-id="session.guestId" :disabled="!session.roomId" @send="session.sendChat" @attachment="session.receivedAttachment"/></div></template>
