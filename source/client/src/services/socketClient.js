export class SocketClient {
 constructor({url=import.meta.env.VITE_WS_URL || 'ws://localhost:8081/ws',onMessage,onClose,onError,Socket=globalThis.WebSocket}={}) {
  Object.assign(this,{url,onMessage,onClose,onError,Socket}); this.socket=null; this.timer=null; this.lastMessage=0
 }
 connect() {
  if(this.socket) return
  const ws=this.socket=new this.Socket(this.url)
  ws.onopen=()=>{
   if(this.socket!==ws)return
   this.lastMessage=Date.now()
   this.timer=setInterval(()=>{
    if(Date.now()-this.lastMessage>45000){this.close();this.onError?.('WebSocket heartbeat timeout');this.onClose?.();return}
    this.send('PING')
   },15000)
  }
  ws.onmessage=event=>{if(this.socket!==ws)return;this.lastMessage=Date.now();try { this.onMessage?.(JSON.parse(event.data)) } catch { this.onError?.('Invalid server message') }}
  ws.onerror=()=>{if(this.socket===ws)this.onError?.('Không thể kết nối WebSocket. Hãy kiểm tra server.')}
  ws.onclose=()=>{if(this.socket!==ws)return;clearInterval(this.timer);this.timer=null;this.socket=null;this.onClose?.()}
 }
 send(type,roomId,payload={}) {
  if(this.socket?.readyState!==1) return false
  this.socket.send(JSON.stringify({type,requestId:globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`,...(roomId?{roomId}:{}),payload}));return true
 }
 close() {clearInterval(this.timer);this.timer=null;const ws=this.socket;this.socket=null;if(ws){ws.onopen=ws.onmessage=ws.onerror=ws.onclose=null;ws.close()}}
}
