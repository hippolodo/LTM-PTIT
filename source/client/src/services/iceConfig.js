import axios from 'axios'
export async function getIceServers(serverIceServers){
 if(import.meta.env.VITE_ICE_CONFIG_URL){const {data}=await axios.get(import.meta.env.VITE_ICE_CONFIG_URL,{timeout:5000,withCredentials:true});if(!Array.isArray(data.iceServers))throw new Error('Invalid ICE config');return data.iceServers}
 const servers=JSON.parse(import.meta.env.VITE_ICE_SERVERS || JSON.stringify(serverIceServers || [{"urls":"stun:stun.l.google.com:19302"}]))
 if(!Array.isArray(servers))throw new Error('Invalid ICE config');return servers
}
