# ETF 数据分析平台

基于 Spring Boot 3.5.7 + MyBatis-Plus + MySQL 8 + Vue 3 + Element Plus 的 ETF 数据管理与智能分析平台。

## 本次调整说明

为适配本地开发和 4C8G ECS 轻量部署，项目已做以下调整：

1. **本地开发默认只依赖 MySQL**
   - 默认关闭 Redis、RabbitMQ、Nacos 的基础设施检查
   - 本地不再要求必须先启动完整中间件栈

2. **启动脚本已去除写死路径**
   - [start.bat](D:/dwb/etf/etf_project_v2/start.bat)
   - [start.sh](D:/dwb/etf/etf_project_v2/start.sh)
   - 启动脚本会自动以脚本所在目录作为项目根目录
   - 启动前会检查 `java`、`mvn`、`npm` 是否可用
   - Windows 脚本在前端未安装依赖时会自动执行 `npm install`

3. **服务器默认部署改为轻量模式**
   - 默认使用 [docker-compose.yml](D:/dwb/etf/etf_project_v2/docker-compose.yml)，只启动 MySQL 和后端
   - 如需完整中间件栈，可叠加 [docker-compose.full.yml](D:/dwb/etf/etf_project_v2/docker-compose.full.yml)

4. **为轻量 ECS 增加了保守资源配置**
   - MySQL 容器添加了基础内存/CPU限制
   - Java 容器默认使用 `-Xms256m -Xmx768m`
   - 目的是降低跑批和日常访问同时进行时的内存风险

5. **前端接口超时改为可配置**
   - 普通接口默认 `30000ms`
   - 长任务接口默认 `180000ms`
   - 可通过前端环境变量覆盖

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

项目根目录默认提供轻量部署配置 [docker-compose.yml](D:/dwb/etf/etf_project_v2/docker-compose.yml)，仅启动：

- MySQL
- 后端服务

适合轻量 ECS（如 2C2G） 的默认启动命令：

```bash
docker compose up -d --build
```

如需额外启用 Redis / RabbitMQ / Nacos，请叠加 [docker-compose.full.yml](D:/dwb/etf/etf_project_v2/docker-compose.full.yml)：

```bash
docker compose -f docker-compose.yml -f docker-compose.full.yml up -d --build
```

启动后服务地址：

| 服务 | 地址 |
|------|------|
| 后端 API | http://localhost:8080 |
| API 文档 | http://localhost:8080/doc.html |
默认模式下连通性检查：

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

本地启动前请先准备以下环境：

- Java 17
- Maven（命令行可直接执行 `mvn`）
- Node.js 与 npm
- MySQL（本地默认只依赖 MySQL）

本地开发环境默认关闭 Redis、RabbitMQ、Nacos 健康检查；如需启用，可设置以下环境变量为 `true`：

- `APP_INFRA_REDIS_ENABLED`
- `APP_INFRA_RABBITMQ_ENABLED`
- `APP_INFRA_NACOS_ENABLED`

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

## 启动脚本

- Windows：运行 [start.bat](D:/dwb/etf/etf_project_v2/start.bat)
- Linux/macOS：运行 `./start.sh`

启动脚本会自动以脚本所在目录作为项目根目录，不再依赖写死路径；但仍要求 `java`、`mvn`、`npm` 已正确安装并加入 PATH。

## 服务器部署

服务器默认可通过 [docker-compose.yml](D:/dwb/etf/etf_project_v2/docker-compose.yml) 仅启动 MySQL 和后端服务；该配置默认关闭 Redis、RabbitMQ、Nacos 基础设施检查，并给 MySQL/JVM 设置了更适合 2C2G 轻量实例的资源上限。

如果服务器后续确实需要中间件，再叠加 [docker-compose.full.yml](D:/dwb/etf/etf_project_v2/docker-compose.full.yml) 启动 Redis、RabbitMQ、Nacos；叠加后后端会自动启用对应基础设施检查。

## 阿里云 ECS 一键部署

```bash
chmod +x deploy_aliyun.sh
./deploy_aliyun.sh
```

默认执行只会启动 MySQL 和后端。

如需完整中间件栈：

```bash
./deploy_aliyun.sh --full-infra
```

部署脚本会检查并安装 Docker Compose、按所选模式启动服务、自动导入数据库初始化脚本，并进行健康检查。

### 推荐服务器 `.env`

可基于 [`.env.example`](D:/dwb/etf/etf_project_v2/.env.example) 调整，2C2G 轻量 ECS 推荐保留以下核心配置：

```env
MYSQL_ROOT_PASSWORD=请改成你自己的强密码
MYSQL_DATABASE=amazingdata_etf
MYSQL_USER=etf
MYSQL_PASSWORD=请改成你自己的强密码
JAVA_OPTS=-Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8
APP_INFRA_REDIS_ENABLED=false
APP_INFRA_RABBITMQ_ENABLED=false
APP_INFRA_NACOS_ENABLED=false
```

如果启用完整中间件栈，再补充：

```env
REDIS_PASSWORD=请改成你自己的强密码
RABBITMQ_DEFAULT_USER=admin
RABBITMQ_DEFAULT_PASS=请改成你自己的强密码
```

### 前端超时配置

前端请求超时已支持环境变量配置：

- 普通接口：`VITE_API_TIMEOUT`，默认 `30000`
- 长任务接口：`VITE_LONG_TASK_TIMEOUT`，默认 `180000`

这些变量应配置在前端项目目录下，可通过 [frontend/.env.example](D:/dwb/etf/etf_project_v2/frontend/.env.example) 作为参考。

## 已实现内容

- 17 张表的后端 CRUD 接口
- 统一返回格式 `code/message/data`
- 分页接口和关键字检索
- 基于通用 CRUD 组件的 Vue3 页面
- AI 智能助手功能
- 系统参数动态配置
- ETL 批处理监控
- 五维共振分析模块
