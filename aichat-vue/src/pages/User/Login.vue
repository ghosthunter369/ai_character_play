<template>
  <div class="login-container">
    <div class="login-content">
      <div class="login-header"><p class="auth-eyebrow">YOUR CONVERSATION SPACE</p><h1>欢迎回来</h1><p class="welcome-text">登录后，继续你的对话。</p></div>

      <el-card class="login-card" shadow="never">
        <el-form
          :model="formState"
          :rules="rules"
          ref="loginFormRef"
          @submit.prevent="onFinish"
          size="large"
          label-position="top"
        >
          <el-form-item prop="userAccount" label="账号">
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

          <el-form-item prop="userPassword" label="密码">
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
          
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Right } from '@element-plus/icons-vue'
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
  
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const response = await userLogin(formState)
    console.log('登录API响应:', response)
    
    if (response.data?.code === 0 && response.data?.data) {
      const userData = response.data.data
      
      // 保存用户信息（session 认证不需要手动管理 token）
      userStore.setUser(userData)
      
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


</script>
<style scoped>
.login-container,.register-container {width:100%;max-width:420px;margin:auto}
.login-header,.register-header {margin-bottom:28px}
.auth-eyebrow {font-size:11px;letter-spacing:1.5px;color:var(--ui-accent);margin-bottom:12px}
h1 {font-size:29px;font-weight:600;letter-spacing:-.8px;line-height:1.3;color:var(--ui-text)}
.welcome-text {font-size:14px;color:var(--ui-muted);margin-top:10px}
.login-card,.register-card {background:#fff;border:1px solid var(--ui-border);border-radius:12px;overflow:visible}
:deep(.el-card__body) {padding:30px}
:deep(.el-form-item) {margin-bottom:22px}
:deep(.el-form-item__label) {font-size:12px;color:#4d5a50;padding-bottom:6px;line-height:1.6}
:deep(.el-input__wrapper) {min-height:44px;box-shadow:0 0 0 1px #dfe5db inset}
:deep(.el-input__inner) {font-size:14px}
.login-button,.register-button {width:100%;height:44px}
.login-button>span,.register-button>span {display:flex;align-items:center;gap:8px}
.login-footer,.register-footer {padding-top:2px;text-align:center;font-size:13px;color:var(--ui-muted)}
@media(max-width:480px) {:deep(.el-card__body) {padding:24px}h1 {font-size:26px}}
</style>
