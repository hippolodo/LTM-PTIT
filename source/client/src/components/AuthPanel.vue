<script setup>
import { ref } from 'vue'
import { useAuthStore } from '../stores/authStore.js'
import { apiError } from '../services/api.js'
const auth = useAuthStore(),
    mode = ref('login'),
    login = ref(''),
    username = ref(''),
    email = ref(''),
    displayName = ref(''),
    password = ref(''),
    error = ref('')
async function submit() {
    error.value = ''
    try {
        await auth.authenticate(
            mode.value,
            mode.value === 'login'
                ? { login: login.value, password: password.value }
                : {
                      username: username.value,
                      email: email.value,
                      password: password.value,
                      displayName: displayName.value,
                  },
        )
        password.value = ''
    } catch (e) {
        error.value = apiError(e)
    }
}
async function guest() {
    error.value = ''
    try {
        await auth.guest()
    } catch (e) {
        error.value = apiError(e)
    }
}
</script>
<template>
    <section class="panel auth-panel">
        <h2>Bạn muốn bắt đầu thế nào?</h2>
        <!-- <p>
            Khách có đầy đủ chat, file, tin nhắn thoại và cuộc gọi. Hội thoại có
            khách chỉ lưu tạm tối đa 24 giờ, không vào lịch sử tài khoản.
        </p> -->
        <button class="primary" :disabled="auth.busy" @click="guest">
            Tiếp tục với tư cách khách
        </button>
        <div class="auth-tabs">
            <button @click="mode = 'login'" :aria-pressed="mode === 'login'">
                Đăng nhập</button
            ><button
                @click="mode = 'register'"
                :aria-pressed="mode === 'register'"
            >
                Đăng ký
            </button>
        </div>
        <form class="auth-form" @submit.prevent="submit">
            <template v-if="mode === 'register'"
                ><label
                    >Tên đăng nhập<input
                        v-model="username"
                        required
                        minlength="3"
                        maxlength="32"
                        pattern="[A-Za-z0-9_]+"
                        autocomplete="username" /></label
                ><label
                    >Email<input
                        v-model="email"
                        required
                        type="email"
                        maxlength="254"
                        autocomplete="email" /></label
                ><label
                    >Tên hiển thị<input
                        v-model="displayName"
                        maxlength="80"
                        autocomplete="nickname" /></label></template
            ><label v-else
                >Tên đăng nhập hoặc email<input
                    v-model="login"
                    required
                    autocomplete="username" /></label
            ><label
                >Mật khẩu<input
                    v-model="password"
                    required
                    type="password"
                    minlength="8"
                    maxlength="72"
                    :autocomplete="
                        mode === 'login' ? 'current-password' : 'new-password'
                    " /></label
            ><button :disabled="auth.busy" type="submit">
                {{ mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản' }}
            </button>
        </form>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <!-- <small
            >Hai tài khoản: lưu lịch sử cho cả hai. Có khách: tự hết hạn, không
            tự chuyển lịch sử khách sang tài khoản.</small
        > -->
    </section>
</template>
