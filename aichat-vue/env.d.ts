/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 简化模块声明
declare module 'vue' {
  export * from 'vue'
}

declare module 'vue-router' {
  export * from 'vue-router'
}

declare module 'pinia' {
  export * from 'pinia'
}

declare module 'ant-design-vue' {
  export * from 'ant-design-vue'
}

declare module '@ant-design/icons-vue' {
  export * from '@ant-design/icons-vue'
}

declare module 'axios' {
  export * from 'axios'
}

declare module 'dayjs' {
  export * from 'dayjs'
}