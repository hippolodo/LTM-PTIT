import axios from 'axios'
import { accessToken,guestToken,currentUser,clearCredentials,authorization } from './credentials.js'
const settings={baseURL:import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',timeout:15000,withCredentials:true}
export const api=axios.create(settings)
const cookies=axios.create(settings)
let csrfPromise,refreshPromise
async function csrf(){if(!csrfPromise)csrfPromise=cookies.get('/api/auth/csrf').then(r=>r.data).catch(e=>{csrfPromise=null;throw e});return csrfPromise}
api.interceptors.request.use(async config=>{
 const auth=config.url?.startsWith('/api/auth/')
 if(auth&&!['get','head','options'].includes(config.method || 'get')){const token=await csrf();config.headers[token.headerName]=token.token}
 if(!auth&&authorization())config.headers.Authorization=authorization()
 return config
})
export async function refreshAccess(){
 if(!refreshPromise)refreshPromise=api.post('/api/auth/refresh').then(({data})=>{accessToken.value=data.accessToken;currentUser.value=data.user;guestToken.value='';return data}).finally(()=>refreshPromise=null)
 return refreshPromise
}
api.interceptors.response.use(r=>r,async error=>{
 const request=error.config
 if(error.response?.status===401&&accessToken.value&&request&&!request._retried&&!request.url?.startsWith('/api/auth/')){
  request._retried=true
  try{await refreshAccess();return api(request)}catch{clearCredentials()}
 }
 return Promise.reject(error)
})
export function apiError(error){return error.response?.data?.message || 'Không thể kết nối server. Hãy thử lại.'}
export async function health(){return (await api.get('/api/health',{timeout:5000})).data}
