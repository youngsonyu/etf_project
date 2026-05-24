# ETF Project

基于 Spring Boot 2.7 + MyBatis-Plus + MySQL 8 + Vue 3 + Element Plus 的 ETF CRUD 项目。

## Docker 一键启动

项目根目录已提供 `docker-compose.yml`，可同时启动：

- MySQL
- Redis
- RabbitMQ
- Nacos
- 后端服务

启动前建议先准备 `.env` 或直接修改 compose 中的默认密码。

说明：

- MySQL 的账号密码不能只放在数据库里，因为应用连接数据库之前就必须先拿到它。
- Redis、RabbitMQ、Nacos 的连接参数可以由数据库里的参数表管理，但前提仍然是应用先连上 MySQL。
- 已预置以下参数键到 `sys_param`：`infra.redis.*`、`infra.rabbitmq.*`、`infra.nacos.server-addr`。
- `/api/infra/health` 会优先读取这些数据库参数进行连通性检查；若未配置则回退到应用环境变量。

推荐启动方式：

```bash
docker compose up -d --build
```

后端地址：`http://localhost:8080`
Nacos 控制台：`http://localhost:8848/nacos`
RabbitMQ 控制台：`http://localhost:15672`

连通性检查：

```bash
curl http://localhost:8080/api/infra/health
```

该接口会依次检查 MySQL、Redis、RabbitMQ 和 Nacos 是否可达。

## 目录结构

- `src/main/java/com/demo` 后端代码
- `src/main/resources/init.sql` 数据库初始化脚本
- `frontend` 前端 Vite 项目

## 数据库初始化

1. 创建数据库：`etf_db`
2. 执行初始化脚本：`src/main/resources/init.sql`
3. 根据实际环境修改 `src/main/resources/application.yml` 的数据库账号密码

## 后端启动

在项目根目录执行：

```bash
mvn spring-boot:run
```

后端地址：`http://localhost:8080`

API 文档：`http://localhost:8080/doc.html`

## 前端启动

在 `frontend` 目录执行：

```bash
npm install
npm run dev
```

前端地址：`http://localhost:5173`

## 已实现内容

- 10 张表后端 CRUD 接口
- 统一返回格式 `code/message/data`
- 分页接口和关键字检索
- Vue3 + Element Plus 通用 CRUD 页面
- 每张表独立路由页面
- Axios 请求封装与代理配置
