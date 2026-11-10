import { createRouter,createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import HistoryView from '../views/HistoryView.vue'
import ProfileView from '../views/ProfileView.vue'
import FriendsView from '../views/FriendsView.vue'
import { useAuthStore } from '../stores/authStore.js'
const router=createRouter({history:createWebHistory(),routes:[{path:'/',component:HomeView},{path:'/history',component:HistoryView,meta:{user:true}},{path:'/profile',component:ProfileView,meta:{user:true}},{path:'/friends',component:FriendsView,meta:{user:true}}]})
router.beforeEach(async to=>{const auth=useAuthStore();await auth.restore();if(to.meta.user&&!auth.user)return '/'})
export default router
