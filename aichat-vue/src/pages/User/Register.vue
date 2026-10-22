<template>
  <div class="register-container">
    <div class="register-content">
      <div class="register-header"><p class="auth-eyebrow">YOUR CONVERSATION SPACE</p><h1>创建账号</h1><p class="welcome-text">选一个喜欢的角色，从第一个问题开始。</p></div>

      <el-card class="register-card" shadow="never">
        <el-form
            :model="formState"
            :rules="rules"
            ref="registerFormRef"
            @submit.prevent="onFinish"
            size="large"
          label-position="top"
        >
          <el-form-item prop="userAccount" label="账号">
            <el-input
                v-model="formState.userAccount"
                placeholder="请输入用户名"
                class="register-input"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="email" label="邮箱">
            <el-input
                v-model="formState.email"
                placeholder="请输入邮箱"
                class="register-input"
            >
              <template #prefix>
                <el-icon><Message /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="userPassword" label="密码">
            <el-input
                v-model="formState.userPassword"
                type="password"
                placeholder="请输入密码"
                class="register-input"
                show-password
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="checkPassword" label="确认密码">
            <el-input
                v-model="formState.checkPassword"
                type="password"
                placeholder="请确认密码"
                class="register-input"
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
                class="register-button"
                @click="onFinish"
            >
              <span v-if="!loading">
                <el-icon><Check /></el-icon>
                立即注册
              </span>
              <span v-else>注册中...</span>
            </el-button>
          </el-form-item>
        </el-form>

        <div class="register-footer">
          <div class="login-link">
            已有账号？
            <el-button type="primary" link @click="goToLogin">
              立即登录
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
import { User, Lock, Check, Message } from '@element-plus/icons-vue'
import { userRegister } from "@/api/userController"

interface FormState {
  userAccount: string
  email: string
  userPassword: string
  checkPassword: string
}

const router = useRouter()
const loading = ref(false)
const registerFormRef = ref<FormInstance>()
const formState = reactive<FormState>({
  userAccount: '',
  email: '',
  userPassword: '',
  checkPassword: ''
})

const validateConfirmPassword = (_: any, value: string, callback: any) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入的密码不一致!'))
  } else {
    callback()
  }
}

const rules = reactive<FormRules>({
  userAccount: [  // 修复：将username改为userAccount
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, message: '用户名长度不能少于3位', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  userPassword: [  // 修复：将password改为userPassword
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  checkPassword: [  // 修复：将confirmPassword改为checkPassword
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
})

const onFinish = async () => {
  if (!registerFormRef.value) return

  try {
    // 验证表单
    const valid = await registerFormRef.value.validate().catch(() => false)
    if (!valid) return

    loading.value = true

    // 修复：正确调用API并等待响应
    const response = await userRegister(formState)

    if (response.data?.code === 0) {
      ElMessage.success('注册成功！')
      // 修复：添加延迟让用户看到成功消息
      setTimeout(() => {
        router.push('/user/login')
      }, 1000)
    } else {
      ElMessage.error(response.data?.message || '注册失败，请重试')
    }
  } catch (error: any) {
    console.error('注册错误:', error)
    ElMessage.error(error.response?.data?.message || '注册失败，请重试')
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  router.push('/user/login')
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
