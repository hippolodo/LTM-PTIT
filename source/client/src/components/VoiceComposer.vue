<script setup>
import { reactive,ref,watch,onBeforeUnmount } from 'vue'
import { useVoiceRecorder } from '../composables/useVoiceRecorder.js'
import { api,apiError } from '../services/api.js'
const props=defineProps({roomId:String,disabled:Boolean}),emit=defineEmits(['sent'])
const voice=reactive(useVoiceRecorder()),busy=ref(false),error=ref(''),clientId=ref('')
let controller,generation=0
watch(()=>voice.blob,()=>{clientId.value=crypto.randomUUID()})
function cancel(){generation++;controller?.abort();controller=null;busy.value=false;voice.cancel()}
async function send(){const file=voice.file();if(!file||busy.value||props.disabled||!props.roomId)return;const token=++generation;const room=props.roomId;busy.value=true;error.value='';controller=new AbortController();const form=new FormData();form.append('file',file);form.append('voice','true');form.append('clientMessageId',clientId.value);try{const {data}=await api.post(`/api/conversations/${room}/attachments`,form,{signal:controller.signal,timeout:60000});if(token===generation){emit('sent',data);cancel()}}catch(e){if(token===generation)error.value=apiError(e)}finally{if(token===generation)busy.value=false}}
watch(()=>props.roomId,cancel);onBeforeUnmount(cancel)
</script><template><div class="voice-composer"><button v-if="!voice.recording && !voice.blob" :disabled="disabled || voice.requesting" @click="voice.start">{{ voice.requesting?'Đang xin quyền mic…':'Ghi tin nhắn thoại' }}</button><template v-if="voice.recording"><span role="status">Đang ghi {{ voice.seconds }} / 120 giây</span><button @click="voice.stop">Dừng ghi</button></template><template v-if="voice.blob"><audio :src="voice.url" controls></audio><button :disabled="disabled || busy" @click="send">{{ busy?'Đang gửi…':'Gửi ghi âm' }}</button></template><button v-if="voice.recording || voice.requesting || voice.blob" @click="cancel">Hủy</button><small>Chỉ ghi mic sau thao tác của bạn; không ghi remote stream của cuộc gọi.</small><p v-if="voice.error || error" class="error" role="alert">{{ voice.error || error }}</p></div></template>
