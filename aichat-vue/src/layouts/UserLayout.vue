<template>
  <div class="user-layout">
    <div class="container">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
// 用户登录注册布局
</script>

<style scoped>
.user-layout {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.container {
  width: 100%;
  max-width: 400px;
  padding: 20px;
}
</style>