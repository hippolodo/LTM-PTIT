import { ref, onBeforeUnmount } from 'vue'
import { api, apiError } from '../services/api.js'

export function useDirectChat() {
  const selected = ref(null), messages = ref([]), draft = ref(''), error = ref('')
  const opening = ref(false), sending = ref(false), loadingOlder = ref(false), hasOlder = ref(false)
  let generation = 0, controller, timer, cursor = null, polling = false, retry = null

  function merge(batch) {
    const byId = new Map(messages.value.map(message => [message.messageId, message]))
    for (const message of batch) byId.set(message.messageId, message)
    const time = message => message.sentAt.replace(/(?:\.(\d+))?Z$/, (_, part = '') => `.${part.padEnd(9, '0')}Z`)
    messages.value = [...byId.values()].sort((a, b) => time(a).localeCompare(time(b)) || a.messageId.localeCompare(b.messageId))
  }

  function close() {
    generation++
    controller?.abort()
    clearTimeout(timer)
    controller = null
    selected.value = null
    messages.value = []
    draft.value = ''
    cursor = null
    retry = null
    polling = false
    opening.value = sending.value = loadingOlder.value = hasOlder.value = false
  }

  async function poll(token) {
    if (token !== generation || !selected.value || polling) return
    polling = true
    try {
      let more = true
      while (more && token === generation) {
        const { data } = await api.get(`/api/social/direct/${selected.value.id}/messages`, {
          params: cursor ? { after: cursor } : {}, signal: controller.signal
        })
        if (token !== generation) return
        merge(data.items)
        if (data.items.length) cursor = cursor ? data.items.at(-1).messageId : data.items[0].messageId
        more = !!cursor && data.items.length > 0 && data.hasMore
        // Without a cursor, the endpoint returns the latest page in descending order.
        if (!data.items.length) more = false
      }
      error.value = ''
    } catch (e) {
      if (token === generation) error.value = apiError(e)
    } finally {
      if (token === generation) {
        polling = false
        timer = setTimeout(() => poll(token), 2000)
      }
    }
  }

  async function open(user) {
    close()
    error.value = ''
    const token = generation
    controller = new AbortController()
    opening.value = true
    try {
      const { data: thread } = await api.post(`/api/social/direct/${user.id}`, {}, { signal: controller.signal })
      if (token !== generation) return
      const { data } = await api.get(`/api/social/direct/${thread.id}/messages`, { signal: controller.signal })
      if (token !== generation) return
      selected.value = thread
      merge(data.items)
      cursor = data.items[0]?.messageId || null
      hasOlder.value = data.hasMore
      timer = setTimeout(() => poll(token), 2000)
    } catch (e) {
      if (token === generation) error.value = apiError(e)
    } finally {
      if (token === generation) opening.value = false
    }
  }

  async function older() {
    if (!selected.value || loadingOlder.value || !hasOlder.value || !messages.value.length) return
    const token = generation
    loadingOlder.value = true
    try {
      const { data } = await api.get(`/api/social/direct/${selected.value.id}/messages`, {
        params: { before: messages.value[0].messageId }, signal: controller.signal
      })
      if (token !== generation) return
      merge(data.items)
      hasOlder.value = data.hasMore
    } catch (e) {
      if (token === generation) error.value = apiError(e)
    } finally {
      if (token === generation) loadingOlder.value = false
    }
  }

  async function send() {
    const text = draft.value.trim()
    if (!selected.value || sending.value || !text || text.length > 2000) return
    const token = generation
    if (!retry || retry.text !== text) retry = { text, clientMessageId: crypto.randomUUID() }
    sending.value = true
    error.value = ''
    try {
      const { data } = await api.post(`/api/social/direct/${selected.value.id}/messages`, retry, { signal: controller.signal })
      if (token !== generation) return
      merge([data])
      if (draft.value.trim() === text) draft.value = ''
      retry = null
    } catch (e) {
      if (token === generation) error.value = apiError(e)
    } finally {
      if (token === generation) sending.value = false
    }
  }

  onBeforeUnmount(close)
  return { selected, messages, draft, error, opening, sending, loadingOlder, hasOlder, open, close, older, send }
}
