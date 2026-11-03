export class WebRtcClient {
 constructor({send,onStream,onState,onError,Peer=globalThis.RTCPeerConnection,Stream=globalThis.MediaStream}) {
  Object.assign(this,{send,onStream,onState,onError,Peer,Stream});this.current=null
 }
 start({roomId,initiator,stream,iceServers}) {
  this.close()
  const pc=new this.Peer({iceServers}),ctx={pc,roomId,pending:[],chain:Promise.resolve(),disconnectTimer:null,connectTimer:null}
  this.current=ctx
  const active=()=>this.current===ctx
  for(const track of stream?.getTracks() || [])pc.addTrack(track,stream)
  // Text-only still negotiates receive slots, so a video peer may send media.
  if(!stream){pc.addTransceiver('audio',{direction:'recvonly'});pc.addTransceiver('video',{direction:'recvonly'})}
  pc.ontrack=event=>{if(active())this.onStream?.(event.streams[0] || new this.Stream(pc.getReceivers().map(r=>r.track).filter(Boolean)))}
  pc.onicecandidate=event=>{if(active() && event.candidate)this.send('ICE_CANDIDATE',roomId,{candidate:event.candidate.toJSON?.() || event.candidate})}
  const failed=()=>{if(active()){this.close();this.onError?.('Không kết nối được media. Hãy thử Next; mạng này có thể cần TURN.')}}
  const check=()=>{
   if(!active())return
   if(pc.connectionState==='connected'){clearTimeout(ctx.connectTimer);clearTimeout(ctx.disconnectTimer);ctx.disconnectTimer=null;this.onState?.('CONNECTED')}
   else if(pc.connectionState==='failed' || pc.iceConnectionState==='failed')failed()
   else if(pc.connectionState==='disconnected' && !ctx.disconnectTimer){this.onState?.('CONNECTING');ctx.disconnectTimer=setTimeout(failed,10000)}
  }
  pc.onconnectionstatechange=check;pc.oniceconnectionstatechange=check
  ctx.connectTimer=setTimeout(failed,30000)
  this.onState?.('CONNECTING')
  if(initiator)pc.createDataChannel?.('session')
  if(initiator)this.enqueue(ctx,async()=>{
   const offer=await pc.createOffer();if(!active())return
   await pc.setLocalDescription(offer);if(active())this.send('WEBRTC_OFFER',roomId,{sdp:pc.localDescription.toJSON?.() || pc.localDescription})
  })
 }
 enqueue(ctx,task){ctx.chain=ctx.chain.then(()=>{if(this.current===ctx)return task()}).catch(()=>{if(this.current===ctx){this.close();this.onError?.('Lỗi thương lượng WebRTC. Hãy chọn Next để thử lại.')}});return ctx.chain}
 signal(message){
  const ctx=this.current;if(!ctx || message.roomId!==ctx.roomId)return Promise.resolve()
  return this.enqueue(ctx,async()=>{
   const active=()=>this.current===ctx,pc=ctx.pc
   if(message.type==='ICE_CANDIDATE'){
    if(!pc.remoteDescription)ctx.pending.push(message.payload.candidate)
    else await pc.addIceCandidate(message.payload.candidate)
    return
   }
   if(message.type==='WEBRTC_OFFER' && (pc.signalingState!=='stable' || pc.remoteDescription))return
   if(message.type==='WEBRTC_ANSWER' && pc.signalingState!=='have-local-offer')return
   await pc.setRemoteDescription(message.payload.sdp);if(!active())return
   for(const candidate of ctx.pending.splice(0)){await pc.addIceCandidate(candidate);if(!active())return}
   if(message.type==='WEBRTC_OFFER'){
    const answer=await pc.createAnswer();if(!active())return
    await pc.setLocalDescription(answer);if(active())this.send('WEBRTC_ANSWER',ctx.roomId,{sdp:pc.localDescription.toJSON?.() || pc.localDescription})
   }
  })
 }
 close(){const ctx=this.current;this.current=null;if(ctx){clearTimeout(ctx.disconnectTimer);clearTimeout(ctx.connectTimer);ctx.pending=[];const pc=ctx.pc;pc.ontrack=pc.onicecandidate=pc.onconnectionstatechange=pc.oniceconnectionstatechange=null;pc.close()}this.onStream?.(null)}
}
