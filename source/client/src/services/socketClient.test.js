import { it,expect,vi } from 'vitest'
import { SocketClient } from './socketClient.js'
class FakeSocket {constructor(){this.readyState=1;FakeSocket.last=this}send=vi.fn();close=vi.fn()}
it('dispatches JSON once, prevents duplicate connections and cleans heartbeat',()=>{vi.useFakeTimers();const onMessage=vi.fn();const client=new SocketClient({url:'ws://test',Socket:FakeSocket,onMessage});client.connect();const ws=FakeSocket.last;client.connect();expect(FakeSocket.last).toBe(ws);ws.onopen();ws.onmessage({data:JSON.stringify({type:'WAITING'})});expect(onMessage).toHaveBeenCalledExactlyOnceWith({type:'WAITING'});vi.advanceTimersByTime(15000);expect(JSON.parse(ws.send.mock.calls[0][0]).type).toBe('PING');client.close();expect(ws.close).toHaveBeenCalledOnce();expect(vi.getTimerCount()).toBe(0);vi.useRealTimers()})
it('rejects sends on closed socket',()=>{const client=new SocketClient();expect(client.send('FIND_PARTNER')).toBe(false)})

it('expires a silent socket without leaking its heartbeat',()=>{vi.useFakeTimers();const onClose=vi.fn(),client=new SocketClient({Socket:FakeSocket,onClose});client.connect();FakeSocket.last.onopen();vi.advanceTimersByTime(60000);expect(onClose).toHaveBeenCalledOnce();expect(client.socket).toBeNull();expect(vi.getTimerCount()).toBe(0);vi.useRealTimers()})
