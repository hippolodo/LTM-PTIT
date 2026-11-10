import { defineStore } from 'pinia'
import { ref,computed } from 'vue'
import { api,refreshAccess } from '../services/api.js'
import { currentUser,accessToken,guestToken,clearCredentials } from '../services/credentials.js'
export const useAuthStore=defineStore('auth',()=>{
 const busy=ref(false),restored=ref(false)
 let restoring
 const ready=computed(()=>!!(accessToken.value||guestToken.value))
 async function restore(){if(restoring)return restoring;restoring=(async()=>{try{await refreshAccess()}catch{}finally{restored.value=true}})();return restoring}
 async function authenticate(mode,body){busy.value=true;try{const {data}=await api.post(`/api/auth/${mode}`,body);clearCredentials();accessToken.value=data.accessToken;currentUser.value=data.user}finally{busy.value=false}}
 async function guest(){busy.value=true;try{const {data}=await api.post('/api/auth/guest');clearCredentials();guestToken.value=data.token}finally{busy.value=false}}
 async function logout(){if(currentUser.value)await api.post('/api/auth/logout');clearCredentials()}
 async function update(displayName){const {data}=await api.patch('/api/users/me',{displayName});currentUser.value=data}
 return {user:currentUser,busy,restored,ready,restore,authenticate,guest,logout,update}
})
