<script setup>
import { useId } from 'vue'
import AttachmentComposer from './AttachmentComposer.vue'
import VoiceComposer from './VoiceComposer.vue'
const props = defineProps({ modelValue: { type: String, default: '' }, roomId: String, disabled: Boolean, sending: Boolean })
const emit = defineEmits(['update:modelValue', 'send', 'attachment'])
const inputId = `message-${useId()}`
function send() {
  const text = props.modelValue.trim()
  if (!props.disabled && !props.sending && text && text.length <= 2000) emit('send', text)
}
</script>

<template>
  <form class="message-composer" aria-label="Soạn tin nhắn" @submit.prevent="send">
    <AttachmentComposer compact :room-id="roomId" :disabled="disabled" @sent="emit('attachment', $event)" />
    <VoiceComposer compact :room-id="roomId" :disabled="disabled" @sent="emit('attachment', $event)" />
    <label class="sr-only" :for="inputId">Tin nhắn</label>
    <input :id="inputId" class="message-input" :value="modelValue" :disabled="disabled || sending" maxlength="2000" placeholder="Nhập tin nhắn…" autocomplete="off" @input="emit('update:modelValue', $event.target.value)">
    <button class="message-send primary" :disabled="disabled || sending || !modelValue.trim()" type="submit">{{ sending ? 'Đang gửi…' : 'Gửi ↗' }}</button>
  </form>
</template>
