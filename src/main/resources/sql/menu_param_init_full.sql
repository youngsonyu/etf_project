-- ============================================================
-- 1. Python 脚本配置
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('PYTHON_SCRIPT_PATH', 'scripts/getETFInfo_new.py', 'Python ETL 脚本的 classpath 路径', 1),
('PYTHON_COMMAND', 'python', 'Python 执行命令或可执行文件路径，如 python / py / C:\\Python311\\python.exe', 1);

-- ============================================================
-- 2. 菜单配置表初始化（按分类组织）
-- ============================================================
INSERT INTO menu_config (menu_code, menu_name, parent_code, path, icon, sort, is_visible, is_active, remark) VALUES
-- 父分类（path=null 表示分组父菜单，不可点击）
('etf_home', '首页', NULL, NULL, 'HomeFilled', 5, 1, 1, '首页分类'),
('etf_regular', 'ETF常规指标', NULL, NULL, 'DataAnalysis', 10, 1, 1, 'ETF常规指标分类'),
('etf_quant', 'ETF量化模型', NULL, NULL, 'DataLine', 20, 1, 1, 'ETF量化模型分类'),
('etf_ai', 'ETF智能助手', NULL, NULL, 'MagicStick', 25, 1, 1, 'ETF智能助手分类'),
('system', '系统管理', NULL, NULL, 'Setting', 30, 1, 1, '系统管理分类'),

-- 首页子菜单
('dashboard', '首页', 'etf_home', '/dashboard', 'HomeFilled', 6, 1, 1, '系统首页'),

-- ETF常规指标子菜单
('etf_fund_flow_summary', 'ETF资金流向', 'etf_regular', '/etf_fund_flow_summary', 'TrendCharts', 11, 1, 1, 'ETF资金流向统计'),
('etf_fund_flow_chart', 'ETF资金流向图表', 'etf_regular', '/etf_fund_flow_chart', 'PieChart', 12, 1, 1, 'ETF资金流向图表'),
('etf_security_master', 'ETF基础信息', 'etf_regular', '/etf_security_master', 'Notebook', 13, 1, 1, 'ETF基础信息主表'),
('etf_market_snapshot', 'ETF行情快照', 'etf_regular', '/etf_market_snapshot', 'Monitor', 14, 1, 1, 'ETF实时/历史快照'),
('etf_market_kline', 'K线数据', 'etf_regular', '/etf_market_kline', 'LineChart', 15, 1, 1, 'K线数据'),
('etf_pcf_info', 'ETF PCF头信息', 'etf_regular', '/etf_pcf_info', 'Document', 16, 1, 1, 'ETF每日PCF头信息'),
('etf_pcf_constituent', 'ETF PCF成分券', 'etf_regular', '/etf_pcf_constituent', 'Tickets', 17, 1, 1, 'ETF每日PCF成分券明细'),
('etf_fund_share', 'ETF基金份额', 'etf_regular', '/etf_fund_share', 'PieChart', 18, 1, 1, 'ETF基金份额'),
('etf_fund_iopv', 'ETF IOPV净值', 'etf_regular', '/etf_fund_iopv', 'Histogram', 19, 1, 1, 'ETF IOPV净值'),
('etf_ta_indicator', 'ETF技术指标', 'etf_regular', '/etf_ta_indicator', 'DataAnalysis', 20, 1, 1, 'ETF技术指标'),

-- ETF量化模型子菜单
('etf_five_dimension_resonance', 'ETF量化数据分析', 'etf_quant', '/etf_five_dimension_resonance', 'DataLine', 21, 1, 1, 'ETF量化数据分析'),
('etf_five_dimension_report', 'ETF量化报告', 'etf_quant', '/etf_five_dimension_report', 'ReadingLamp', 22, 1, 1, 'ETF量化报告'),

-- ETF智能助手子菜单
('etf_ai_assistant', 'ETF智能助手', 'etf_ai', '/etf_ai_assistant', 'MagicStick', 26, 1, 1, 'ETF AI智能分析助手'),

-- 系统管理子菜单
('trade_calendar', '交易日历', 'system', '/trade_calendar', 'Calendar', 31, 1, 1, '交易日历表'),
('menu_config', '菜单配置', 'system', '/menu_config', 'Operation', 32, 1, 1, '系统菜单配置'),
('sys_param', '参数配置', 'system', '/sys_param', 'Tools', 33, 1, 1, '系统参数配置'),
('etl_batch_status', 'ETL跑批状态', 'system', '/etl_batch_status', 'Clock', 34, 1, 1, 'ETL跑批状态管理'),
('etl_checkpoint', 'ETL跑批检查点', 'system', '/etl_checkpoint', 'Memo', 35, 1, 1, 'ETL跑批检查点管理');

-- ============================================================
-- 3. 菜单显示参数配置
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('menu.etf_home.visible', '1', '首页分类菜单是否显示', 1),
('menu.etf_regular.visible', '1', 'ETF常规指标分类菜单是否显示', 1),
('menu.etf_quant.visible', '1', 'ETF量化模型分类菜单是否显示', 1),
('menu.etf_ai.visible', '1', 'ETF智能助手分类菜单是否显示', 1),
('menu.dashboard.visible', '1', '首页菜单是否显示', 1),
('menu.etf_fund_flow_summary.visible', '1', 'ETF资金流向菜单是否显示', 1),
('menu.etf_fund_flow_chart.visible', '1', 'ETF资金流向图表菜单是否显示', 1),
('menu.etf_fund_iopv.visible', '1', 'ETF IOPV净值菜单是否显示', 1),
('menu.etf_fund_share.visible', '1', 'ETF基金份额菜单是否显示', 1),
('menu.etf_market_kline.visible', '1', 'K线数据菜单是否显示', 1),
('menu.etf_market_snapshot.visible', '1', 'ETF快照菜单是否显示', 1),
('menu.etf_pcf_constituent.visible', '1', 'ETF PCF成分券菜单是否显示', 1),
('menu.etf_pcf_info.visible', '1', 'ETF PCF头信息菜单是否显示', 1),
('menu.etf_security_master.visible', '1', 'ETF基础信息菜单是否显示', 1),
('menu.etf_ta_indicator.visible', '1', 'ETF技术指标菜单是否显示', 1),
('menu.etf_five_dimension_resonance.visible', '1', 'ETF量化数据分析菜单是否显示', 1),
('menu.etf_five_dimension_report.visible', '1', 'ETF量化报告菜单是否显示', 1),
('menu.etf_ai_assistant.visible', '1', 'ETF智能助手菜单是否显示', 1),
('menu.trade_calendar.visible', '1', '交易日历菜单是否显示', 1),
('menu.menu_config.visible', '1', '菜单配置菜单是否显示', 1),
('menu.sys_param.visible', '1', '参数配置菜单是否显示', 1),
('menu.system.visible', '1', '系统管理菜单是否显示', 1),
('menu.etl_batch_status.visible', '1', 'ETL跑批状态菜单是否显示', 1),
('menu.etl_checkpoint.visible', '1', 'ETL跑批检查点菜单是否显示', 1);

-- ============================================================
-- 4. 系统认证与 AI 配置
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('auth.admin.username', 'dwb', '管理员登录账号(明文)', 1),
('auth.admin.password', 'yy0101.', '管理员登录密码(明文)', 1),
('auth.user.register.enabled', '1', '普通用户注册开关：1开放 0关闭', 1),
('ai.dashscope.api-key', '', 'MiniMax API Key', 1);

-- ============================================================
-- 5. Redis 配置
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('infra.redis.host', 'redis', 'Redis 主机地址（容器内建议redis，本地建议127.0.0.1）', 1),
('infra.redis.port', '6379', 'Redis 端口', 1),
('infra.redis.password', 'redis123456', 'Redis 连接密码', 1);

-- ============================================================
-- 6. RabbitMQ 配置
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('infra.rabbitmq.host', 'rabbitmq', 'RabbitMQ 主机地址（容器内建议rabbitmq，本地建议127.0.0.1）', 1),
('infra.rabbitmq.port', '5672', 'RabbitMQ AMQP 端口', 1),
('infra.rabbitmq.username', 'admin', 'RabbitMQ 用户名', 1),
('infra.rabbitmq.password', 'rabbit123456', 'RabbitMQ 密码', 1),
('infra.rabbitmq.virtual-host', '/', 'RabbitMQ 虚拟主机', 1);

-- ============================================================
-- 7. Nacos 配置
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('infra.nacos.server-addr', 'nacos:8848', 'Nacos 服务地址（容器内建议nacos:8848，本地建议127.0.0.1:8848）', 1);

-- ============================================================
-- 8. ETL 定时任务配置
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('etl.schedule.enabled', '1', 'ETF Python跑批定时任务是否启用：1启用 0禁用', 1),
('etl.schedule.time', '08:00', 'ETF Python跑批定时任务执行时间，格式HH:mm', 1);

-- ============================================================
-- 9. AmazingData 与 MySQL 数据源配置
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('AD_USERNAME', '410500122546', 'AmazingData用户名', 1),
('AD_PASSWORD', '', 'AmazingData密码（请修改）', 1),
('AD_HOST', '101.230.159.234', 'AmazingData主机地址', 1),
('AD_PORT', '8600', 'AmazingData端口', 1),
('MYSQL_HOST', '127.0.0.1', 'MySQL主机地址', 1),
('MYSQL_PORT', '3306', 'MySQL端口', 1),
('MYSQL_USER', 'root', 'MySQL用户名', 1),
('MYSQL_PASSWORD', 'Dwb5201314.', 'MySQL密码', 1),
('MYSQL_DB', 'amazingdata_etf', 'MySQL数据库名', 1),
('MYSQL_CHARSET', 'utf8mb4', 'MySQL字符集', 1);

-- ============================================================
-- 10. ETL 运行配置与技术指标参数
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('SOURCE', 'AmazingData', '数据来源标识', 1),
('DEFAULT_CALENDAR_MARKET', 'SH', '默认交易日历市场', 1),
('MAX_CODE_BATCH', '300', '单次接口请求ETF代码数量上限', 1),
('RUN_MODE', 'recent', '运行模式：full/recent/incremental', 1),
('RECENT_DAYS', '1', 'recent 模式处理最近交易日天数', 1),
('INCLUDE_TODAY', '0', '是否包含当天交易日：1是0否', 1),
('MAX_PROCESS_DAYS', '30', '单次最多处理天数（避免一次处理太多）', 1),
('KLINE_PERIOD_NAMES', 'day,week,month,season,year', 'K线周期列表(逗号分隔)', 1),
('TARGET_PERIODS', 'day,week,month,season', '技术指标计算周期(逗号分隔)', 1),
('LOOKBACK_BARS', '500', '增量模式回看K线数量', 1),
('WRITE_CHUNK_SIZE', '2000', '写入分块大小', 1),
('SAR_N', '4', 'SAR指标N参数', 1),
('SAR_AF_STEP', '0.02', 'SAR指标加速因子步长', 1),
('SAR_AF_MAX', '0.20', 'SAR指标加速因子最大值', 1);

-- ============================================================
-- 11. 表级别 ETL 开关配置（含 ON DUPLICATE KEY UPDATE）
-- ============================================================
INSERT INTO sys_param (param_key, param_value, description, is_active) VALUES
('ENABLE_ETF_SECURITY_MASTER', '1', 'ETF基础信息表开关', 1),
('ENABLE_TRADE_CALENDAR', '1', '交易日历表开关', 1),
('ENABLE_ETF_MARKET_SNAPSHOT', '0', '行情快照表开关(当前兼容性问题)', 1),
('ENABLE_ETF_MARKET_KLINE', '1', 'ETF行情K线表开关', 1),
('ENABLE_ETF_PCF_INFO', '1', 'ETF PCF主表开关', 1),
('ENABLE_ETF_PCF_CONSTITUENT', '1', 'ETF PCF成分表开关', 1),
('ENABLE_ETF_FUND_SHARE', '1', 'ETF基金份额表开关', 1),
('ENABLE_ETF_FUND_IOPV', '1', 'ETF IOPV表开关', 1),
('ENABLE_TA_INDICATOR', '1', '技术指标计算开关', 1)
ON DUPLICATE KEY UPDATE
    param_value = VALUES(param_value),
    description = VALUES(description),
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================
-- 12. ETL 检查点初始化
-- ============================================================
INSERT INTO etl_checkpoint (checkpoint_key, last_trade_date, last_batch_no) 
VALUES ('ETF_KLINE_DAY', 20260520, NULL)
ON DUPLICATE KEY UPDATE
    last_trade_date = VALUES(last_trade_date),
    last_batch_no = VALUES(last_batch_no);
