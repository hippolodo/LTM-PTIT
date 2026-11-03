<script setup>
import { ref, watch, nextTick } from 'vue'
const props=defineProps({messages:Array,guestId:String,disabled:Boolean})
const emit=defineEmits(['send'])
const text=ref(''),list=ref(null)
function send(){const value=text.value.trim();if(props.disabled || !value || value.length>2000)return;emit('send',value);text.value=''}
watch(()=>props.messages?.length,async()=>{await nextTick();if(list.value)list.value.scrollTop=list.value.scrollHeight})
</script><template><section class="chat panel"><div class="panel-heading"><h2>Trò chuyện</h2><span>Chỉ trong phiên này</span></div><div ref="list" class="messages" aria-live="polite"><p v-if="!messages?.length" class="empty">Một lời chào có thể mở đầu một cuộc trò chuyện.</p><article v-for="message in messages" :key="message.messageId" :class="{mine:message.senderId===guestId}"><small>{{ message.senderId===guestId?'Bạn':'Người bạn mới' }}</small><p>{{ message.text }}</p></article></div><form @submit.prevent="send"><label class="sr-only" for="message">Tin nhắn</label><input id="message" v-model="text" maxlength="2000" placeholder="Nhập lời chào…" :disabled="disabled" autocomplete="off"><button :disabled="disabled || !text.trim()" type="submit">Gửi ↗</button></form></section></template>
