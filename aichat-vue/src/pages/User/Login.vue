<template>
  <div class="login-container">
    <div class="login-background">
      <div class="bg-shapes">
        <div class="shape shape-1"></div>
        <div class="shape shape-2"></div>
        <div class="shape shape-3"></div>
      </div>
    </div>
    
    <div class="login-content">
      <div class="login-header">
        <div class="logo">
          <el-icon size="48" class="logo-icon"><ChatDotRound /></el-icon>
          <h1 class="logo-text">AI Chat</h1>
        </div>
        <p class="welcome-text">欢迎回来，开启智能对话之旅</p>
      </div>

      <el-card class="login-card" shadow="never">
        <el-form
          :model="formState"
          :rules="rules"
          ref="loginFormRef"
          @submit.prevent="onFinish"
          size="large"
        >
          <el-form-item prop="userAccount">
            <el-input
              v-model="formState.userAccount"
              placeholder="请输入用户名"
              class="login-input"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="userPassword">
            <el-input
              v-model="formState.userPassword"
              type="password"
              placeholder="请输入密码"
              class="login-input"
              show-password
              @keyup.enter="onFinish"
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-button 
              type="primary" 
              :loading="loading" 
              class="login-button"
              @click="onFinish"
            >
              <span v-if="!loading">
                <el-icon><Right /></el-icon>
                立即登录
              </span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>
        </el-form>

        <div class="login-footer">
          <div class="register-link">
            还没有账号？
            <el-button type="primary" link @click="goToRegister">
              立即注册
            </el-button>
          </div>
          
          <el-divider>或</el-divider>
          
          <div class="quick-login">
            <el-button class="demo-btn" @click="quickLogin">
              <el-icon><Lightning /></el-icon>
              体验账号登录
            </el-button>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Right, Lightning, ChatDotRound } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { userLogin } from '@/api/userController'

interface FormState {
  userAccount: string
  userPassword: string
}

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const loginFormRef = ref<FormInstance>()
const formState = reactive<FormState>({
  userAccount: '',
  userPassword: ''
})

const rules = reactive<FormRules>({
  userAccount: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  userPassword: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
})

const onFinish = async () => {
  if (!loginFormRef.value) return
  
  const valid = await loginFormRef.value.validate()
  if (!valid) return

  loading.value = true
  try {
    const response = await userLogin(formState)
    console.log('登录API响应:', response)
    
    if (response.data?.code === 0 && response.data?.data) {
      userStore.setUser(response.data.data)
      userStore.setToken('token-placeholder')
      ElMessage.success('登录成功！')
      router.push('/app-square')
    } else {
      ElMessage.error(response.data?.message || '登录失败')
    }
  } catch (error) {
    console.error('登录错误:', error)
    ElMessage.error('登录失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

const goToRegister = () => {
  router.push('/user/register')
}

const quickLogin = () => {
  formState.userAccount = 'demo'
  formState.userPassword = '123456'
  onFinish()
}
</script>

<style scoped>
.login-container {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.login-background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  z-index: 1;
}

.bg-shapes {
  position: absolute;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.shape {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  animation: float 6s ease-in-out infinite;
}

.shape-1 {
  width: 200px;
  height: 200px;
  top: 10%;
  left: 10%;
  animation-delay: 0s;
}

.shape-2 {
  width: 150px;
  height: 150px;
  top: 60%;
  right: 10%;
  animation-delay: 2s;
}

.shape-3 {
  width: 100px;
  height: 100px;
  bottom: 20%;
  left: 60%;
  animation-delay: 4s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(180deg);
  }
}

.login-content {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 420px;
  padding: 20px;
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-bottom: 16px;
}

.logo-icon {
  color: white;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.2));
}

.logo-text {
  font-size: 32px;
  font-weight: 700;
  color: white;
  margin: 0;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.welcome-text {
  color: rgba(255, 255, 255, 0.9);
  font-size: 16px;
  margin: 0;
}

.login-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  padding: 40px 32px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
}

.login-input {
  height: 50px;
  margin-bottom: 8px;
}

.login-input :deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 1px solid #e1e8ed;
  transition: all 0.3s ease;
}

.login-input :deep(.el-input__wrapper:hover) {
  border-color: #667eea;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

.login-input :deep(.el-input__wrapper.is-focus) {
  border-color: #667eea;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.login-button {
  width: 100%;
  height: 50px;
  border-radius: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  font-size: 16px;
  font-weight: 600;
  margin-top: 8px;
  transition: all 0.3s ease;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.4);
}

.login-button:active {
  transform: translateY(0);
}

.login-footer {
  margin-top: 24px;
}

.register-link {
  text-align: center;
  color: #666;
  font-size: 14px;
}

.demo-btn {
  width: 100%;
  height: 44px;
  border-radius: 12px;
  background: rgba(102, 126, 234, 0.1);
  border: 1px solid rgba(102, 126, 234, 0.3);
  color: #667eea;
  font-weight: 500;
  transition: all 0.3s ease;
}

.demo-btn:hover {
  background: rgba(102, 126, 234, 0.2);
  border-color: #667eea;
  transform: translateY(-1px);
}

:deep(.el-divider__text) {
  background: rgba(255, 255, 255, 0.95);
  color: #999;
  font-size: 12px;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .login-content {
    max-width: 100%;
    padding: 16px;
  }
  
  .login-card {
    padding: 32px 24px;
    border-radius: 16px;
  }
  
  .logo-text {
    font-size: 28px;
  }
  
  .welcome-text {
    font-size: 14px;
  }
  
  .shape {
    display: none;
  }
}

/* 加载动画 */
.login-button.is-loading {
  background: linear-gradient(135deg, #a0a0a0 0%, #888 100%);
}

/* 表单验证样式 */
:deep(.el-form-item.is-error .el-input__wrapper) {
  border-color: #f56565;
  box-shadow: 0 4px 12px rgba(245, 101, 101, 0.2);
}

:deep(.el-form-item__error) {
  font-size: 12px;
  margin-top: 4px;
}
</style>