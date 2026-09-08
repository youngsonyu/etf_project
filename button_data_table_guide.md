# 三个业务按钮与数据表功能说明

## 1. 刷新最新交易日

页面：五维共振分析。接口：`POST /api/etf_five_dimension_resonance/refresh-latest`。

直接写入：

- `etf_five_dimension_resonance`：ETF五维共振分析结果表。按 `(etf_code, trade_date)` 新增或更新最新交易日结果。

读取依赖：

- `etf_ta_indicator`：ETF技术指标表，提供日、周、月、季指标。
- `etf_security_master`：ETF基础信息主表，提供 ETF 名称和有效状态。

该按钮现在以异步任务方式处理一个最新交易日，接口会立即返回任务编号，页面通过任务状态查询显示结果。它本身仍只处理一个最新交易日，不能替代历史补算。

页面新增“全量补算历史共振”入口，调用 `POST /api/etf_five_dimension_resonance/backfill-all`。接口会从 L2 表中找出全部实际存在的日线交易日，逐日 upsert 所有有效 ETF 的结果。清空五维表后可用它恢复全量历史数据，但某日没有 L2 日线指标时会跳过，不能凭空生成指标。

当前策略按四个已有周期 `day`、`week`、`month`、`season` 判断，每个周期要求 MACD 金叉状态、MACD 红柱和 SAR 多头同时满足。45 日线暂不计算，按业务约定默认“金叉红柱”通过。因此这是“45日线默认通过的四周期实现”，不是实际计算了 45 日线。

对于目标交易日，周线、月线和季线取该目标日之前（含目标日）最近的一条有效指标，不要求它们的 `trade_time` 必须恰好等于日线日期。这样才能在普通交易日正确使用最近的周、月、季状态。

## 2. 确认新增累计

页面：资金流向汇总。接口：`POST /api/etf_fund_flow_summary/accumulate`。

直接写入或更新：

- `etf_fund_flow_summary`：ETF资金流向汇总表。

读取依赖：

- `etf_fund_share`：ETF基金份额表。
- `etf_market_kline`：ETF K线表，提供收盘价。
- `etf_security_master`：ETF基础信息主表，提供 ETF 名称。

核心计算：

```text
份额变动 = 当日份额 - 上一个记录日份额
当日资金流向 = 份额变动 × 收盘价 × 10000
累计资金流向 = 按 ETF 和交易日期排序的资金流向累计值
```

## 3. 银河证券数据导入

页面：ETL批次状态。接口：`POST /api/etl_batch_status/import-galaxy`。该按钮只触发后台异步 Python ETL，最终状态在 `etl_batch_status` 中查看。

### 直接更新的 L1 表

| 表名 | 中文名称 | 功能 |
| --- | --- | --- |
| `etf_security_master` | ETF基础信息主表 | ETF代码、名称、市场、上市状态 |
| `trade_calendar` | 交易日历表 | 上海、深圳交易日和开市状态 |
| `etf_market_snapshot` | ETF实时/历史快照表 | 实时或历史快照，当前开关默认可能关闭 |
| `etf_market_kline` | ETF K线表 | 日、周、月、季、年等周期行情 |
| `etf_pcf_info` | ETF每日PCF头信息表 | 申购赎回篮子、净值和现金替代信息 |
| `etf_pcf_constituent` | ETF每日PCF成分券明细表 | PCF 成分证券及替代金额、份额 |
| `etf_fund_share` | ETF基金份额表 | 基金份额、总份额、流通份额和变动原因 |
| `etf_fund_iopv` | ETF每日收盘IOPV表 | 每日收盘 IOPV 净值 |

L1 表并非每次全部更新，取决于 `sys_param` 中的 `ENABLE_*` 开关；PCF、IOPV、行情快照当前可能关闭。

### 直接生成的 L2 表

- `etf_ta_indicator`：ETF技术指标表。

它不是银河证券直接返回的原始表，而是 ETL 在写入 K 线后，根据 K 线计算 MACD、SAR、MA、RSI、KDJ、BOLL、ATR、ADX 和综合信号，再写入的计算指标表。只有 K 线导入成功且 `ENABLE_TA_INDICATOR=true` 时才会更新。

### ETL 监控与断点表

- `etl_batch_status`：ETL批处理任务状态表，记录批次号、状态、日期范围、数量和错误信息。
- `etl_checkpoint`：ETL断点检查点表，记录上次成功处理日期和批次号，用于增量续跑。
- `sys_param`：系统参数表，提供数据源、数据库、运行模式和模块开关配置。它是读取配置，不是本次 ETL 的主要业务结果表。

## 4. 三个按钮的关系

```text
银河证券数据导入
  -> L1 基础数据表
  -> etf_market_kline
  -> L2 etf_ta_indicator

L2 技术指标 + L1 ETF基础信息
  -> 刷新最新交易日 / 全量补算历史共振
  -> L3 etf_five_dimension_resonance

L1 etf_fund_share + etf_market_kline
  -> 确认新增累计
  -> etf_fund_flow_summary
```

## 5. 结论

- L1 的原始/基础数据：主要由“银河证券数据导入”按钮触发的 ETL 获取和落库。
- L2 的技术指标：由同一个 ETL 流程读取 L1 K 线后计算并落库，不是直接从银河证券拿到的成品。
- L3 五维共振：不由“银河证券数据导入”直接生成，需要先有 L2，再点击“刷新最新交易日”或“全量补算历史共振”。

## 6. L1/L2/L3 全量重跑建议

当需要重新计算 L2 时，在 `sys_param` 中确认：

```text
RUN_MODE = full
ENABLE_ETF_MARKET_KLINE = true
ENABLE_TA_INDICATOR = true
```

`RUN_MODE=full` 不设置日期起点，会从交易日历最早可用日期拉取到最新交易日。这样 L1、L2 都是全量数据，并为 MACD、RSI、ATR/ADX、SAR 提供完整历史预热；L3 使用“全量补算历史共振”以单条数据库集合化 SQL 处理 L2 中全部日线交易日。

全量 L2 写入使用批量事务，重复执行依赖 `(etf_code, period, trade_time)` 唯一键更新，不会产生重复指标记录。L2 全量完成后，使用“全量补算历史共振”覆盖写入 L3。L3 以存在日线 L2 的 `(etf_code, trade_date)` 为驱动，周/月/季指标通过“本周期指标生效日至下一条同周期指标生效日前”的有效区间与日线对齐；在选定最新状态后才判断三项共振条件。触发的昨日、今年首次、上次和最后日期均通过窗口函数在同一次计算中得出。

## 7. L2 技术指标计算公式

L2 指标由 [getETFInfo_new.py](src/main/resources/scripts/getETFInfo_new.py) 按 `etf_code + period` 分组，并按 `trade_time` 升序计算。这里的 `period` 是 K 线周期，例如 `day`、`week`、`month`、`season`。

以下公式以收盘价 `C_t`、最高价 `H_t`、最低价 `L_t` 表示。代码中的指标值会直接写入 `etf_ta_indicator`（ETF技术指标表）。

### 6.1 MACD

代码使用 Pandas `ewm(adjust=False)`：

```text
EMA12_t = EMA12_{t-1} + alpha12 × (C_t - EMA12_{t-1})
alpha12 = 2 / (12 + 1)

EMA26_t = EMA26_{t-1} + alpha26 × (C_t - EMA26_{t-1})
alpha26 = 2 / (26 + 1)

DIF_t = EMA12_t - EMA26_t
DEA_t = EMA9(DIF_t)
MACD_t = 2 × (DIF_t - DEA_t)
```

状态字段：

| 字段 | 计算规则 |
| --- | --- |
| `is_macd_golden_cross` | 当前 `DIF > DEA` 且上一根 `DIF <= DEA` 时为 1，否则为 0 |
| `is_macd_dead_cross` | 当前 `DIF < DEA` 且上一根 `DIF >= DEA` 时为 1，否则为 0 |
| `is_macd_golden_state` | `DIF > DEA` 为 1，否则为 0 |
| `is_macd_positive` | `MACD > 0` 为 1，否则为 0 |
| `is_macd_red` | 当前实现等同于 `MACD > 0`，为 1 表示红柱 |
| `macd_hist_direction` | 当前 MACD 大于上一根为 1，小于为 -1，相等或没有上一根为 0 |

### 6.2 移动平均线 MA

代码使用简单移动平均（SMA），并设置 `min_periods=1`，所以数据不足完整窗口时也会计算：

```text
MA_n(t) = 最近 min(n, 已有数据条数) 个收盘价的算术平均值
```

计算了 `MA5`、`MA10`、`MA20`、`MA30`、`MA60`。状态字段为：

```text
is_ma5_above_ma10  = 1，当 MA5 > MA10
is_ma10_above_ma20 = 1，当 MA10 > MA20
is_close_above_ma20 = 1，当 C_t > MA20
is_close_above_ma60 = 1，当 C_t > MA60
```

相等时均记为 0。

### 6.3 RSI

对每个周期分别计算 RSI6、RSI12、RSI24。首先计算价格变化：

```text
Delta_t = C_t - C_{t-1}
Gain_t = max(Delta_t, 0)
Loss_t = max(-Delta_t, 0)
```

随后使用 `ewm(adjust=False, alpha=1/n, min_periods=n)` 平滑：

```text
AvgGain_t = EWM(Gain_t, alpha=1/n)
AvgLoss_t = EWM(Loss_t, alpha=1/n)
RS_t = AvgGain_t / AvgLoss_t
RSI_n(t) = 100 - 100 / (1 + RS_t)
```

当不足 `n` 个价格变化时结果为缺失值。首个有效值使用前 `n` 个价格变化的算术平均初始化，之后使用 Wilder 递推。平均亏损为 0 且平均上涨大于 0 时 RSI=100；平均上涨和平均亏损都为 0 时 RSI=50。

### 6.4 KDJ

代码使用 `n=9`，先计算 9 个周期的最高价和最低价：

```text
Highest9_t = max(H_{t-8}, ..., H_t)
Lowest9_t  = min(L_{t-8}, ..., L_t)

RSV_t = 50，若 Highest9_t = Lowest9_t
RSV_t = (C_t - Lowest9_t) / (Highest9_t - Lowest9_t) × 100，否则
```

平滑规则：

```text
K_0 = 50
D_0 = 50
K_t = 2/3 × K_{t-1} + 1/3 × RSV_t
D_t = 2/3 × D_{t-1} + 1/3 × K_t
J_t = 3 × K_t - 2 × D_t
```

对应落库字段为 `k_value`、`d_value`、`j_value`。

### 6.5 BOLL 布林带

代码使用 `n=20`、标准差倍数 `k=2`，并使用总体标准差 `ddof=0`：

```text
BOLL_MID_t = SMA20(C_t)
STD20_t = sqrt(Σ(C_i - BOLL_MID_t)^2 / m_t)，其中 m_t = 当前窗口实际数据条数
BOLL_UPPER_t = BOLL_MID_t + 2 × STD20_t
BOLL_LOWER_t = BOLL_MID_t - 2 × STD20_t
```

不足 20 根数据时结果为空；完整 20 根数据后才开始计算。标准差使用总体标准差 `ddof=0`。

### 6.6 ATR 和 ADX

代码使用 `n=14`。真实波幅：

```text
TR1_t = H_t - L_t
TR2_t = abs(H_t - C_{t-1})
TR3_t = abs(L_t - C_{t-1})
TR_t = max(TR1_t, TR2_t, TR3_t)
```

方向移动：

```text
UpMove_t = H_t - H_{t-1}
DownMove_t = L_{t-1} - L_t

+DM_t = UpMove_t，当 UpMove_t > DownMove_t 且 UpMove_t > 0，否则 0
-DM_t = DownMove_t，当 DownMove_t > UpMove_t 且 DownMove_t > 0，否则 0
```

然后使用 `alpha=1/14` 的指数平滑：

```text
ATR14_t = EWM(TR_t, alpha=1/14)
+DI14_t = 100 × EWM(+DM_t, alpha=1/14) / ATR14_t
-DI14_t = 100 × EWM(-DM_t, alpha=1/14) / ATR14_t
DX_t = 100 × abs(+DI14_t - -DI14_t) / (+DI14_t + -DI14_t)
ADX14_t = EWM(DX_t, alpha=1/14)
```

ATR 使用前 14 根真实波幅的算术平均初始化，之后按 Wilder 递推；ADX 使用前 14 个有效 DX 的算术平均初始化，之后按 Wilder 递推。因此 ATR 通常从第 14 根开始有效，ADX 通常从第 27 根开始有效；分母为 0 时仍为空。

### 6.7 SAR

SAR 使用常见 Parabolic SAR 逐根迭代算法，默认加速参数来自 `sys_param`：

```text
SAR_AF_STEP = 0.02
SAR_AF_MAX = 0.20
```

使用前两根数据确定初始方向：

- 如果第二根收盘价不低于第一根收盘价，初始趋势为多头，否则为空头；
- 多头初始 SAR 为第一根最低价、EP 为第一根最高价；空头初始 SAR 为第一根最高价、EP 为第一根最低价；
- 初始加速因子 `AF = 0.02`。

多头状态下：

```text
SAR_t = SAR_{t-1} + AF × (EP - SAR_{t-1})
SAR_t = min(SAR_t, 前 N 根最低价)
```

如果当日最低价小于计算出的 SAR，则反转为空头，SAR 取此前极值点；否则若创新高，则更新 `EP`，并令 `AF = min(AF + 0.02, 0.20)`。空头逻辑对称，反转条件使用当日最高价大于 SAR。

空头状态对称计算：使用前 N 根最高价约束 SAR；若当日最高价大于等于 SAR，则反转为多头；创新低时更新 EP 和 AF。

落库字段：

```text
sar_value = SAR 值
sar_trend = 多头 1，空头 -1，初始化不足时 0
is_sar_bullish = 1，当 sar_trend = 1，否则 0
```

### 6.8 综合信号

代码生成三个综合信号：

```text
signal_trend_long = 1，当以下条件全部满足：
  is_macd_golden_state = 1
  is_sar_bullish = 1
  is_close_above_ma20 = 1

signal_momentum_long = 1，当以下条件全部满足：
  is_macd_red = 1
  rsi12 > 50
  k_value > d_value

signal_warning = 1，当以下任一条件满足：
  is_macd_dead_cross = 1
  is_close_above_ma20 = 0
  is_sar_bullish = 0
```

不满足条件时信号值为 0。五维共振刷新目前主要使用各周期的 `is_macd_golden_state` 和 `is_sar_bullish`，并不是直接使用 `signal_trend_long`、`signal_momentum_long` 两个综合字段。

## 7. 公式审查结论与注意事项

### 7.1 公式与代码一致的部分

- MACD 的 EMA 周期、DIF、DEA 和柱值公式与代码一致；代码使用 `adjust=False` 的递推式 EMA。
- MA、KDJ、BOLL、ATR/ADX 的窗口和主要计算关系与代码一致。
- 综合信号字段的布尔条件与代码一致。

### 7.2 与常见技术分析软件可能不同的部分

以下不是文档错误，而是当前代码的明确实现选择，使用其他软件复核时可能出现数值差异：

1. **软件初始化差异**：不同软件可能对 EMA、Wilder 平滑和 SAR 的第一根有效值采用不同初始化，因此前期数值可能有轻微差异；本项目现在明确使用文档第 6 节的初始化规则。
2. **MACD 红柱字段命名**：`is_macd_red` 的实际规则是 `MACD > 0`，没有判断当前柱是否比上一根变大；如果业务要求“红柱变长”，应另设规则。

### 7.3 当前实现中值得关注的策略风险

- L2 指标是按 `etf_code + period` 独立计算的；每个分组必须按时间升序，并且历史回看长度要足够，否则 EMA、RSI、ADX、SAR 的早期值会受到初始化影响。
- 当前五维共振 SQL 使用四个周期 `day/week/month/season`，判断各周期的 `is_macd_golden_state`、`is_macd_red` 与 `is_sar_bullish`，45 日线按业务约定默认通过。它没有直接使用 RSI、KDJ、MA 等全部指标作为共振条件。
- 因此，文档和页面中的“指标”是 L2 全量计算结果，而“共振”是 L3 选取部分 L2 状态字段后的策略判断，两者不能混为同一个公式。
