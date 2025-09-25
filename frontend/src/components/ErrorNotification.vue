<template>
  <div class="error-notification">
    <!-- 错误提示对话框 -->
    <el-dialog
      v-model="showErrorDialog"
      :title="dialogTitle"
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
    >
      <div class="error-content">
        <div class="error-message">
          <el-icon class="error-icon" :size="24">
            <WarningFilled v-if="currentError?.severity === 'high' || currentError?.severity === 'critical'" />
            <Warning v-else-if="currentError?.severity === 'medium'" />
            <InfoFilled v-else />
          </el-icon>
          <span class="message-text">{{ errorMessage }}</span>
        </div>

        <!-- 错误详情 -->
        <div v-if="showDetails" class="error-details">
          <el-collapse v-model="activeCollapse">
            <el-collapse-item title="错误详情" name="details">
              <div class="details-content">
                <p><strong>错误类型:</strong> {{ currentError?.type }}</p>
                <p><strong>发生时间:</strong> {{ formatTime(currentError?.timestamp) }}</p>
                <div v-if="currentError?.details" class="additional-details">
                  <strong>附加信息:</strong>
                  <pre>{{ JSON.stringify(currentError.details, null, 2) }}</pre>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>

        <!-- 用户指导 -->
        <div v-if="userGuidance.length > 0" class="user-guidance">
          <h4>解决方案:</h4>
          <el-steps :active="guidanceStep" direction="vertical" size="small">
            <el-step
              v-for="(step, index) in userGuidance"
              :key="index"
              :title="`步骤 ${index + 1}`"
              :description="step"
            />
          </el-steps>
        </div>

        <!-- 重试信息 -->
        <div v-if="showRetryInfo" class="retry-info">
          <el-alert
            :title="retryMessage"
            type="info"
            :closable="false"
            show-icon
          >
            <template #default>
              <div class="retry-details">
                <p>重试次数: {{ retryCount }} / {{ maxRetries }}</p>
                <div v-if="isRetrying" class="retry-progress">
                  <el-progress
                    :percentage="retryProgress"
                    :show-text="false"
                    :stroke-width="6"
                    status="success"
                  />
                  <p class="retry-text">正在重试中，请稍候...</p>
                </div>
              </div>
            </template>
          </el-alert>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeDialog">关闭</el-button>
          <el-button
            v-if="canRetry"
            type="primary"
            :loading="isRetrying"
            @click="handleRetry"
          >
            {{ isRetrying ? '重试中...' : '重试' }}
          </el-button>
          <el-button
            v-if="showResetButton"
            type="warning"
            @click="handleReset"
          >
            重新开始
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 网络状态指示器 -->
    <div v-if="showNetworkStatus" class="network-status">
      <el-tag
        :type="networkStatusType"
        :effect="networkStatusEffect"
        size="small"
      >
        <el-icon class="status-icon">
          <Connection v-if="isOnline" />
          <Close v-else />
        </el-icon>
        {{ networkStatusText }}
      </el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import {
  ElDialog,
  ElIcon,
  ElButton,
  ElAlert,
  ElProgress,
  ElSteps,
  ElStep,
  ElCollapse,
  ElCollapseItem,
  ElTag
} from 'element-plus'
import {
  WarningFilled,
  Warning,
  InfoFilled,
  Connection,
  Close
} from '@element-plus/icons-vue'
import { useErrorHandler } from '../composables/useErrorHandler'
import type { AppError } from '../types/error'
import { ErrorSeverity } from '../types/error'

interface Props {
  showNetworkStatus?: boolean
  showDetails?: boolean
  autoClose?: boolean
  autoCloseDelay?: number
}

const props = withDefaults(defineProps<Props>(), {
  showNetworkStatus: true,
  showDetails: false,
  autoClose: false,
  autoCloseDelay: 5000
})

const emit = defineEmits<{
  retry: []
  reset: []
  close: []
}>()

// 使用错误处理composable
const {
  currentError,
  hasError,
  canRetry,
  isRetrying,
  retryCount,
  retry,
  clearError,
  getErrorGuidance
} = useErrorHandler()

// 组件状态
const showErrorDialog = ref(false)
const activeCollapse = ref<string[]>([])
const guidanceStep = ref(0)
const isOnline = ref(navigator.onLine)
const retryProgress = ref(0)
const maxRetries = ref(3)

// 计算属性
const dialogTitle = computed(() => {
  if (!currentError.value) return '错误'
  
  const severityMap = {
    [ErrorSeverity.LOW]: '提示',
    [ErrorSeverity.MEDIUM]: '警告',
    [ErrorSeverity.HIGH]: '错误',
    [ErrorSeverity.CRITICAL]: '严重错误'
  }
  
  return severityMap[currentError.value.severity] || '错误'
})

const errorMessage = computed(() => {
  return currentError.value?.message || '发生未知错误'
})

const userGuidance = computed(() => {
  if (!currentError.value) return []
  return getErrorGuidance(currentError.value.type)
})

const showRetryInfo = computed(() => {
  return canRetry.value || isRetrying.value
})

const retryMessage = computed(() => {
  if (isRetrying.value) {
    return '正在尝试重新连接...'
  }
  return canRetry.value ? '可以尝试重新连接' : '已达到最大重试次数'
})

const showResetButton = computed(() => {
  return currentError.value?.type === 'session_timeout' || 
         currentError.value?.type === 'connection_lost'
})

const networkStatusType = computed(() => {
  return isOnline.value ? 'success' : 'danger'
})

const networkStatusEffect = computed(() => {
  return isOnline.value ? 'light' : 'dark'
})

const networkStatusText = computed(() => {
  return isOnline.value ? '网络已连接' : '网络已断开'
})

// 监听错误状态变化
watch(hasError, (newValue) => {
  if (newValue) {
    showErrorDialog.value = true
    
    // 自动关闭
    if (props.autoClose && currentError.value?.severity === ErrorSeverity.LOW) {
      setTimeout(() => {
        closeDialog()
      }, props.autoCloseDelay)
    }
  }
})

// 监听重试进度
watch(isRetrying, (newValue) => {
  if (newValue) {
    startRetryProgress()
  } else {
    retryProgress.value = 0
  }
})

// 网络状态监听
const handleOnline = () => {
  isOnline.value = true
}

const handleOffline = () => {
  isOnline.value = false
}

onMounted(() => {
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
})

onUnmounted(() => {
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
})

// 方法
const closeDialog = () => {
  showErrorDialog.value = false
  clearError()
  emit('close')
}

const handleRetry = async () => {
  try {
    await retry()
    emit('retry')
  } catch (error) {
    console.error('Retry failed:', error)
  }
}

const handleReset = () => {
  closeDialog()
  emit('reset')
}

const formatTime = (timestamp?: number) => {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleString()
}

const startRetryProgress = () => {
  retryProgress.value = 0
  const interval = setInterval(() => {
    retryProgress.value += 10
    if (retryProgress.value >= 100 || !isRetrying.value) {
      clearInterval(interval)
      retryProgress.value = 100
    }
  }, 100)
}

// 暴露方法给父组件
defineExpose({
  showError: (error: AppError) => {
    showErrorDialog.value = true
  },
  closeDialog,
  clearError
})
</script>

<style scoped>
.error-notification {
  position: relative;
}

.error-content {
  padding: 16px 0;
}

.error-message {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.error-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.message-text {
  flex: 1;
  line-height: 1.5;
  font-size: 14px;
}

.error-details {
  margin: 16px 0;
}

.details-content {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.details-content p {
  margin: 8px 0;
}

.additional-details pre {
  background: var(--el-fill-color-light);
  padding: 8px;
  border-radius: 4px;
  font-size: 11px;
  overflow-x: auto;
}

.user-guidance {
  margin: 16px 0;
}

.user-guidance h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.retry-info {
  margin: 16px 0;
}

.retry-details p {
  margin: 8px 0;
  font-size: 13px;
}

.retry-progress {
  margin-top: 12px;
}

.retry-text {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.network-status {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 2000;
}

.status-icon {
  margin-right: 4px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .error-notification :deep(.el-dialog) {
    width: 90% !important;
    margin: 5vh auto;
  }
  
  .error-message {
    flex-direction: column;
    gap: 8px;
  }
  
  .dialog-footer {
    flex-direction: column;
  }
  
  .dialog-footer .el-button {
    width: 100%;
  }
}
</style>