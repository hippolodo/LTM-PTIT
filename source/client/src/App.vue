<script setup>
import { watch, onMounted, onBeforeUnmount } from 'vue'
import { useAuthStore } from './stores/authStore.js'
import { useCallStore } from './stores/callStore.js'
import DirectCallPanel from './components/DirectCallPanel.vue'
const auth = useAuthStore(), calls = useCallStore()
watch(() => auth.user?.id, id => { calls.disconnect(); if (id) calls.connect(id) }, { immediate: true })
function leave() { calls.disconnect() }
function resume() { if (auth.user) calls.connect(auth.user.id) }
onMounted(() => { window.addEventListener('pagehide', leave); window.addEventListener('pageshow', resume) })
onBeforeUnmount(() => { window.removeEventListener('pagehide', leave); window.removeEventListener('pageshow', resume); calls.disconnect() })
</script>
<template><RouterView /><DirectCallPanel /></template>
