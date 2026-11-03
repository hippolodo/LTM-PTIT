import { shallowRef, ref } from 'vue'
export function useMedia(devices=globalThis.navigator?.mediaDevices) {
 const localStream=shallowRef(null),microphoneEnabled=ref(true),cameraEnabled=ref(true),mediaError=ref('')
 let generation=0,pending=null
 function release(){generation++;localStream.value?.getTracks().forEach(t=>t.stop());localStream.value=null;pending=null}
 async function enable(){
  if(localStream.value?.getTracks().some(t=>t.readyState==='live'))return localStream.value
  if(pending)return pending
  const token=++generation
  const task=(async()=>{
   try {
    if(!devices?.getUserMedia)throw new Error('INSECURE_CONTEXT')
    const stream=await devices.getUserMedia({video:true,audio:true})
    if(token!==generation){stream.getTracks().forEach(t=>t.stop());return null}
    stream.getAudioTracks().forEach(t=>t.enabled=microphoneEnabled.value)
    stream.getVideoTracks().forEach(t=>t.enabled=cameraEnabled.value)
    localStream.value=stream;mediaError.value='';return stream
   } catch(e){if(token!==generation)return null;mediaError.value=e.name==='NotAllowedError'?'Bạn đã từ chối camera/mic. Hãy cấp quyền trong trình duyệt hoặc chọn Chỉ chat chữ.':e.name==='NotFoundError'?'Không tìm thấy camera/mic. Bạn có thể chọn Chỉ chat chữ.':'Không mở được camera/mic. Cần HTTPS hoặc localhost và thiết bị đang khả dụng.';throw e}
   finally {if(token===generation)pending=null}
  })()
  pending=task;return task
 }
 function toggleMic(){microphoneEnabled.value=!microphoneEnabled.value;localStream.value?.getAudioTracks().forEach(t=>t.enabled=microphoneEnabled.value)}
 function toggleCamera(){cameraEnabled.value=!cameraEnabled.value;localStream.value?.getVideoTracks().forEach(t=>t.enabled=cameraEnabled.value)}
 return {localStream,microphoneEnabled,cameraEnabled,mediaError,enable,release,toggleMic,toggleCamera}
}
