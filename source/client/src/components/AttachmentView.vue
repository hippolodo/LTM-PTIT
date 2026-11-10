<script setup>
import { ref,onMounted,watch,onBeforeUnmount } from 'vue'
import { api,apiError } from '../services/api.js'
const props=defineProps({attachment:Object})
const url=ref(''),error=ref(''),busy=ref(false)
let generation=0
async function load(){const token=++generation;busy.value=true;error.value='';try{const {data}=await api.get(`/api/attachments/${props.attachment.id}/download-url`);if(token===generation)url.value=data.url}catch(e){if(token===generation)error.value=apiError(e)}finally{if(token===generation)busy.value=false}}
watch(()=>props.attachment.id,load);onMounted(load);onBeforeUnmount(()=>generation++)
</script><template><div class="attachment"><span>{{ attachment.name }} · {{ (attachment.size/1024).toFixed(1) }} KB</span><img v-if="url && attachment.mimeType.startsWith('image/')" :src="url" :alt="attachment.name" loading="lazy" referrerpolicy="no-referrer"><audio v-else-if="url && attachment.mimeType.startsWith('audio/')" :src="url" controls preload="none"></audio><a v-if="url" :href="url" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer">Mở / tải file</a><button :disabled="busy" @click="load">{{ url?'Làm mới liên kết':'Tải liên kết' }}</button><small v-if="error" role="alert">{{ error }}</small></div></template>
