# ETF 数据分析平台

基于 Spring Boot 3.5.7 + MyBatis-Plus + MySQL 8 + Vue 3 + Element Plus 的 ETF 数据管理与智能分析平台。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.7 (Java 17) |
| ORM | MyBatis-Plus 3.5.14 |
| 数据库 | MySQL 8.4 |
| 缓存 | Redis |
| 消息队列 | RabbitMQ |
| 服务注册 | Nacos |
| 前端框架 | Vue 3 + Vite |
| UI 组件库 | Element Plus |
| API 文档 | Knife4j |

## 功能模块

### ETF 数据管理
- **基金数据**：IOPV 估值、份额管理
- **市场数据**：实时行情快照、K线数据
- **PCF 数据**：申购赎回清单（PCF）信息及成分股
- **资金流分析**：资金流向汇总与可视化图表
- **TA 指标**：技术分析指标管理

### 智能分析
- **五维共振分析**：多维度共振信号识别
- **AI 智能助手**：ETF 相关问题的智能问答

### 系统管理
- **用户认证**：登录与权限管理
- **参数配置**：系统参数管理与模糊查询
- **菜单配置**：动态菜单管理
- ** ETL 监控**：批处理任务状态与检查点管理

## Docker 一键启动

项目根目录已提供 `docker-compose.yml`，可同时启动：

- MySQL
- Redis
- RabbitMQ
- Nacos
- 后端服务

```bash
docker compose up -d --build
```

启动后服务地址：

| 服务 | 地址 |
|------|------|
| 后端 API | http://localhost:8080 |
| API 文档 | http://localhost:8080/doc.html |
| Nacos 控制台 | http://localhost:8848/nacos |
| RabbitMQ 控制台 | http://localhost:15672 |

连通性检查：

```bash
curl http://localhost:8080/api/infra/health
```

## 目录结构

```
etfProject/
├── src/main/java/com/demo/    # 后端代码
│   ├── controller/            # 控制器层
│   ├── service/               # 业务逻辑层
│   ├── mapper/                 # 数据访问层
│   ├── entity/                # 实体类
│   ├── config/                 # 配置类
│   └── utils/                  # 工具类
├── src/main/resources/
│   └── init.sql               # 数据库初始化脚本
├── frontend/                   # 前端 Vite 项目
│   └── src/views/             # 页面组件
├── docker-compose.yml         # Docker 编排配置
└── Dockerfile                # 后端镜像构建
```

## 后端启动

```bash
mvn spring-boot:run
```

## 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端地址：http://localhost:5173

## 阿里云 ECS 一键部署

```bash
chmod +x deploy_aliyun.sh
./deploy_aliyun.sh
```

部署脚本会检查并安装 Docker Compose、启动全部服务、自动导入数据库初始化脚本，并进行健康检查。

## 已实现内容

- 17 张表的后端 CRUD 接口
- 统一返回格式 `code/message/data`
- 分页接口和关键字检索
- 基于通用 CRUD 组件的 Vue3 页面
- AI 智能助手功能
- 系统参数动态配置
- ETL 批处理监控
- 五维共振分析模块
