<template>
  <div class="login-container">
    <el-card class="login-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="login-title">登录 AI Chat</span>
        </div>
      </template>
      
      <el-form
        :model="formState"
        :rules="rules"
        ref="loginFormRef"
        @submit.prevent="onFinish"
      >
        <el-form-item prop="userAccount">
          <el-input
            v-model="formState.userAccount"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="userPassword">
          <el-input
            v-model="formState.userPassword"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button 
            type="primary" 
            :loading="loading" 
            size="large" 
            style="width: 100%;"
            @click="onFinish"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>

        <div class="register-link">
          没有账号？<el-link type="primary" @click="goToRegister">立即注册</el-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { userService } from '@/services'

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
    const response = await userService.login(formState)
    if (response.code === 0) {
      userStore.setUser(response.data)
      userStore.setToken('token-placeholder') // 实际项目中应从响应中获取token
      ElMessage.success('登录成功')
      router.push('/app-square')
    } else {
      ElMessage.error(response.message || '登录失败')
    }
  } catch (error) {
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
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  border-radius: 8px;
}

.card-header {
  text-align: center;
}

.login-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.register-link {
  text-align: center;
  margin-top: 16px;
  color: #666;
}
</style>