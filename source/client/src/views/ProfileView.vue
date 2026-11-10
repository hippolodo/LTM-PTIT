<script setup>
import { ref } from 'vue'
import { useAuthStore } from '../stores/authStore.js'
import { apiError } from '../services/api.js'
import ThemeToggle from '../components/ThemeToggle.vue'

const auth = useAuthStore()
const displayName = ref(auth.user?.displayName || '')
const error = ref('')
const saved = ref(false)

async function save() {
  saved.value = false
  error.value = ''
  try {
    await auth.update(displayName.value)
    saved.value = true
  } catch (e) {
    error.value = apiError(e)
  }
}
</script>

<template>
  <main>
    <header>
      <RouterLink class="brand" to="/" title="Về trang chủ">
        <span class="brand-icon">↔</span>Random<span>Connect</span>
      </RouterLink>
      <div class="header-right">
        <nav class="account-nav">
          <!-- <RouterLink to="/" class="nav-link-with-icon" title="Trang chủ">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
            <span>Trang chủ</span>
          </RouterLink> -->
          <RouterLink to="/friends" class="nav-link-with-icon" title="Bạn bè">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
            <span>Bạn bè</span>
          </RouterLink>
          <!-- <RouterLink to="/history" class="nav-link-with-icon" title="Lịch sử">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
            <span>Lịch sử</span>
          </RouterLink> -->
          <RouterLink to="/profile" class="nav-link-with-icon router-link-active" title="Hồ sơ">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
            <span>{{ auth.user?.displayName || 'Hồ sơ' }}</span>
          </RouterLink>
        </nav>
        <ThemeToggle />
      </div>
    </header>

    <div class="page-title-banner">
      <span class="eyebrow">TÀI KHOẢN CỦA BẠN</span>
      <h1>Hồ sơ cá nhân</h1>
      <p>Quản lý tên hiển thị và thông tin tài khoản của bạn.</p>
    </div>

    <section class="panel profile-card">
      <div class="profile-header-meta">
        <div class="profile-avatar">
          {{ (auth.user?.displayName || auth.user?.username || 'U')[0].toUpperCase() }}
        </div>
        <div class="profile-info-text">
          <h2>{{ auth.user?.displayName || auth.user?.username }}</h2>
          <span class="profile-badge">@{{ auth.user?.username }}</span>
          <span class="profile-email">{{ auth.user?.email }}</span>
        </div>
      </div>

      <form class="profile-edit-form" @submit.prevent="save">
        <label for="display-name-input">Tên hiển thị công khai</label>
        <div class="input-action-row">
          <input
            id="display-name-input"
            v-model="displayName"
            required
            maxlength="80"
            placeholder="Nhập tên hiển thị…"
          />
          <button class="primary" type="submit">Lưu thay đổi</button>
        </div>
      </form>

      <p v-if="saved" class="save-success" role="status">✓ Đã cập nhật tên hiển thị thành công.</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </section>
  </main>
</template>

<style scoped>
.page-title-banner {
  margin: 16px 0 24px 0;
}

.page-title-banner h1 {
  margin: 4px 0 6px 0;
  font-size: 26px;
}

.page-title-banner p {
  color: var(--text-secondary, #94a3b8);
  font-size: 14px;
  margin: 0;
}

.profile-card {
  max-width: 580px;
  padding: 24px;
  border-radius: 16px;
}

.profile-header-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color, #26384f);
  margin-bottom: 20px;
}

.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #10b981 0%, #0d9488 100%);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.profile-info-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.profile-info-text h2 {
  font-size: 18px;
  margin: 0;
  color: var(--text-main, #f1f5f9);
}

.profile-badge {
  font-size: 12px;
  color: var(--accent, #10b981);
  font-weight: 600;
}

.profile-email {
  font-size: 12.5px;
  color: var(--text-muted, #94a3b8);
}

.profile-edit-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-edit-form label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary, #94a3b8);
}

.input-action-row {
  display: flex;
  gap: 10px;
}

.input-action-row input {
  flex: 1;
  background: var(--bg-input, #0e1726);
  border: 1px solid var(--border-color, #26384f);
  border-radius: 12px;
  padding: 10px 14px;
  color: var(--text-main, #f1f5f9);
  font-size: 14px;
  outline: none;
}

.input-action-row input:focus {
  border-color: var(--accent, #10b981);
}

.save-success {
  margin-top: 12px;
  color: #10b981;
  font-size: 13px;
  font-weight: 600;
}
</style>
