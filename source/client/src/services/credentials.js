import { ref } from 'vue'
export const currentUser=ref(null)
export const accessToken=ref('')
export const guestToken=ref('')
export function clearCredentials(){currentUser.value=null;accessToken.value='';guestToken.value=''}
export function authorization(){return accessToken.value?`Bearer ${accessToken.value}`:guestToken.value?`Guest ${guestToken.value}`:''}
