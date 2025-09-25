<template>
  <div class="app-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>应用管理</span>
          <el-button type="primary" size="small" @click="handleAdd">新增应用</el-button>
        </div>
      </template>
      
      <el-table :data="appList" stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="appId" label="ID" width="80" />
        <el-table-column prop="appName" label="应用名称" />
        <el-table-column prop="description" label="应用描述" />
        <el-table-column prop="userName" label="创建者" width="120" />
        <el-table-column prop="priority" label="优先级" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.priority === 99 ? 'success' : 'info'">
              {{ scope.row.priority === 99 ? '精选' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
            <el-button size="small" type="warning" @click="handleSetPriority(scope.row)" v-if="scope.row.priority !== 99">
              设为精选
            </el-button>
            <el-button size="small" type="danger" @click="handleCancelPriority(scope.row)" v-else>
              取消精选
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { appService } from '@/services'
import type { AppVO, AppQueryRequest, PageWrapper } from '@/types/api'

const loading = ref(false)
const appList = ref<AppVO[]>([])
const total = ref(0)

const queryParams = reactive<AppQueryRequest>({
  pageNum: 1,
  pageSize: 10
})

// 加载应用列表
const loadAppList = async () => {
  try {
    loading.value = true
    const response = await appService.listAllApps(queryParams)
    if (response.code === 0) {
      const pageData = response.data as PageWrapper<AppVO>
      appList.value = pageData.records
      total.value = pageData.total
    } else {
      ElMessage.error(response.message || '获取应用列表失败')
    }
  } catch (error) {
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 分页大小改变
const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadAppList()
}

// 当前页改变
const handleCurrentChange = (page: number) => {
  queryParams.pageNum = page
  loadAppList()
}

// 新增应用
const handleAdd = () => {
  ElMessage.info('新增应用功能开发中')
}

// 编辑应用
const handleEdit = (app: AppVO) => {
  ElMessage.info(`编辑应用: ${app.appName}`)
}

// 删除应用
const handleDelete = async (app: AppVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除应用 "${app.appName}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await appService.deleteAppByAdmin({ id: app.appId })
    if (response.code === 0) {
      ElMessage.success('删除成功')
      loadAppList()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    ElMessage.info('取消删除')
  }
}

// 设为精选应用
const handleSetPriority = async (app: AppVO) => {
  try {
    const response = await appService.setPriorityApp(app.appId)
    if (response.code === 0) {
      ElMessage.success('设为精选成功')
      loadAppList()
    } else {
      ElMessage.error(response.message || '设置失败')
    }
  } catch (error) {
    ElMessage.error('网络错误，请稍后重试')
  }
}

// 取消精选应用
const handleCancelPriority = async (app: AppVO) => {
  try {
    const response = await appService.cancelPriorityApp(app.appId)
    if (response.code === 0) {
      ElMessage.success('取消精选成功')
      loadAppList()
    } else {
      ElMessage.error(response.message || '取消失败')
    }
  } catch (error) {
    ElMessage.error('网络错误，请稍后重试')
  }
}

onMounted(() => {
  loadAppList()
})
</script>

<style scoped>
.app-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>