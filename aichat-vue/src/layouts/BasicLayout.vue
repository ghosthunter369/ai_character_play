<template>
 <div class="basic-layout">
  <header class="workspace-header">
   <div class="header-content">
    <router-link to="/featured-apps" class="brand" aria-label="Character 首页"><span class="brand-mark" aria-hidden="true">c.</span><span>Character</span></router-link>
    <nav aria-label="主导航" class="workspace-nav">
     <router-link to="/featured-apps">精选角色</router-link>
     <router-link to="/app-square">发现角色</router-link>
     <router-link to="/my-apps">我的角色</router-link>
     <router-link to="/chat">对话</router-link>
    </nav>
    <div class="account-actions">
     <el-button v-if="!userStore.user" @click="router.push('/user/login')">登录</el-button>
     <el-dropdown v-else @command="handleUserCommand" trigger="click">
      <button class="account-button" aria-label="账户菜单"><el-avatar :size="28" :src="userStore.user?.userAvatar"><el-icon><User /></el-icon></el-avatar><span>{{ userStore.user?.userName || userStore.user?.userAccount }}</span><el-icon><ArrowDown /></el-icon></button>
      <template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
     </el-dropdown>
    </div>
   </div>
  </header>
  <main class="workspace-main"><router-view /></main>
 </div>
</template>
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { userLogout } from '@/api/userController'
const router = useRouter()
const userStore = useUserStore()
async function handleUserCommand(command: string) {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('退出后可以随时重新登录。', '退出登录', {
      confirmButtonText: '退出登录', cancelButtonText: '取消'
    })
  } catch { return }
  try {
    const response = await userLogout()
    if (response.data?.code !== 0) {
      ElMessage.error(response.data?.message || '退出失败，请重试')
      return
    }
    userStore.clearUser()
    router.push('/user/login')
  } catch { ElMessage.error('退出失败，请检查网络后重试') }
}
</script>
<style scoped>
.basic-layout {min-height:100dvh; background:var(--ui-bg)}
.workspace-header {height:var(--shell-header-height); background:#fff; border-bottom:1px solid var(--ui-border); position:sticky; top:0; z-index:100}
.header-content {height:100%;max-width:1440px;margin:auto;padding:0 36px;display:grid;grid-template-columns:200px 1fr 200px;align-items:center;gap:24px}
.brand {display:flex;align-items:center;gap:10px;font-weight:650;font-size:17px;letter-spacing:-.5px;width:fit-content}
.brand-mark {width:29px;height:29px;display:grid;place-items:center;background:#eaf0e5;color:#3b5641;border:1px solid #dce5d6;border-radius:8px;font-family:Georgia,serif;font-size:24px;line-height:1}
.workspace-nav {display:flex;align-items:center;justify-content:center;gap:6px;min-width:0;height:100%}
.workspace-nav a {padding:7px 14px;border-radius:6px;color:var(--ui-muted);font-size:13px;white-space:nowrap}
.workspace-nav a:hover {background:var(--ui-bg);color:var(--ui-text)}
.workspace-nav a.router-link-active {color:var(--ui-accent);background:var(--ui-tint);font-weight:600}
.account-actions {display:flex;justify-content:flex-end;min-width:0}
.account-button {background:transparent;border:0;display:flex;align-items:center;gap:8px;cursor:pointer;color:var(--ui-text);font-size:12px;max-width:190px;padding:4px;border-radius:6px}
.account-button>span {max-width:105px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.workspace-main {min-width:0}
@media(max-width:1000px) {.header-content {grid-template-columns:auto 1fr auto;padding:0 24px;gap:16px}.workspace-nav a {padding:7px 10px}}
@media(max-width:760px) {.header-content {grid-template-columns:1fr auto;grid-template-rows:60px 48px;gap:0;padding:0 20px}.brand {font-size:16px}.workspace-nav {grid-column:1/-1;grid-row:2;justify-content:flex-start;gap:4px;overflow:auto}.workspace-nav a {padding:6px 12px}.account-actions {grid-column:2;grid-row:1}.account-button>span {max-width:82px}}
</style>
