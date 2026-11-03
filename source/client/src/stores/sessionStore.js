import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import { SocketClient } from '../services/socketClient.js'
import { WebRtcClient } from '../services/webrtcClient.js'
import { getIceServers } from '../services/iceConfig.js'
import { useMedia } from '../composables/useMedia.js'
export const useSessionStore=defineStore('session',()=>{
 const guestId=ref(null),socketStatus=ref('CLOSED'),matchStatus=ref('IDLE'),roomId=ref(null),partnerId=ref(null),error=ref(''),messages=ref([]),busy=ref(false),remoteStream=shallowRef(null),textOnly=ref(false)
 const media=useMedia()
 let socket,iceServers=[],serverIceServers,action=0
 const rtc=new WebRtcClient({send:(...args)=>socket?.send(...args),onStream:s=>remoteStream.value=s,onState:s=>matchStatus.value=s,onError:e=>{error.value=e;matchStatus.value='ERROR'}})
 function resetRoom(){rtc.close();roomId.value=null;partnerId.value=null;messages.value=[]}
 function dispatch(m) {
  if(m.type==='SESSION_READY'){guestId.value=m.payload.guestId;serverIceServers=m.payload.iceServers;socketStatus.value='OPEN'}
  if(m.type==='WAITING'){matchStatus.value='WAITING';busy.value=false}
  if(m.type==='MATCH_FOUND'){
   if(roomId.value===m.roomId)return
   resetRoom();roomId.value=m.roomId;partnerId.value=m.payload.partnerId;busy.value=false
   try {rtc.start({roomId:m.roomId,initiator:m.payload.initiator,stream:media.localStream.value,iceServers:(import.meta.env.VITE_ICE_CONFIG_URL || import.meta.env.VITE_ICE_SERVERS)?iceServers:(m.payload.iceServers || iceServers)})}catch{error.value='Trình duyệt không hỗ trợ WebRTC. Chat chữ vẫn sẵn sàng.';matchStatus.value='ERROR'}
  }
  if(m.type==='CHAT_MESSAGE' && m.roomId===roomId.value && !messages.value.some(x=>x.messageId===m.payload.messageId))messages.value.push(m.payload)
  if(['WEBRTC_OFFER','WEBRTC_ANSWER','ICE_CANDIDATE'].includes(m.type) && m.roomId===roomId.value)rtc.signal(m)
  if(m.type==='PARTNER_LEFT' && m.roomId===roomId.value){resetRoom();media.release();matchStatus.value='PARTNER_LEFT';busy.value=false}
  if(m.type==='ROOM_CLOSED' && (!roomId.value || m.roomId===roomId.value)){resetRoom();matchStatus.value='IDLE'}
  if(m.type==='ERROR'){error.value=m.payload.message;busy.value=false}
 }
 function connect(){
  if(socket)return
  socketStatus.value='CONNECTING';error.value=''
  socket=new SocketClient({onMessage:dispatch,onError:e=>error.value=e,onClose:()=>{socket=null;action++;socketStatus.value='CLOSED';resetRoom();media.release();matchStatus.value='ERROR';busy.value=false;error.value='Mất kết nối. Nhấn Kết nối lại để bắt đầu phiên mới.'}});socket.connect()
 }
 async function enableMedia(){error.value='';try{await media.enable()}catch{error.value=media.mediaError.value}}
 async function start(mode=false){
  if(busy.value || roomId.value || matchStatus.value==='WAITING' || socketStatus.value!=='OPEN')return
  const token=++action;busy.value=true;error.value='';textOnly.value=mode
  try {
   if(mode){media.release();media.mediaError.value=''}else {const stream=await media.enable();if(!stream)return}
   iceServers=await getIceServers(serverIceServers);if(token!==action)return
   if(!socket?.send('FIND_PARTNER'))throw new Error('Socket closed')
  }catch{if(token===action){error.value=media.mediaError.value || 'Không thể bắt đầu. Kiểm tra kết nối và cấu hình ICE.';media.release()}}
  finally{if(token===action && matchStatus.value!=='WAITING')busy.value=false}
 }
 function next(){if(!roomId.value || busy.value)return;const old=roomId.value;resetRoom();error.value='';busy.value=true;matchStatus.value='WAITING';if(!socket?.send('NEXT_PARTNER',old)){busy.value=false;matchStatus.value='ERROR'}}
 function sendChat(text){text=text.trim();if(!roomId.value || !text || text.length>2000)return false;return socket?.send('CHAT_MESSAGE',roomId.value,{text}) || false}
 function stop(){socket?.send('LEAVE_ROOM',roomId.value);dispose();matchStatus.value='IDLE';error.value=''}
 function dispose(){action++;socket?.close();socket=null;resetRoom();media.release();socketStatus.value='CLOSED';guestId.value=null;busy.value=false}
 return {guestId,socketStatus,matchStatus,roomId,partnerId,error,messages,busy,remoteStream,textOnly,...media,connect,start,next,stop,sendChat,dispose,dispatch,enableMedia}
})
