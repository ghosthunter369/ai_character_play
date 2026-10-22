<template>
  <div class="my-apps-page catalog-page">
    <div class="page-container">
      <!-- 页面头部 -->
      <div class="page-header">
        <div class="header-content">
          <div class="header-left">
            <h1 class="page-title">
              <el-icon class="title-icon"><Folder /></el-icon>
              我的角色
            </h1>
            <p class="page-subtitle">管理你创建的角色和对话设定。</p>
          </div>
          <div class="header-right">
            <el-button type="primary" size="large" @click="goToCreate">
              <el-icon><Plus /></el-icon>
              创建角色
            </el-button>
          </div>
        </div>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-section">
        <el-row :gutter="24">
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-content">
                <div class="stat-icon total">
                  <el-icon size="24"><Collection /></el-icon>
                </div>
                <div class="stat-info">
                  <div class="stat-number">{{ total }}</div>
                  <div class="stat-label">全部角色</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-content">
                <div class="stat-icon active">
                  <el-icon size="24"><ChatLineRound /></el-icon>
                </div>
                <div class="stat-info">
                  <div class="stat-number">{{ apps.length }}</div>
                  <div class="stat-label">本页角色</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-content">
                <div class="stat-icon featured">
                  <el-icon size="24"><Star /></el-icon>
                </div>
                <div class="stat-info">
                  <div class="stat-number">{{ featuredApps }}</div>
                  <div class="stat-label">本页精选</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-content">
                <div class="stat-icon recent">
                  <el-icon size="24"><Clock /></el-icon>
                </div>
                <div class="stat-info">
                  <div class="stat-number">{{ recentApps }}</div>
                  <div class="stat-label">本页近月新增</div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 搜索和筛选 -->
      <div class="filter-section">
        <el-row :gutter="16" align="middle">
          <el-col :span="8">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索角色名称..."
              :prefix-icon="Search"
              @input="handleSearch"
            />
          </el-col>
          <el-col :span="4">
            <el-select v-model="sortBy" placeholder="排序方式" @change="handleSort">
              <el-option label="创建时间" value="createTime" />
              <el-option label="修改时间" value="editTime" />
              <el-option label="角色名称" value="appName" />
            </el-select>
          </el-col>
          <el-col :span="4">
            <el-select v-model="filterStatus" placeholder="状态筛选" @change="handleFilter">
              <el-option label="全部" value="" />
              <el-option label="精选" value="featured" />
              <el-option label="普通" value="normal" />
            </el-select>
          </el-col>
          <el-col :span="8">
            <div class="view-controls">
              <el-button-group>
                <el-button
                  :type="viewMode === 'grid' ? 'primary' : 'default'"
                  @click="viewMode = 'grid'"
                >
                  <el-icon><Grid /></el-icon>
                </el-button>
                <el-button
                  :type="viewMode === 'list' ? 'primary' : 'default'"
                  @click="viewMode = 'list'"
                >
                  <el-icon><List /></el-icon>
                </el-button>
              </el-button-group>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 应用列表 -->
      <div class="apps-section" v-loading="loading">
        <!-- 网格视图 -->
        <div v-if="viewMode === 'grid'" class="apps-grid">
          <transition-group name="app-item" tag="div" class="grid-container">
            <div
              v-for="app in apps"
              :key="app.id || app.appId"
              class="app-card"
            >
              <div class="card-cover">
                <img v-if="app.cover" :src="app.cover" :alt="app.appName" @error="handleImageError" />
                <div v-else class="default-cover">
                  <el-icon size="48"><ChatDotRound /></el-icon>
                </div>
                <div class="card-overlay">
                  <el-button type="primary" @click="startChat(app)" round>
                    <el-icon><ChatLineRound /></el-icon>
                    开始聊天
                  </el-button>
                </div>
                <el-tag v-if="app.priority > 0" class="featured-tag" type="warning" size="small">
                  精选
                </el-tag>
              </div>

              <div class="card-content">
                <div class="card-header">
                  <h3 class="app-name">{{ app.appName }}</h3>
                  <el-dropdown @command="(command) => handleAppAction(command, app)">
                    <el-button circle size="small">
                      <el-icon><MoreFilled /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="edit">
                          <el-icon><Edit /></el-icon>
                          编辑
                        </el-dropdown-item>
                        <el-dropdown-item command="copy">
                          <el-icon><CopyDocument /></el-icon>
                          复制
                        </el-dropdown-item>
                        <el-dropdown-item command="export">
                          <el-icon><Download /></el-icon>
                          导出
                        </el-dropdown-item>
                        <el-dropdown-item command="delete" divided>
                          <el-icon><Delete /></el-icon>
                          删除
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>

                <p class="app-description">{{ app.description || '暂无描述' }}</p>

                <div class="card-footer">
                  <div class="app-stats">
                    <span class="stat-item">
                      <el-icon><Clock /></el-icon>
                      {{ formatTime(app.editTime || app.createTime) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </transition-group>
        </div>

        <!-- 列表视图 -->
        <div v-else class="apps-list">
          <el-table :data="apps" style="width: 100%" @row-click="startChat">
            <el-table-column width="80">
              <template #default="{ row }">
                <el-avatar :size="48" :src="row.cover">
                  <el-icon><ChatDotRound /></el-icon>
                </el-avatar>
              </template>
            </el-table-column>

            <el-table-column prop="appName" label="角色名称" min-width="150">
              <template #default="{ row }">
                <div class="app-name-cell">
                  <span class="name">{{ row.appName }}</span>
                  <el-tag v-if="row.priority > 0" type="warning" size="small">精选</el-tag>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />

            <el-table-column label="创建时间" width="150">
              <template #default="{ row }">
                {{ formatTime(row.createTime) }}
              </template>
            </el-table-column>

            <el-table-column label="最后修改" width="150">
              <template #default="{ row }">
                {{ formatTime(row.editTime || row.createTime) }}
              </template>
            </el-table-column>

            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click.stop="startChat(row)">
                  聊天
                </el-button>
                <el-dropdown @command="(command) => handleAppAction(command, row)" @click.stop>
                  <el-button size="small">
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="copy">复制</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 空状态 -->
        <el-empty v-if="!loading && apps.length === 0" description="还没有创建任何应用">
          <el-button type="primary" @click="goToCreate">创建第一个角色</el-button>
        </el-empty>
      </div>

      <!-- 分页 -->
      <div class="pagination-section" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[12, 24, 48, 96]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 编辑应用对话框 -->
    <el-dialog
      v-model="showEditDialog"
      title="编辑应用"
      width="600px"
      :before-close="handleCloseEdit"
    >
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-form-item label="角色名称" prop="appName">
          <el-input v-model="editForm.appName" placeholder="请输入角色名称" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateApp" :loading="updateLoading">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Folder, Plus, Collection, ChatLineRound, Star, Clock, Search,
  Grid, List, ChatDotRound, MoreFilled, Edit, CopyDocument,
  Download, Delete
} from '@element-plus/icons-vue'
import { listMyAppVoByPage, deleteApp, updateApp, getAppVoById } from '@/api/appController'
import type { AppVO, AppUpdateRequest } from '@/types/api'

const router = useRouter()

// 响应式数据
const apps = ref<AppVO[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)
const searchKeyword = ref('')
const sortBy = ref('createTime')
const filterStatus = ref('')
const viewMode = ref<'grid' | 'list'>('grid')

// 编辑相关
const showEditDialog = ref(false)
const updateLoading = ref(false)
const editFormRef = ref()
const editForm = ref<AppUpdateRequest>({
  appId: 0,
  appName: ''
})

const editRules = {
  appName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' }
  ]
}

// 计算属性

const featuredApps = computed(() => {
  return apps.value.filter(app => app.priority > 0).length
})

const recentApps = computed(() => {
  const oneMonthAgo = new Date()
  oneMonthAgo.setMonth(oneMonthAgo.getMonth() - 1)
  return apps.value.filter(app => new Date(app.createTime) > oneMonthAgo).length
})

// 方法
const loadApps = async () => {
  loading.value = true
  try {
    const response = await listMyAppVoByPage({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      appName: searchKeyword.value || undefined,
      sortField: 'create_time',
      sortOrder: 'descend'
    })
    if (response.data?.code === 0) {
      apps.value = response.data?.data?.records || []
      total.value = response.data?.data?.total || 0
    }
  } catch (error) {
    ElMessage.error('加载应用列表失败')
  } finally {
    loading.value = false
  }
}

const goToCreate = () => {
  router.push('/create-app')
}

const startChat = async (app: AppVO) => {
  try {
    // 先获取应用详情
    const response = await getAppVoById({ id: app.id || app.appId })
    if (response.data?.code === 0) {
      const appDetail = response.data.data
      // 跳转到聊天页面，传递应用详情
      router.push({
        path: '/chat',
        query: {
          appId: appDetail.id || appDetail.appId,
          appName: appDetail.appName,
          initPrompt: appDetail.initPrompt,
          prologue: appDetail.prologue
        }
      })
      ElMessage.success(`开始与 ${appDetail.appName} 对话`)
    } else {
      ElMessage.error('获取应用信息失败')
    }
  } catch (error) {
    console.error('获取应用详情失败:', error)
    ElMessage.error('获取应用信息失败，请稍后重试')
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadApps()
}

const handleSort = () => {
  // 实现排序逻辑
  loadApps()
}

const handleFilter = () => {
  // 实现筛选逻辑
  loadApps()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  loadApps()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadApps()
}

const handleAppAction = async (command: string, app: AppVO) => {
  switch (command) {
    case 'edit':
      editForm.value = {
        appId: app.id || app.appId,
        appName: app.appName
      }
      showEditDialog.value = true
      break
    case 'copy':
      await copyApp(app)
      break
    case 'export':
      exportApp(app)
      break
    case 'delete':
      await deleteAppAction(app)
      break
  }
}

const handleUpdateApp = async () => {
  if (!editFormRef.value) return

  const valid = await editFormRef.value.validate()
  if (!valid) return

  updateLoading.value = true
  try {
    const response = await updateApp(editForm.value)
    if (response.data?.code === 0) {
      ElMessage.success('更新成功')
      showEditDialog.value = false
      loadApps()
    }
  } catch (error) {
    ElMessage.error('更新失败')
  } finally {
    updateLoading.value = false
  }
}

const handleCloseEdit = () => {
  showEditDialog.value = false
  editFormRef.value?.resetFields()
}

const copyApp = async (app: AppVO) => {
  try {
    await ElMessageBox.confirm('确定要复制这个应用吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })

    // 这里实现复制逻辑
    ElMessage.success('复制功能开发中...')
  } catch {
    // 用户取消
  }
}

const exportApp = (app: AppVO) => {
  const appData = {
    appName: app.appName,
    description: app.description,
    initPrompt: app.initPrompt,
    prologue: app.prologue,
    cover: app.cover
  }

  const blob = new Blob([JSON.stringify(appData, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${app.appName}.json`
  a.click()
  URL.revokeObjectURL(url)

  ElMessage.success('应用配置已导出')
}

const deleteAppAction = async (app: AppVO) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除应用"${app.appName}"吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error'
      }
    )

    const response = await deleteApp({ id: app.id || app.appId })
    if (response.data?.code === 0) {
      ElMessage.success('删除成功')
      loadApps()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleImageError = (event: Event) => {
  const target = event.target as HTMLImageElement
  target.style.display = 'none'
}

const formatTime = (time: string) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    return '今天'
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString()
  }
}


// 生命周期
onMounted(() => {
  loadApps()
})
</script>
