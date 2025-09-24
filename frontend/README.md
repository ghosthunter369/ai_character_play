# Vue Realtime Voice Chat Frontend

基于Vue 3 + TypeScript + Vite的实时语音对话前端应用。

## 技术栈

- **Vue 3** - 渐进式JavaScript框架
- **TypeScript** - 类型安全的JavaScript超集
- **Vite** - 快速的前端构建工具
- **Element Plus** - Vue 3 UI组件库
- **Pinia** - Vue状态管理库
- **ESLint + Prettier** - 代码质量和格式化工具

## 项目结构

```
src/
├── components/          # Vue组件
├── composables/         # 组合式API逻辑
├── services/           # 服务层
├── stores/             # Pinia状态管理
├── types/              # TypeScript类型定义
├── utils/              # 工具函数和常量
├── App.vue             # 根组件
└── main.ts             # 应用入口
```

## 开发命令

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 类型检查
npm run type-check

# 代码检查和修复
npm run lint

# 格式化代码
npm run format

# 构建生产版本
npm run build

# 预览生产构建
npm run preview
```

## 环境配置

项目使用环境变量进行配置：

- `VITE_APP_TITLE` - 应用标题
- `VITE_API_BASE_URL` - API基础URL
- `VITE_WS_BASE_URL` - WebSocket基础URL

## 功能特性

- 🎤 实时语音识别
- 💬 流式文本生成
- 🔊 语音合成播放
- 🌐 WebSocket和SSE通信
- 📱 响应式UI设计
- 🎨 Element Plus组件库

## 开发说明

本项目采用组合式API（Composition API）开发模式，使用TypeScript提供类型安全。状态管理使用Pinia，UI组件使用Element Plus。

项目结构遵循Vue 3最佳实践，代码组织清晰，便于维护和扩展。