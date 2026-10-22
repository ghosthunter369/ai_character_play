<template>
 <div class="user-layout">
  <header class="auth-header"><router-link to="/featured-apps" class="auth-brand"><span aria-hidden="true">c.</span>Character</router-link><router-link to="/featured-apps" class="browse-link">先看看角色 ↗</router-link></header>
  <main class="auth-main"><router-view /></main>
  <footer>AI Character Play <span>·</span> 角色对话工作台</footer>
 </div>
</template>
<style scoped>
.user-layout {min-height:100dvh;display:flex;flex-direction:column;background:var(--ui-bg)}
.auth-header {display:flex;align-items:center;justify-content:space-between;padding:28px 40px;gap:20px}
.auth-brand {display:flex;align-items:center;gap:10px;font-size:17px;font-weight:650;letter-spacing:-.5px}
.auth-brand>span {display:grid;place-items:center;width:30px;height:30px;background:#e4ecdf;border:1px solid #d7e1d0;border-radius:8px;font:24px Georgia,serif;color:var(--ui-accent)}
.browse-link {font-size:12px;color:var(--ui-muted)}
.auth-main {flex:1;display:grid;align-items:center;padding:44px 24px 64px}
footer {padding:24px;text-align:center;color:#727c74;font-size:11px;letter-spacing:.3px}footer span {margin:0 8px}
@media(max-width:600px) {.auth-header {padding:24px}.auth-main {padding:24px 20px 40px}}
</style>
