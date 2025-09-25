<template>
  <div class="my-apps">
    <div class="page-header">
      <h1>我的应用</h1>
      <p>管理您创建的AI聊天应用</p>
    </div>
    
    <a-card>
      <a-table
        :dataSource="apps"
        :columns="columns"
        :pagination="pagination"
        :loading="loading"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'operation'">
            <a-button type="link" @click="handleEdit(record)">编辑</a-button>
            <a-button type="link" danger @click="handleDelete(record)">删除</a-button>
          </template>
          
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'red'">
              {{ record.status === 1 ? '正常' : '禁用' }}
            </a-tag>
          </template>
          
          <template v-else-if="column.key === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { appService } from '@/services'
import type { AppVO } from '@/types/api'

const apps = ref<AppVO[]>([])
const loading = ref(false)
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100']
})

const columns = [
  {
    title: '应用名称',
    dataIndex: 'appName',
    key: 'appName'
  },
  {
    title: '描述',
    dataIndex: 'description',
    key: 'description',
    ellipsis: true
  },
  {
    title: '状态',
    key: 'status',
    width: 100
  },
  {
    title: '创建时间',
    key: 'createTime',
    width: 150
  },
  {
    title: '操作',
    key: 'operation',
    width: 150
  }
]

const loadMyApps = async () => {
  loading.value = true
  try {
    const response = await appService.listMyApps({
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize
    })
    if (response.code === 0) {
      apps.value = response.data.records
      pagination.value.total = response.data.total
    }
  } catch (error) {
    message.error('加载应用列表失败')
  } finally {
    loading.value = false
  }
}

const handleEdit = (app: AppVO) => {
  // 编辑应用逻辑
  console.log('编辑应用:', app)
}

const handleDelete = (app: AppVO) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除应用"${app.appName}"吗？`,
    onOk: async () => {
      try {
        const response = await appService.deleteApp(app.appId)
        if (response.code === 0) {
          message.success('删除应用成功')
          loadMyApps()
        } else {
          message.error(response.message || '删除应用失败')
        }
      } catch (error) {
        message.error('删除应用失败')
      }
    }
  })
}

const handleTableChange = (pag: any) => {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  loadMyApps()
}

const formatTime = (time: string) => {
  return new Date(time).toLocaleString()
}

onMounted(() => {
  loadMyApps()
})
</script>

<style scoped>
.my-apps {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 28px;
  margin-bottom: 8px;
  color: #262626;
}

.page-header p {
  font-size: 14px;
  color: #8c8c8c;
}
</style>