<script setup>
import { reactive,ref,watch,onBeforeUnmount } from 'vue'
import { useVoiceRecorder } from '../composables/useVoiceRecorder.js'
import { api,apiError } from '../services/api.js'
const props=defineProps({roomId:String,disabled:Boolean,compact:Boolean}),emit=defineEmits(['sent'])
const voice=reactive(useVoiceRecorder()),busy=ref(false),error=ref(''),clientId=ref('')
let controller,generation=0
watch(()=>voice.blob,()=>{clientId.value=crypto.randomUUID()})
function cancel(){generation++;controller?.abort();controller=null;busy.value=false;voice.cancel()}
function record(){if(voice.recording)voice.stop();else voice.start()}
async function send(){const file=voice.file();if(!file||busy.value||props.disabled||!props.roomId)return;const token=++generation;const room=props.roomId;busy.value=true;error.value='';controller=new AbortController();const form=new FormData();form.append('file',file);form.append('voice','true');form.append('clientMessageId',clientId.value);try{const {data}=await api.post(`/api/conversations/${room}/attachments`,form,{signal:controller.signal,timeout:60000});if(token===generation){emit('sent',data);cancel()}}catch(e){if(token===generation)error.value=apiError(e)}finally{if(token===generation)busy.value=false}}
watch(()=>props.roomId,cancel);onBeforeUnmount(cancel)
</script>
<template>
  <div class="voice-composer" :class="{compact}">
    <button v-if="compact" type="button" class="composer-tool voice-tool" :class="{recording:voice.recording}" :disabled="disabled || voice.requesting || busy" :title="voice.recording?'Dừng ghi âm':'Ghi tin nhắn thoại (chỉ ghi mic của bạn)'" :aria-label="voice.recording?'Dừng ghi âm':'Ghi tin nhắn thoại'" :aria-pressed="voice.recording" @click="record">
      <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="3" width="6" height="12" rx="3"/><path d="M6 11v1a6 6 0 0 0 12 0v-1M12 18v3M9 21h6"/></svg>
    </button>
    <button v-else-if="!voice.recording && !voice.blob" type="button" :disabled="disabled || voice.requesting" @click="voice.start">{{ voice.requesting?'Đang xin quyền mic…':'Ghi tin nhắn thoại' }}</button>
    <div v-if="!compact || voice.recording || voice.requesting || voice.blob || voice.error || error" class="voice-details">
      <span v-if="voice.requesting" role="status">Đang xin quyền microphone…</span>
      <template v-if="voice.recording"><span role="status">Đang ghi {{ voice.seconds }} / 120 giây</span><button type="button" @click="voice.stop">Dừng ghi</button></template>
      <template v-if="voice.blob"><audio :src="voice.url" controls></audio><button type="button" :disabled="disabled || busy" @click="send">{{ busy?'Đang gửi…':'Gửi ghi âm' }}</button></template>
      <button v-if="voice.recording || voice.requesting || voice.blob" type="button" @click="cancel">Hủy</button>
      <small>Chỉ ghi mic của bạn; không ghi âm cuộc gọi.</small>
      <p v-if="voice.error || error" class="error" role="alert">{{ voice.error || error }}</p>
    </div>
  </div>
</template>
