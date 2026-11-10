<script setup>
import { ref,watch,onBeforeUnmount } from 'vue'
import { api,apiError } from '../services/api.js'
const props=defineProps({roomId:String,disabled:Boolean,compact:Boolean})
const emit=defineEmits(['sent'])
const selected=ref(null),preview=ref(''),error=ref(''),busy=ref(false),progress=ref(0),clientId=ref('')
const picker=ref(null)
let controller,generation=0
function clear(){generation++;controller?.abort();controller=null;if(preview.value)URL.revokeObjectURL(preview.value);preview.value='';selected.value=null;clientId.value='';busy.value=false;progress.value=0}
function choose(event){clear();error.value='';const file=event.target.files?.[0];event.target.value='';if(!file)return;if(file.size>10485760){error.value='File tối đa 10 MB.';return}selected.value=file;clientId.value=crypto.randomUUID();if(['image/png','image/jpeg','image/webp'].includes(file.type))preview.value=URL.createObjectURL(file)}
async function upload(file=selected.value,voice=false){if(!file||busy.value||props.disabled||!props.roomId)return;const token=++generation;const room=props.roomId;busy.value=true;error.value='';controller=new AbortController();const form=new FormData();form.append('file',file);form.append('clientMessageId',clientId.value || crypto.randomUUID());form.append('voice',String(voice));try{const {data}=await api.post(`/api/conversations/${room}/attachments`,form,{signal:controller.signal,timeout:60000,onUploadProgress:e=>{if(token===generation&&e.total)progress.value=Math.round(e.loaded/e.total*100)}});if(token===generation){emit('sent',data);clear()}}catch(e){if(token===generation)error.value=apiError(e)}finally{if(token===generation)busy.value=false}}
watch(()=>props.roomId,clear);onBeforeUnmount(clear)
defineExpose({upload,busy})
</script>
<template>
  <div class="attachment-composer" :class="{compact}">
    <button v-if="compact" type="button" class="composer-tool attach-tool" :disabled="disabled || busy" title="Đính kèm file" aria-label="Đính kèm file" @click="picker?.click()">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 13 6-6a3 3 0 0 1 4 4l-8 8a5 5 0 0 1-7-7l8-8"/><path d="m7 15 8-8"/></svg>
    </button>
    <label :class="compact?'sr-only':'file-picker'">Đính kèm<input ref="picker" type="file" :disabled="disabled || busy" accept=".png,.jpg,.jpeg,.webp,.pdf,.txt,.docx" @change="choose"></label>
    <div v-if="selected || error" class="attachment-details">
      <div v-if="selected" class="file-preview"><img v-if="preview" :src="preview" alt="File đã chọn"><span>{{ selected.name }} · {{ (selected.size/1024).toFixed(1) }} KB</span><button type="button" :disabled="busy || disabled" @click="upload()">{{ busy?`Đang tải ${progress}%`:'Gửi file' }}</button><button type="button" @click="clear">Hủy</button></div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </div>
  </div>
</template>
