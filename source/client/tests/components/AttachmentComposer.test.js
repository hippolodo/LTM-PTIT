import { createApp, h, reactive, nextTick } from 'vue'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import AttachmentComposer from '../../src/components/AttachmentComposer.vue'
import { api } from '../../src/services/api.js'
vi.mock('../../src/services/api.js', () => ({ api: { post: vi.fn() }, apiError: () => 'Upload failed' }))
let app, host, props, sent
beforeEach(() => {
  vi.resetAllMocks()
  host = document.createElement('div')
  props = reactive({ roomId: 'direct-one', disabled: false })
  sent = []
  app = createApp({ render: () => h(AttachmentComposer, { ...props, onSent: m => sent.push(m) }) })
  app.mount(host)
})
afterEach(() => app.unmount())
async function select() {
  const input = host.querySelector('input[type=file]')
  Object.defineProperty(input, 'files', { configurable: true, value: [new File(['hello'], 'hello.txt', { type: 'text/plain' })] })
  input.dispatchEvent(new Event('change'))
  await nextTick()
}
async function send() { [...host.querySelectorAll('button')].find(b => b.textContent === 'Gửi file').click(); await nextTick(); await nextTick() }

it('uploads to the selected direct conversation and preserves retry ID on failure', async () => {
  await select()
  api.post.mockRejectedValueOnce(new Error('lost response'))
  await send()
  const [url, form] = api.post.mock.calls[0]
  expect(url).toBe('/api/conversations/direct-one/attachments')
  expect(form.get('file').name).toBe('hello.txt')
  expect(host.textContent).toContain('Upload failed')
  api.post.mockResolvedValueOnce({ data: { messageId: 'file-message' } })
  await send()
  expect(api.post.mock.calls[1][1].get('clientMessageId')).toBe(form.get('clientMessageId'))
  expect(sent).toEqual([{ messageId: 'file-message' }])
})

it('aborts an old upload when switching direct conversations and ignores its late response', async () => {
  let resolve
  api.post.mockImplementationOnce(() => new Promise(r => { resolve = r }))
  await select(); await send()
  const signal = api.post.mock.calls[0][2].signal
  props.roomId = 'direct-two'; await nextTick()
  expect(signal.aborted).toBe(true)
  resolve({ data: { messageId: 'old-file' } })
  await nextTick(); await nextTick()
  expect(sent).toEqual([])
  expect(host.textContent).not.toContain('hello.txt')
})
