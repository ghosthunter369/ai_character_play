<template>
  <div class="create-app">
    <a-card title="创建应用" class="create-card">
      <a-form
        :model="formState"
        :rules="rules"
        ref="formRef"
        :label-col="{ span: 4 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="应用名称" name="appName">
          <a-input v-model:value="formState.appName" placeholder="请输入应用名称" />
        </a-form-item>
        
        <a-form-item label="应用描述" name="description">
          <a-textarea
            v-model:value="formState.description"
            placeholder="请输入应用描述"
            :rows="4"
          />
        </a-form-item>
        
        <a-form-item label="应用图标" name="cover">
          <a-upload
            v-model:fileList="fileList"
            list-type="picture-card"
            :before-upload="beforeUpload"
            @preview="handlePreview"
          >
            <div v-if="fileList.length < 1">
              <plus-outlined />
              <div style="margin-top: 8px">上传图标</div>
            </div>
          </a-upload>
        </a-form-item>
        
        <a-form-item label="系统提示" name="systemPrompt">
          <a-textarea
            v-model:value="formState.systemPrompt"
            placeholder="请输入系统提示词，用于指导AI的行为"
            :rows="6"
          />
        </a-form-item>
        
        <a-form-item :wrapper-col="{ offset: 4, span: 18 }">
          <a-button type="primary" @click="handleSubmit" :loading="loading">
            创建应用
          </a-button>
          <a-button style="margin-left: 10px" @click="handleReset">
            重置
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { UploadFile } from 'ant-design-vue'
import { appService } from '@/services'

const formRef = ref()
const loading = ref(false)
const fileList = ref<UploadFile[]>([])

const formState = reactive({
  appName: '',
  description: '',
  cover: '',
  systemPrompt: ''
})

const rules = {
  appName: [
    { required: true, message: '请输入应用名称', trigger: 'blur' },
    { min: 2, max: 50, message: '应用名称长度在2-50个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入应用描述', trigger: 'blur' },
    { max: 500, message: '描述不能超过500个字符', trigger: 'blur' }
  ],
  systemPrompt: [
    { required: true, message: '请输入系统提示词', trigger: 'blur' },
    { max: 2000, message: '提示词不能超过2000个字符', trigger: 'blur' }
  ]
}

const beforeUpload = (file: File) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isJpgOrPng) {
    message.error('只能上传JPG/PNG格式的图片!')
    return false
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过2MB!')
    return false
  }
  return false // 阻止自动上传，手动处理
}

const handlePreview = (file: UploadFile) => {
  // 预览图片逻辑
  console.log('预览图片:', file)
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    loading.value = true
    
    const response = await appService.createApp(formState)
    if (response.code === 0) {
      message.success('创建应用成功')
      handleReset()
    } else {
      message.error(response.message || '创建应用失败')
    }
  } catch (error) {
    console.error('创建应用失败:', error)
    message.error('创建应用失败')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  formRef.value.resetFields()
  fileList.value = []
}
</script>

<style scoped>
.create-app {
  max-width: 800px;
  margin: 0 auto;
}

.create-card {
  border-radius: 8px;
}

:deep(.ant-upload-select-picture-card i) {
  font-size: 32px;
  color: #999;
}

:deep(.ant-upload-select-picture-card .ant-upload-text) {
  margin-top: 8px;
  color: #666;
}
</style>