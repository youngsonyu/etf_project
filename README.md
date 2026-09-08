# ETF 数据分析平台

基于 Spring Boot 3.5.7 + MyBatis-Plus + MySQL 8 + Vue 3 + Element Plus 的 ETF 数据管理与智能分析平台。

## 本次调整说明

为适配本地开发和轻量 ECS 部署，项目已做以下调整：

1. **本地开发默认只依赖 MySQL**
   - 默认关闭 Redis、RabbitMQ、Nacos 的基础设施检查
   - 本地不再要求必须先启动完整中间件栈

2. **启动脚本已去除写死路径**
   - [start.bat](D:/dwb/etf/etf_project_v2/start.bat)
   - [start.sh](D:/dwb/etf/etf_project_v2/start.sh)
   - 启动脚本会自动以脚本所在目录作为项目根目录
   - 启动前会检查 `java`、`mvn`、`npm` 是否可用
   - Windows 脚本在前端未安装依赖时会自动执行 `npm install`

3. **本地和服务器统一为 Java + MySQL 直连运行（不依赖 Docker）**
   - 本地继续使用 `mvn spring-boot:run` 和 `npm run dev`
   - 服务器使用 [deploy_aliyun.sh](D:/dwb/etf/etf_project_v2/deploy_aliyun.sh) 直接打包并后台启动 Java 进程

4. **为 2C2G 轻量 ECS 调整了 JVM 默认内存**
   - 默认使用 `-Xms256m -Xmx768m`
   - 目标是在低内存机器上提升稳定性并减少 OOM 风险

5. **前端接口超时改为可配置**
   - 普通接口默认 `30000ms`
   - 长任务接口默认 `180000ms`
   - 可通过前端环境变量覆盖

6. **ETL 导入状态提示与判定增强**
   - ETL 页面触发导入后改为“任务已触发”，避免误导为“已成功入库”
   - 后端识别“没有可处理的新交易日”场景，并将批次状态标记为 `PARTIAL`，同时写入说明信息

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.7 (Java 17) |
| ORM | MyBatis-Plus 3.5.14 |
| 数据库 | MySQL 8.4 |
| 缓存/消息/注册 | 本地与轻量服务器默认关闭（可选） |
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

## Java + MySQL 快速启动（推荐）

当前推荐方案：**本地和服务器都不使用 Docker**，仅使用 Java 和 MySQL。

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
└── deploy_aliyun.sh           # 服务器一键部署脚本（Java 进程模式）
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

## 服务器部署（无 Docker）

服务器直接运行 Java 进程并连接 MySQL，部署脚本为 [deploy_aliyun.sh](D:/dwb/etf/etf_project_v2/deploy_aliyun.sh)。

可行性评估（2C2G）：

- **比 Docker 模式更省内存**：少了容器运行时与镜像层开销
- 适合你当前场景（跑批主要在本地、服务器日常小数据量任务）
- 风险：并发提升或任务叠加时，仍可能触发内存紧张，需要再升配或下调并发

前端同样不依赖 Docker：在服务器上执行 `cd frontend && npm install && npm run build` 生成静态文件，再用 Nginx（或其他静态服务）托管 `frontend/dist`。

## 阿里云 ECS 一键部署

```bash
chmod +x deploy_aliyun.sh
./deploy_aliyun.sh
```

部署脚本会执行：

1. 检查 `java` / `mvn` / `curl`
2. 校验 Java 版本为 17
3. 打包后端（`mvn -DskipTests package`）
4. 以后台进程方式启动后端并写入 PID/日志
5. 轮询健康检查 `http://127.0.0.1:8080/api/infra/health`

### 推荐服务器 `.env`

可基于 [`.env.example`](D:/dwb/etf/etf_project_v2/.env.example) 调整，2C2G 轻量 ECS 推荐配置如下：

```env
SERVER_PORT=8080
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_USER=etf
MYSQL_PASSWORD=请改成你自己的强密码
MYSQL_DB=amazingdata_etf
JAVA_OPTS=-Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8
APP_INFRA_REDIS_ENABLED=false
APP_INFRA_RABBITMQ_ENABLED=false
APP_INFRA_NACOS_ENABLED=false
```

### 前端超时配置

前端请求超时已支持环境变量配置：

- 普通接口：`VITE_API_TIMEOUT`，默认 `30000`
- 长任务接口：`VITE_LONG_TASK_TIMEOUT`，默认 `180000`

这些变量应配置在前端项目目录下，可通过 [frontend/.env.example](D:/dwb/etf/etf_project_v2/frontend/.env.example) 作为参考。

### ETL 按钮触发失败排查（银河证券导入）

若点击“银河证券数据导入”后批次很快失败，请优先检查 Python 环境：

1. `sys_param` 中 `PYTHON_COMMAND` 是否可执行（例如 `python`、`python3` 或 `py -3`）
2. 该 Python 环境是否已安装依赖：`numpy`、`pandas`、`sqlalchemy`、`AmazingData`
3. 也可直接在后端进程环境变量中配置 `PYTHON_COMMAND`（例如 `C:\\Python311\\python.exe`）和 `PYTHON_SCRIPT_PATH`（默认 `scripts/getETFInfo_new.py`）

后端现在会在触发 ETL 前做 Python 命令与依赖自检，失败信息会写入 ETL 批次错误信息列。

## 已实现内容

- 17 张表的后端 CRUD 接口
- 统一返回格式 `code/message/data`
- 分页接口和关键字检索
- 基于通用 CRUD 组件的 Vue3 页面
- AI 智能助手功能
- 系统参数动态配置
- ETL 批处理监控
- 五维共振分析模块

## 数据库表分层分类（L1~L4）

按你的定义将当前 17 张表分为 4 层：

### L1 券商提供的基础数据表

- `etf_security_master`
- `trade_calendar`
- `etf_market_snapshot`
- `etf_market_kline`
- `etf_pcf_info`
- `etf_pcf_constituent`
- `etf_fund_share`
- `etf_fund_iopv`

### L2 计算的基础指标表

- `etf_ta_indicator`

### L3 策略类表（资金流/五维共振等）

- `etf_fund_flow_summary`（资金流入流出金额汇总）
- `etf_five_dimension_resonance`（五维共振 ETF 结果）
- `etf_five_dimension_report`（策略分析报告）

### L4 系统类表

- `sys_user`
- `sys_param`
- `menu_config`
- `etl_batch_status`
- `etl_checkpoint`
