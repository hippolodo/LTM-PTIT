import { it, expect, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import FloatingChatWidget from '../../src/components/FloatingChatWidget.vue'

vi.mock('../../src/services/api.js', () => ({
  api: {
    get: vi.fn().mockResolvedValue({ data: { friends: [{ id: 'u1', username: 'testuser', displayName: 'Test User' }], threads: [] } }),
    post: vi.fn().mockResolvedValue({ data: { id: 'conv-1', peer: { id: 'u1', username: 'testuser', displayName: 'Test User' } } })
  },
  apiError: vi.fn(e => e?.message || 'error')
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ path: '/' })
}))

it('renders the floating chat button and toggles open', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)

  const host = document.createElement('div')
  document.body.append(host)
  const app = createApp(FloatingChatWidget)
  app.use(pinia)
  app.mount(host)

  await nextTick()
  const fab = host.querySelector('.floating-chat-fab')
  expect(fab).not.toBeNull()
  expect(fab.textContent).toContain('Chat')

  fab.click()
  await nextTick()
  const windowEl = host.querySelector('.messenger-window')
  expect(windowEl).not.toBeNull()

  app.unmount()
  host.remove()
})
