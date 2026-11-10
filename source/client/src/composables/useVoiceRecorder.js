import { ref,shallowRef } from 'vue'
export function useVoiceRecorder({devices=globalThis.navigator?.mediaDevices,Recorder=globalThis.MediaRecorder,maxSeconds=120,maxBytes=10485760}={}){
 const recording=ref(false),requesting=ref(false),seconds=ref(0),blob=shallowRef(null),url=ref(''),error=ref('')
 let stream,recorder,timer,chunks=[],size=0,generation=0,started=0
 function release(){clearInterval(timer);timer=null;stream?.getTracks().forEach(t=>t.stop());stream=null}
 function cancel(){generation++;if(recorder){recorder.ondataavailable=recorder.onstop=recorder.onerror=null;if(recorder.state!=='inactive')recorder.stop();recorder=null}release();if(url.value)URL.revokeObjectURL(url.value);url.value='';blob.value=null;chunks=[];size=0;recording.value=requesting.value=false;seconds.value=0}
 function stop(){if(recorder&&recorder.state!=='inactive')recorder.stop()}
 async function start(){
  if(recording.value||requesting.value)return
  cancel();error.value=''
  if(!Recorder||!devices?.getUserMedia){error.value='Trình duyệt chưa hỗ trợ ghi âm; hãy dùng HTTPS và trình duyệt mới.';return}
  const token=++generation;requesting.value=true
  try{
   const owned=await devices.getUserMedia({audio:true});if(token!==generation){owned.getTracks().forEach(t=>t.stop());return}stream=owned
   const mime=['audio/webm;codecs=opus','audio/ogg;codecs=opus','audio/mp4'].find(t=>Recorder.isTypeSupported?.(t))
   recorder=new Recorder(owned,mime?{mimeType:mime}:{})
   const current=recorder
   current.ondataavailable=e=>{if(token!==generation||!e.data.size)return;size+=e.data.size;if(size>maxBytes){error.value='Ghi âm vượt quá dung lượng cho phép.';cancel();return}chunks.push(e.data)}
   current.onstop=()=>{if(token!==generation)return;release();recording.value=false;blob.value=new Blob(chunks,{type:current.mimeType || chunks[0]?.type || 'audio/webm'});if(blob.value.size)url.value=URL.createObjectURL(blob.value);recorder=null;chunks=[]}
   current.onerror=()=>{if(token===generation){error.value='Không thể ghi âm. Hãy thử lại.';cancel()}}
   started=Date.now();current.start(1000);recording.value=true
   timer=setInterval(()=>{seconds.value=Math.min(maxSeconds,Math.floor((Date.now()-started)/1000));if(seconds.value>=maxSeconds)stop()},250)
  }catch(e){if(token===generation){error.value=e.name==='NotAllowedError'?'Bạn đã từ chối quyền microphone.':'Không mở được microphone.';release()}}
  finally{if(token===generation)requesting.value=false}
 }
 function file(){if(!blob.value)return null;const type=blob.value.type;const ext=type.includes('mp4')?'m4a':type.includes('ogg')?'ogg':type.includes('wav')?'wav':'webm';return new File([blob.value],`voice-${Date.now()}.${ext}`,{type})}
 return {recording,requesting,seconds,blob,url,error,start,stop,cancel,file}
}
