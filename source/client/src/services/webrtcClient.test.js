import { describe,it,expect,vi } from 'vitest'
import { WebRtcClient } from './webrtcClient.js'
class FakePeer {
 constructor(){this.signalingState='stable';this.remoteDescription=null;this.candidates=[];this.tracks=[];FakePeer.instances.push(this)}
 static instances=[]
 addTrack(track){this.tracks.push(track)}
 addTransceiver(){}
 async createOffer(){return {type:'offer',sdp:'offer'}}
 async createAnswer(){return {type:'answer',sdp:'answer'}}
 async setLocalDescription(sdp){this.localDescription=sdp;this.signalingState=sdp.type==='offer'?'have-local-offer':'stable'}
 async setRemoteDescription(sdp){this.remoteDescription=sdp;this.signalingState=sdp.type==='offer'?'have-remote-offer':'stable'}
 async addIceCandidate(c){this.candidates.push(c)}
 close(){this.closed=true}
}
function setup(){const send=vi.fn(),onError=vi.fn(),onState=vi.fn(),onStream=vi.fn();const rtc=new WebRtcClient({Peer:FakePeer,send,onError,onState,onStream});return {rtc,send,onError,onState,onStream}}
describe('WebRTC lifecycle',()=>{
 it('queues early ICE until the remote description then answers once',async()=>{const {rtc,send}=setup();rtc.start({roomId:'one',initiator:false,iceServers:[]});const pc=rtc.current.pc;await rtc.signal({type:'ICE_CANDIDATE',roomId:'one',payload:{candidate:{candidate:'early'}}});expect(pc.candidates).toHaveLength(0);await rtc.signal({type:'WEBRTC_OFFER',roomId:'one',payload:{sdp:{type:'offer',sdp:'v=0'}}});expect(pc.candidates).toHaveLength(1);expect(send).toHaveBeenCalledWith('WEBRTC_ANSWER','one',expect.any(Object));rtc.close()})
 it('only initiator offers, ignores old room signals and closes all handlers',async()=>{const {rtc,send}=setup();rtc.start({roomId:'old',initiator:true,iceServers:[]});const old=rtc.current;await old.chain;expect(send).toHaveBeenCalledTimes(1);rtc.start({roomId:'new',initiator:false,iceServers:[]});await rtc.signal({type:'ICE_CANDIDATE',roomId:'old',payload:{candidate:{candidate:'stale'}}});expect(old.pc.closed).toBe(true);expect(old.pc.ontrack).toBeNull();expect(rtc.current.pending).toHaveLength(0);rtc.close()})
 it('does not send an offer from an obsolete async operation',async()=>{const {rtc,send}=setup();let resolve;const pending=new Promise(r=>resolve=r);const spy=vi.spyOn(FakePeer.prototype,'createOffer').mockReturnValueOnce(pending);rtc.start({roomId:'old',initiator:true,iceServers:[]});const ctx=rtc.current;await Promise.resolve();rtc.close();resolve({type:'offer',sdp:'stale'});await ctx.chain;expect(send).not.toHaveBeenCalled();spy.mockRestore()})
 it('reports connected only from actual peer connection state',()=>{const {rtc,onState}=setup();rtc.start({roomId:'one',initiator:false,iceServers:[]});expect(onState).not.toHaveBeenCalledWith('CONNECTED');rtc.current.pc.connectionState='connected';rtc.current.pc.onconnectionstatechange();expect(onState).toHaveBeenCalledWith('CONNECTED');rtc.close()})
 it('ICE failure releases peer and reports a useful error',()=>{const {rtc,onError}=setup();rtc.start({roomId:'one',initiator:false,iceServers:[]});const pc=rtc.current.pc;pc.iceConnectionState='failed';pc.oniceconnectionstatechange();expect(pc.closed).toBe(true);expect(rtc.current).toBeNull();expect(onError).toHaveBeenCalledOnce()})
})
