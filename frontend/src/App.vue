<script setup lang="ts">
import { Microphone } from '@element-plus/icons-vue'

// Main application component
const appTitle = import.meta.env.VITE_APP_TITLE || 'Vue Realtime Voice Chat'
</script>

<template>
  <div id="app">
    <el-container class="app-container">
      <el-header class="app-header">
        <h1>{{ appTitle }}</h1>
      </el-header>
      
      <el-main class="app-main">
        <el-card class="main-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>实时语音对话</span>
              <el-tag type="info" size="small">准备就绪</el-tag>
            </div>
          </template>
          
          <div class="placeholder-content">
            <el-empty description="语音对话组件将在后续任务中实现">
              <el-button type="primary" :icon="Microphone">开始对话</el-button>
            </el-empty>
          </div>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
}

.app-header {
  background-color: var(--el-color-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-header h1 {
  margin: 0;
  font-size: 1.5rem;
}

.app-main {
  padding: 20px;
  background-color: var(--el-bg-color-page);
}

.main-card {
  max-width: 800px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.placeholder-content {
  padding: 40px 20px;
  text-align: center;
}
</style>
