# AI Chat Vue 前端项目

基于 Vue 3 + TypeScript + Vite + Ant Design Vue 的 AI 聊天应用前端。

## 项目特性

- ✅ **现代化技术栈**: Vue 3 + TypeScript + Vite
- ✅ **UI框架**: Ant Design Vue
- ✅ **状态管理**: Pinia
- ✅ **路由管理**: Vue Router 4
- ✅ **HTTP客户端**: Axios
- ✅ **实时通信**: Server-Sent Events (SSE)

## 项目结构

```
src/
├── assets/          # 静态资源
├── components/      # 公共组件
├── layouts/         # 布局组件
│   ├── BasicLayout.vue    # 基础布局
│   └── UserLayout.vue     # 用户认证布局
├── pages/           # 页面组件
│   ├── Welcome.vue        # 欢迎页
│   ├── Chat.vue           # AI聊天页面
│   ├── User/              # 用户相关页面
│   │   ├── Login.vue      # 登录页
│   │   └── Register.vue   # 注册页
│   ├── Admin/             # 管理页面
│   │   └── User.vue       # 用户管理
│   └── 404.vue            # 404页面
├── router/          # 路由配置
│   ├── index.ts           # 路由实例
│   └── routes.ts          # 路由定义
├── services/        # API服务层
│   ├── api.ts             # HTTP客户端配置
│   ├── userService.ts     # 用户服务
│   ├── appService.ts      # 应用服务
│   ├── chatService.ts     # 聊天服务
│   └── index.ts           # 服务导出
├── stores/          # 状态管理
│   ├── user.ts            # 用户状态
│   └── index.ts           # store导出
├── types/           # TypeScript类型定义
│   └── api.ts             # API接口类型
└── main.ts          # 应用入口
```

## API接口对应

### 用户管理接口
- `POST /user/register` - 用户注册
- `POST /user/login` - 用户登录
- `GET /user/get/login` - 获取当前用户
- `POST /user/logout` - 用户退出
- `POST /user/add` - 添加用户（管理员）
- `GET /user/get/vo` - 获取用户详情
- `POST /user/delete` - 删除用户
- `POST /user/update` - 更新用户
- `POST /user/list/page/vo` - 分页用户列表

### 应用管理接口
- `POST /app/create` - 创建应用
- `POST /app/delete` - 删除应用
- `POST /app/update` - 更新应用
- `GET /app/get/vo` - 获取应用详情
- `POST /app/my/list/page/vo` - 我的应用列表
- `POST /app/all/list/page/vo` - 所有应用列表
- `POST /app/good/list/page/vo` - 精选应用列表

### AI聊天接口
- `GET /chat/chat` - AI聊天（SSE流式响应）
- `GET /chatHistory/app/{appId}` - 获取聊天历史

## 开发指南

### 环境要求
- Node.js 16+
- npm 或 yarn

### 安装依赖
```bash
npm install
```

### 开发模式
```bash
npm run dev
```

### 构建生产版本
```bash
npm run build
```

### 预览构建结果
```bash
npm run preview
```

## 核心功能

1. **用户认证系统**: 注册、登录、退出登录
2. **应用管理**: 创建、编辑、删除AI聊天应用
3. **AI聊天**: 支持流式响应的实时聊天
4. **聊天历史**: 查看和管理聊天记录
5. **权限管理**: 用户角色和权限控制

## 技术特色

- **TypeScript支持**: 完整的类型安全
- **响应式设计**: 适配不同屏幕尺寸
- **模块化架构**: 清晰的代码组织结构
- **错误处理**: 完善的异常处理机制
- **性能优化**: 代码分割和懒加载