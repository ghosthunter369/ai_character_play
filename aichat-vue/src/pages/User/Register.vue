<template>
  <a-card title="注册" class="register-card">
    <a-form
      :model="formState"
      name="register"
      @finish="onFinish"
      @finishFailed="onFinishFailed"
    >
      <a-form-item
        label="用户名"
        name="username"
        :rules="[{ required: true, message: '请输入用户名!' }]"
      >
        <a-input v-model:value="formState.username" />
      </a-form-item>

      <a-form-item
        label="邮箱"
        name="email"
        :rules="[
          { required: true, message: '请输入邮箱!' },
          { type: 'email', message: '请输入有效的邮箱地址!' }
        ]"
      >
        <a-input v-model:value="formState.email" />
      </a-form-item>

      <a-form-item
        label="密码"
        name="password"
        :rules="[{ required: true, message: '请输入密码!' }]"
      >
        <a-input-password v-model:value="formState.password" />
      </a-form-item>

      <a-form-item
        label="确认密码"
        name="confirmPassword"
        :rules="[
          { required: true, message: '请确认密码!' },
          { validator: validateConfirmPassword }
        ]"
      >
        <a-input-password v-model:value="formState.confirmPassword" />
      </a-form-item>

      <a-form-item>
        <a-button type="primary" html-type="submit" :loading="loading" block>
          注册
        </a-button>
      </a-form-item>

      <div class="login-link">
        已有账号？<a @click="goToLogin">立即登录</a>
      </div>
    </a-form>
  </a-card>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

interface FormState {
  username: string
  email: string
  password: string
  confirmPassword: string
}

const router = useRouter()
const loading = ref(false)
const formState = reactive<FormState>({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_: any, value: string) => {
  if (value && value !== formState.password) {
    return Promise.reject(new Error('两次输入的密码不一致!'))
  }
  return Promise.resolve()
}

const onFinish = async (values: FormState) => {
  loading.value = true
  try {
    // 模拟注册逻辑
    await new Promise(resolve => setTimeout(resolve, 1000))
    message.success('注册成功')
    router.push('/user/login')
  } catch (error) {
    message.error('注册失败')
  } finally {
    loading.value = false
  }
}

const onFinishFailed = (errorInfo: any) => {
  console.log('Failed:', errorInfo)
}

const goToLogin = () => {
  router.push('/user/login')
}
</script>

<style scoped>
.register-card {
  width: 400px;
  margin: 0 auto;
}

.login-link {
  text-align: center;
  margin-top: 16px;
}
</style>