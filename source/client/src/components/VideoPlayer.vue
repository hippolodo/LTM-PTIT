<script setup>
import { ref, watch } from 'vue'
const props=defineProps({stream:Object,muted:Boolean,label:String,placeholder:String})
const video=ref(null),blocked=ref(false)
async function play(){try{await video.value?.play();blocked.value=false}catch{blocked.value=true}}
watch([()=>props.stream,video],async()=>{if(video.value){video.value.srcObject=props.stream || null;if(props.stream)await play()}},{flush:'post',immediate:true})
</script><template><div class="video-frame"><video ref="video" autoplay playsinline :muted="muted" :aria-label="label"></video><div v-if="!stream" class="video-empty"><span>↔</span><p>{{ placeholder }}</p></div><span class="video-label">{{ label }}</span><button v-if="blocked && stream" class="play-overlay" @click="play">Nhấn để phát video / âm thanh</button></div></template>
