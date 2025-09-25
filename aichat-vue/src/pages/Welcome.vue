<template>
  <div class="welcome-page">
    <a-card title="欢迎使用 AI Chat" :bordered="false">
      <p>这是一个基于 Vue 3 + TypeScript + Ant Design Vue 的 AI 聊天应用前端。</p>
      <a-button type="primary" @click="goToLogin">立即登录</a-button>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goToLogin = () => {
  router.push('/user/login')
}
</script>

<style scoped>
.welcome-page {
  max-width: 600px;
  margin: 0 auto;
  padding: 40px 0;
}
</style>