<template>
  <div class="user-admin">
    <a-card title="用户管理">
      <a-table
        :columns="columns"
        :data-source="data"
        :pagination="pagination"
        :loading="loading"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small">编辑</a-button>
              <a-button type="link" size="small" danger>删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'

interface User {
  key: string
  username: string
  email: string
  status: string
  createTime: string
}

const loading = ref(false)
const data = ref<User[]>([
  {
    key: '1',
    username: 'admin',
    email: 'admin@example.com',
    status: '正常',
    createTime: '2024-01-01'
  }
])

const columns = [
  {
    title: '用户名',
    dataIndex: 'username',
    key: 'username'
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    key: 'email'
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status'
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime'
  },
  {
    title: '操作',
    key: 'action'
  }
]

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 1
})
</script>

<style scoped>
.user-admin {
  padding: 20px;
}
</style>