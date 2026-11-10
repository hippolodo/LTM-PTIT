import { createApp } from 'vue'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { useDirectChat } from './useDirectChat.js'
import { api } from '../services/api.js'

vi.mock('../services/api.js', () => ({ api: { get: vi.fn(), post: vi.fn() }, apiError: () => 'Request failed' }))

let app, chat, host
const message = (id, text = id) => ({ messageId: id, sentAt: `2026-11-09T10:00:0${id}.000Z`, text })
beforeEach(() => {
  vi.useFakeTimers()
  vi.resetAllMocks()
  host = document.createElement('div')
  app = createApp({ setup() { chat = useDirectChat(); return () => null } })
  app.mount(host)
  api.post.mockResolvedValue({ data: { id: 'room', peer: { id: 'peer', displayName: 'Peer' } } })
  api.get.mockResolvedValue({ data: { items: [], hasMore: false } })
})
afterEach(() => { app.unmount(); vi.useRealTimers() })

it('preserves a failed draft and reuses its ID when retrying', async () => {
  await chat.open({ id: 'peer' })
  chat.draft.value = ' hello '
  api.post.mockRejectedValueOnce(new Error('lost response'))
  await chat.send()
  expect(chat.draft.value).toBe(' hello ')
  const first = api.post.mock.calls.at(-1)[1]
  api.post.mockResolvedValueOnce({ data: message('1', 'hello') })
  await chat.send()
  expect(api.post.mock.calls.at(-1)[1].clientMessageId).toBe(first.clientMessageId)
  expect(chat.draft.value).toBe('')
  expect(chat.messages.value).toHaveLength(1)
})

it('ignores a slow open response after switching conversations and releases timers', async () => {
  let resolveFirst
  api.post.mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve }))
  const first = chat.open({ id: 'first' })
  api.post.mockResolvedValueOnce({ data: { id: 'second', peer: { id: 'second' } } })
  await chat.open({ id: 'second' })
  resolveFirst({ data: { id: 'first', peer: { id: 'first' } } })
  await first
  expect(chat.selected.value.id).toBe('second')
  expect(api.get).toHaveBeenCalledTimes(1)
  chat.close()
  expect(vi.getTimerCount()).toBe(0)
})

it('polls from the server cursor even if a newer own message arrived through POST', async () => {
  api.get.mockResolvedValueOnce({ data: { items: [message('1')], hasMore: false } })
  await chat.open({ id: 'peer' })
  chat.draft.value = 'my reply'
  api.post.mockResolvedValueOnce({ data: message('3') })
  await chat.send()
  api.get.mockResolvedValueOnce({ data: { items: [message('2'), message('3')], hasMore: false } })
  await vi.advanceTimersByTimeAsync(2000)
  expect(api.get.mock.calls.at(-1)[1].params).toEqual({ after: '1' })
  expect(chat.messages.value.map(m => m.messageId)).toEqual(['1', '2', '3'])
})

it('loads older messages using a stable cursor and deduplicates response items', async () => {
  api.get.mockResolvedValueOnce({ data: { items: [message('3'), message('2')], hasMore: true } })
  await chat.open({ id: 'peer' })
  api.get.mockResolvedValueOnce({ data: { items: [message('2'), message('1')], hasMore: false } })
  await chat.older()
  expect(api.get.mock.calls.at(-1)[1].params).toEqual({ before: '2' })
  expect(chat.messages.value.map(m => m.messageId)).toEqual(['1', '2', '3'])
  expect(chat.hasOlder.value).toBe(false)
})
