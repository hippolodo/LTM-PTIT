<script setup>
import { ref } from 'vue'
import { useAuthStore } from '../stores/authStore.js'
import { apiError } from '../services/api.js'
import ThemeToggle from '../components/ThemeToggle.vue'
const auth=useAuthStore(),displayName=ref(auth.user?.displayName || ''),error=ref(''),saved=ref(false)
async function save(){saved.value=false;try{await auth.update(displayName.value);saved.value=true}catch(e){error.value=apiError(e)}}
</script><template><main><header><RouterLink class="brand" to="/">← RandomConnect</RouterLink><div class="header-right"><nav class="account-nav"><RouterLink to="/friends">Bạn bè & chat riêng</RouterLink><RouterLink to="/history">Lịch sử</RouterLink></nav><ThemeToggle /></div></header><section class="panel auth-panel"><h1>Hồ sơ cá nhân</h1><p>{{ auth.user?.username }} · {{ auth.user?.email }}</p><form class="auth-form" @submit.prevent="save"><label>Tên hiển thị<input v-model="displayName" required maxlength="80"></label><button>Lưu thay đổi</button></form><p v-if="saved" role="status">Đã lưu.</p><p v-if="error" class="error" role="alert">{{ error }}</p></section></main></template>
