<template>
  <div class="create-app-page">
    <div class="page-container">
      <div class="page-header">
        <div class="header-content">
          <h1 class="page-title">
            <el-icon class="title-icon"><Plus /></el-icon>
            创建AI应用
          </h1>
          <p class="page-subtitle">定制你的专属AI助手，开启智能对话体验</p>
        </div>
      </div>

      <div class="form-container">
        <el-card class="create-form-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><EditPen /></el-icon>
              <span>应用配置</span>
            </div>
          </template>

          <el-form
            :model="formData"
            :rules="formRules"
            ref="formRef"
            label-width="120px"
            size="large"
            @submit.prevent="handleSubmit"
          >
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="应用名称" prop="appName">
                  <el-input
                    v-model="formData.appName"
                    placeholder="请输入应用名称"
                    :prefix-icon="ChatDotRound"
                    maxlength="50"
                    show-word-limit
                  />
                </el-form-item>
              </el-col>
              
              <el-col :span="12">
                <el-form-item label="封面图片">
                  <el-input
                    v-model="formData.cover"
                    placeholder="请输入图片URL（可选）"
                    :prefix-icon="Picture"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="应用描述" prop="description">
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="3"
                placeholder="请描述你的AI应用的功能和特点..."
                maxlength="200"
                show-word-limit
              />
            </el-form-item>

            <el-form-item label="系统提示词" prop="initPrompt">
              <el-input
                v-model="formData.initPrompt"
                type="textarea"
                :rows="6"
                placeholder="请输入系统提示词，定义AI的角色和行为..."
                maxlength="2000"
                show-word-limit
              />
              <div class="form-tip">
                <el-icon><InfoFilled /></el-icon>
                系统提示词将决定AI的角色定位和回答风格，请详细描述
              </div>
            </el-form-item>

            <el-form-item label="开场白" prop="prologue">
              <el-input
                v-model="formData.prologue"
                type="textarea"
                :rows="2"
                placeholder="请输入开场白，这将是用户看到的第一条消息..."
                maxlength="500"
                show-word-limit
              />
            </el-form-item>

            <el-form-item>
              <div class="form-actions">
                <el-button @click="handleReset" size="large">
                  <el-icon><RefreshLeft /></el-icon>
                  重置
                </el-button>
                <el-button @click="handlePreview" size="large">
                  <el-icon><View /></el-icon>
                  预览
                </el-button>
                <el-button 
                  type="primary" 
                  @click="handleSubmit" 
                  :loading="loading"
                  size="large"
                >
                  <el-icon><Check /></el-icon>
                  {{ loading ? '创建中...' : '创建应用' }}
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 预览卡片 -->
        <el-card class="preview-card" shadow="hover" v-if="showPreview">
          <template #header>
            <div class="card-header">
              <el-icon><View /></el-icon>
              <span>应用预览</span>
            </div>
          </template>

          <div class="app-preview">
            <div class="preview-cover">
              <img v-if="formData.cover" :src="formData.cover" alt="封面" @error="handleImageError" />
              <div v-else class="default-cover">
                <el-icon size="48"><ChatDotRound /></el-icon>
              </div>
            </div>
            
            <div class="preview-content">
              <h3 class="preview-title">{{ formData.appName || '应用名称' }}</h3>
              <p class="preview-description">{{ formData.description || '应用描述' }}</p>
              
              <div class="preview-tags">
                <el-tag type="primary" size="small">AI助手</el-tag>
                <el-tag type="success" size="small">智能对话</el-tag>
              </div>
              
              <div class="preview-prologue" v-if="formData.prologue">
                <h4>开场白：</h4>
                <p>{{ formData.prologue }}</p>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 模板选择对话框 -->
    <el-dialog
      v-model="showTemplateDialog"
      title="选择应用模板"
      width="800px"
      :before-close="handleCloseTemplate"
    >
      <div class="template-grid">
        <div
          v-for="template in templates"
          :key="template.id"
          :class="['template-item', { selected: selectedTemplate?.id === template.id }]"
          @click="selectTemplate(template)"
        >
          <div class="template-icon">
            <el-icon size="32" :color="template.color">
              <component :is="template.icon" />
            </el-icon>
          </div>
          <h4>{{ template.name }}</h4>
          <p>{{ template.description }}</p>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="showTemplateDialog = false">取消</el-button>
        <el-button type="primary" @click="applyTemplate" :disabled="!selectedTemplate">
          应用模板
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, EditPen, ChatDotRound, Picture, InfoFilled, RefreshLeft,
  View, Check, Tools, Document, DataAnalysis, Location
} from '@element-plus/icons-vue'
import { createApp1 } from '@/api/appController'
import type { AppDTO } from '@/types/api'

const router = useRouter()

// 响应式数据
const formRef = ref()
const loading = ref(false)
const showPreview = ref(false)
const showTemplateDialog = ref(false)
const selectedTemplate = ref<any>(null)

const formData = ref<AppDTO>({
  appName: '',
  description: '',
  initPrompt: '',
  prologue: '',
  cover: ''
})

// 表单验证规则
const formRules = {
  appName: [
    { required: true, message: '请输入应用名称', trigger: 'blur' },
    { min: 2, max: 50, message: '应用名称长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入应用描述', trigger: 'blur' },
    { min: 10, max: 200, message: '应用描述长度在 10 到 200 个字符', trigger: 'blur' }
  ],
  initPrompt: [
    { required: true, message: '请输入系统提示词', trigger: 'blur' },
    { min: 20, max: 2000, message: '系统提示词长度在 20 到 2000 个字符', trigger: 'blur' }
  ],
  prologue: [
    { max: 500, message: '开场白长度不能超过 500 个字符', trigger: 'blur' }
  ]
}

// 应用模板
const templates = [
  {
    id: 1,
    name: '智能客服',
    description: '专业的客户服务助手，解答用户问题',
    icon: 'Tools',
    color: '#1890ff',
    initPrompt: '你是一个专业的客服助手，请友好、耐心地回答用户的问题。始终保持礼貌和专业的态度，如果遇到无法解决的问题，请引导用户联系人工客服。',
    prologue: '您好！我是智能客服助手，很高兴为您服务。请问有什么可以帮助您的吗？'
  },
  {
    id: 2,
    name: '学习助手',
    description: '帮助用户学习和理解各种知识',
    icon: 'Document',
    color: '#52c41a',
    initPrompt: '你是一个专业的学习助手，擅长解释复杂概念，提供学习建议。请用简单易懂的语言回答问题，并提供相关的学习资源和练习建议。',
    prologue: '你好！我是你的学习助手，可以帮你解答学习中的疑问，提供学习建议。有什么想学习的吗？'
  },
  {
    id: 3,
    name: '数据分析师',
    description: '专业的数据分析和解读助手',
    icon: 'DataAnalysis',
    color: '#722ed1',
    initPrompt: '你是一个专业的数据分析师，擅长数据分析、统计学和商业洞察。请用专业但易懂的方式解释数据趋势和分析结果。',
    prologue: '您好！我是数据分析助手，可以帮您分析数据、解读趋势、提供商业洞察。请分享您的数据或问题。'
  },
  {
    id: 4,
    name: '创意顾问',
    description: '激发创意灵感的创作助手',
    icon: 'Location',
    color: '#fa8c16',
    initPrompt: '你是一个富有创意的顾问，擅长头脑风暴、创意思考和问题解决。请提供新颖的想法和创意解决方案。',
    prologue: '嗨！我是创意顾问，专门帮助激发灵感和创意思考。有什么创意挑战需要我帮忙吗？'
  }
]

// 计算属性
const canPreview = computed(() => {
  return formData.value.appName && formData.value.description
})

// 方法
const handleSubmit = async () => {
  if (!formRef.value) return
  
  const valid = await formRef.value.validate()
  if (!valid) return

  loading.value = true
  try {
    console.log('开始创建应用，请求数据：', formData.value)
    console.log('当前 cookies：', document.cookie)
    
    const response = await createApp1(formData.value)
    console.log('创建应用响应：', response)
    
    if (response.data?.code === 0) {
      ElMessage.success('应用创建成功！')
      
      // 询问用户是否立即开始聊天
      try {
        await ElMessageBox.confirm('应用创建成功！是否立即开始聊天？', '创建成功', {
          confirmButtonText: '开始聊天',
          cancelButtonText: '查看应用',
          type: 'success'
        })
        router.push('/chat')
      } catch {
        router.push('/my-apps')
      }
    } else {
      console.error('创建失败，响应：', response)
      ElMessage.error(response.message || '创建失败')
    }
  } catch (error) {
    console.error('创建应用出错：', error)
    ElMessage.error('创建失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

const handleReset = async () => {
  try {
    await ElMessageBox.confirm('确定要重置表单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    formRef.value?.resetFields()
    formData.value = {
      appName: '',
      description: '',
      initPrompt: '',
      prologue: '',
      cover: ''
    }
    showPreview.value = false
    ElMessage.success('表单已重置')
  } catch {
    // 用户取消
  }
}

const handlePreview = () => {
  if (!canPreview.value) {
    ElMessage.warning('请先填写应用名称和描述')
    return
  }
  showPreview.value = !showPreview.value
}

const handleImageError = (event: Event) => {
  const target = event.target as HTMLImageElement
  target.style.display = 'none'
}

const selectTemplate = (template: any) => {
  selectedTemplate.value = template
}

const applyTemplate = () => {
  if (!selectedTemplate.value) return
  
  const template = selectedTemplate.value
  formData.value.initPrompt = template.initPrompt
  formData.value.prologue = template.prologue
  
  if (!formData.value.appName) {
    formData.value.appName = template.name
  }
  if (!formData.value.description) {
    formData.value.description = template.description
  }
  
  showTemplateDialog.value = false
  selectedTemplate.value = null
  ElMessage.success('模板应用成功！')
}

const handleCloseTemplate = () => {
  showTemplateDialog.value = false
  selectedTemplate.value = null
}

const showTemplates = () => {
  showTemplateDialog.value = true
}
</script>

<style scoped>
.create-app-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.page-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.header-content {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 40px;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.page-title {
  font-size: 36px;
  font-weight: 700;
  color: white;
  margin: 0 0 16px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.title-icon {
  color: #ffd700;
}

.page-subtitle {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
}

.form-container {
  display: grid;
  grid-template-columns: 1fr;
  gap: 24px;
}

.create-form-card,
.preview-card {
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
}

.form-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: #666;
}

.form-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
}

/* 预览样式 */
.app-preview {
  display: flex;
  gap: 20px;
}

.preview-cover {
  flex-shrink: 0;
  width: 120px;
  height: 120px;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.default-cover {
  color: white;
}

.preview-content {
  flex: 1;
}

.preview-title {
  font-size: 20px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 8px 0;
}

.preview-description {
  color: #666;
  line-height: 1.5;
  margin: 0 0 12px 0;
}

.preview-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.preview-prologue h4 {
  font-size: 14px;
  color: #2c3e50;
  margin: 0 0 8px 0;
}

.preview-prologue p {
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 8px;
  border-left: 4px solid #667eea;
}

/* 模板选择样式 */
.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.template-item {
  padding: 20px;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
}

.template-item:hover {
  border-color: #667eea;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

.template-item.selected {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.1);
}

.template-icon {
  margin-bottom: 12px;
}

.template-item h4 {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 8px 0;
}

.template-item p {
  font-size: 12px;
  color: #666;
  margin: 0;
  line-height: 1.4;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .form-container {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .create-app-page {
    padding: 16px;
  }
  
  .header-content {
    padding: 24px;
  }
  
  .page-title {
    font-size: 28px;
  }
  
  .page-subtitle {
    font-size: 16px;
  }
  
  .app-preview {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  
  .preview-cover {
    width: 100px;
    height: 100px;
  }
  
  .form-actions {
    flex-direction: column;
  }
  
  .template-grid {
    grid-template-columns: 1fr;
  }
}

/* 表单样式增强 */
:deep(.el-form-item__label) {
  font-weight: 600;
  color: #2c3e50;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

:deep(.el-textarea__inner) {
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

:deep(.el-textarea__inner:hover) {
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.3s ease;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
}

:deep(.el-button--primary:hover) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.4);
}
</style>