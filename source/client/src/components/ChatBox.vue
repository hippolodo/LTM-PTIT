<script setup>
import VoiceComposer from './VoiceComposer.vue'
import AttachmentView from './AttachmentView.vue'
import AttachmentComposer from './AttachmentComposer.vue'
import { ref, watch, nextTick } from 'vue'
const props=defineProps({messages:Array,guestId:String,disabled:Boolean,readOnly:Boolean,roomId:String,title:String,peerLabel:String})
const emit=defineEmits(['send','attachment'])
const text=ref(''),list=ref(null)
function send(){const value=text.value.trim();if(props.disabled || !value || value.length>2000)return;emit('send',value);text.value=''}
watch(()=>props.messages?.length,async()=>{await nextTick();if(list.value)list.value.scrollTop=list.value.scrollHeight})
</script><template><section class="chat panel"><div class="panel-heading"><h2>Trò chuyện</h2><span>{{ title || (readOnly?'Lịch sử đã lưu':'Tin nhắn & tệp') }}</span></div><div ref="list" class="messages" aria-live="polite"><p v-if="!messages?.length" class="empty">Một lời chào có thể mở đầu một cuộc trò chuyện.</p><article v-for="message in messages" :key="message.messageId" :class="{mine:message.senderId===guestId}"><small>{{ message.senderId===guestId?'Bạn':(peerLabel || 'Người bạn mới') }}</small><p v-if="message.text">{{ message.text }}</p><AttachmentView v-if="message.attachment" :attachment="message.attachment"/><small v-if="message.senderId===guestId">{{ ({SENT:'Đã gửi',DELIVERED:'Đã nhận',READ:'Đã đọc'})[message.status] || '' }}</small></article></div><VoiceComposer v-if="!readOnly" :room-id="roomId" :disabled="disabled" @sent="emit('attachment',$event)"/><AttachmentComposer v-if="!readOnly" :room-id="roomId" :disabled="disabled" @sent="emit('attachment',$event)"/><form v-if="!readOnly" @submit.prevent="send"><label class="sr-only" for="message">Tin nhắn</label><input id="message" v-model="text" maxlength="2000" placeholder="Nhập lời chào…" :disabled="disabled" autocomplete="off"><button :disabled="disabled || !text.trim()" type="submit">Gửi ↗</button></form></section></template>
