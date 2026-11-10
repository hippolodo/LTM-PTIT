import { beforeEach,it,expect,vi } from 'vitest'
import { createPinia,setActivePinia } from 'pinia'
import { useAuthStore } from './authStore.js'
import { currentUser,accessToken,guestToken,clearCredentials } from '../services/credentials.js'
import { api,refreshAccess } from '../services/api.js'
vi.mock('../services/api.js',()=>({api:{post:vi.fn()},refreshAccess:vi.fn()}))
beforeEach(()=>{setActivePinia(createPinia());clearCredentials();vi.resetAllMocks()})
it('restores once despite concurrent callers',async()=>{refreshAccess.mockResolvedValue({});const auth=useAuthStore();await Promise.all([auth.restore(),auth.restore()]);expect(refreshAccess).toHaveBeenCalledOnce();expect(auth.restored).toBe(true)})
it('replaces guest identity on login without retaining guest token',async()=>{guestToken.value='temporary';api.post.mockResolvedValue({data:{accessToken:'jwt',user:{id:'user'}}});await useAuthStore().authenticate('login',{});expect(accessToken.value).toBe('jwt');expect(currentUser.value.id).toBe('user');expect(guestToken.value).toBe('')})
it('keeps identity if server logout fails, clears it after successful revocation',async()=>{currentUser.value={id:'user'};accessToken.value='jwt';api.post.mockRejectedValueOnce(new Error('offline'));const auth=useAuthStore();await expect(auth.logout()).rejects.toThrow('offline');expect(auth.ready).toBe(true);api.post.mockResolvedValueOnce({});await auth.logout();expect(auth.ready).toBe(false);expect(currentUser.value).toBeNull()})
