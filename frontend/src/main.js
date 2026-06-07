import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

// 全局样式优化
const style = document.createElement('style')
style.textContent = `
  * {
    box-sizing: border-box;
  }

  body {
    margin: 0;
    padding: 0;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    background: #f8fafc;
  }

  .el-card {
    border-radius: 12px;
    border: 1px solid #e4e7ed;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    transition: all 0.2s ease;
  }

  .el-card:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  }

  .el-button--primary {
    background: linear-gradient(135deg, #0f766e 0%, #0891b2 100%);
    border: none;
    border-radius: 8px;
  }

  .el-button--primary:hover {
    background: linear-gradient(135deg, #0d9488 0%, #0ea5e9 100%);
  }

  .el-input__wrapper {
    border-radius: 8px;
  }

  .el-table th.el-table__cell {
    background: #f8fafc !important;
    color: #475569;
    font-weight: 600;
  }

  .el-table td.el-table__cell {
    color: #334155;
  }

  .el-pagination {
    font-weight: 500;
  }

  ::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }

  ::-webkit-scrollbar-track {
    background: #f1f5f9;
    border-radius: 4px;
  }

  ::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 4px;
  }

  ::-webkit-scrollbar-thumb:hover {
    background: #94a3b8;
  }
`
document.head.appendChild(style)

app.mount('#app')
