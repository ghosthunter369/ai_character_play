<template>
  <article class="role-card">
    <div class="role-heading">
      <div class="role-portrait">
        <img v-if="app.cover && !imageFailed" :src="app.cover" :alt="app.appName || '角色头像'" @error="imageFailed = true">
        <span v-else>{{ app.appName?.slice(0, 1) || '角' }}</span>
      </div>
      <span v-if="curated" class="role-badge">精选角色</span>
    </div>
    <h2>{{ app.appName || '未命名角色' }}</h2>
    <p>{{ app.description || '选择这个角色，开始一段对话。' }}</p>
    <div class="role-footer">
      <span>文字 / 语音</span>
      <el-button :aria-label="`与${app.appName || '角色'}开始对话`" @click="emit('start')">
        开始对话 <span aria-hidden="true" class="arrow">↗</span>
      </el-button>
    </div>
  </article>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ app: { appName?: string; description?: string; cover?: string }; curated?: boolean }>()
const emit = defineEmits<{ start: [] }>()
const imageFailed = ref(false)
watch(() => props.app.cover, () => { imageFailed.value = false })
</script>
<style scoped>
.role-card { background: var(--ui-surface); border: 1px solid var(--ui-border); border-radius: 12px; padding: 26px; display: flex; flex-direction: column; min-width: 0; transition: border-color .15s, box-shadow .15s; }
.role-card:hover { border-color: #abbba7; box-shadow: 0 4px 16px rgb(40 51 46 / 4%); }
.role-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; gap: 12px; }
.role-portrait { height: 64px; width: 64px; overflow: hidden; border-radius: 50%; background: #eef1e8; display: grid; place-items: center; color: var(--ui-accent); font-size: 25px; flex-shrink: 0; }
.role-portrait img { width: 100%; height: 100%; object-fit: cover; }
.role-badge { color: #5b7157; font-size: 11px; background: var(--ui-tint); padding: 3px 9px; border-radius: 5px; }
h2 { font-size: 19px; font-weight: 600; letter-spacing: -.3px; overflow-wrap: anywhere; }
p { font-size: 14px; line-height: 1.8; color: var(--ui-muted); margin: 10px 0 28px; overflow-wrap: anywhere; }
.role-footer { margin-top: auto; padding-top: 18px; border-top: 1px solid #eef0eb; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.role-footer > span { color: var(--ui-muted); font-size: 12px; }
.role-footer .el-button { border-color: #d8e1d3; background: #f7f9f4; color: #3d5843; }
.arrow { padding-left: 10px; font-size: 16px; }
@media(max-width: 480px) { .role-card { padding: 22px; } }
</style>
