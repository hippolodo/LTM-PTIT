<script setup>
import { watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from './stores/authStore.js'
import { useCallStore } from './stores/callStore.js'
import { useTheme } from './composables/useTheme.js'
import DirectCallPanel from './components/DirectCallPanel.vue'
import FloatingChatWidget from './components/FloatingChatWidget.vue'

const auth = useAuthStore(), calls = useCallStore()
const route = useRoute()
const { initTheme } = useTheme()

watch(() => auth.user?.id, id => { calls.disconnect(); if (id) calls.connect(id) }, { immediate: true })
function leave() { calls.disconnect() }
function resume() { if (auth.user) calls.connect(auth.user.id) }

onMounted(() => {
  initTheme()
  window.addEventListener('pagehide', leave)
  window.addEventListener('pageshow', resume)
})
onBeforeUnmount(() => {
  window.removeEventListener('pagehide', leave)
  window.removeEventListener('pageshow', resume)
  calls.disconnect()
})
</script>
<template>
  <RouterView />
  <DirectCallPanel />
  <FloatingChatWidget v-if="auth.user && route.path !== '/friends'" />
</template>
