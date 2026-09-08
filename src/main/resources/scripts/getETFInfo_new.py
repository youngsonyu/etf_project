# -*- coding: utf-8 -*-
"""
ETF 数据落库 + 技术指标计算一体化脚本（支持断点续跑和失败重试）

功能说明：
1. ETF基础信息、交易日历、K线、PCF、份额、IOPV 落库
2. ETF技术指标计算（MACD, MA, RSI, KDJ, BOLL, ATR/ADX, SAR）
3. 所有配置从 sys_param 表读取（需先执行 SQL 脚本初始化）
4. 自动追踪跑批进度，支持断点续跑和失败重试

前置条件：
- 已执行 init_sys_params.sql 初始化数据库表
- 已正确配置 sys_param 表中的参数（特别是 AD_PASSWORD）

运行方式：
python etf_etl.py
"""

import math
import sys
import time
import traceback
from datetime import datetime, date
from typing import List, Tuple, Dict, Any, Optional

import numpy as np
import pandas as pd
from sqlalchemy import create_engine, text

import AmazingData as ad


# =========================
# 周期映射
# =========================

PERIOD_MAPPING = {
    "min1": ad.constant.Period.min1.value,
    "min3": ad.constant.Period.min3.value,
    "min5": ad.constant.Period.min5.value,
    "min10": ad.constant.Period.min10.value,
    "min15": ad.constant.Period.min15.value,
    "min30": ad.constant.Period.min30.value,
    "min60": ad.constant.Period.min60.value,
    "min120": ad.constant.Period.min120.value,
    "day": ad.constant.Period.day.value,
    "week": ad.constant.Period.week.value,
    "month": ad.constant.Period.month.value,
    "season": ad.constant.Period.season.value,
    "year": ad.constant.Period.year.value,
}

# 任务名称常量
JOB_NAME = "ETF_DATA_ETL"
_LAST_GENERATED_ID = 0


def generate_bigint_id() -> int:
    """生成兼容 BIGINT 的主键值（应用侧生成，避免依赖 AUTO_INCREMENT）"""
    global _LAST_GENERATED_ID
    candidate = int(time.time_ns())
    if candidate <= _LAST_GENERATED_ID:
        candidate = _LAST_GENERATED_ID + 1
    _LAST_GENERATED_ID = candidate
    return candidate


class Config:
    """配置类，从 sys_param 表加载参数"""
    FORCE_STRING_KEYS = {
        "AD_USERNAME", "AD_PASSWORD", "AD_HOST",
        "MYSQL_HOST", "MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_DB", "MYSQL_CHARSET",
        "SOURCE", "DEFAULT_CALENDAR_MARKET", "RUN_MODE", "KLINE_PERIOD_NAMES", "TARGET_PERIODS"
    }
    
    _instance = None
    _loaded = False
    
    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance
    
    def __init__(self):
        if not self._loaded:
            self._params: Dict[str, Any] = {}
            self._mysql_engine = None
            self._loaded = True
    
    def load_from_db(self, mysql_engine):
        """从数据库加载配置"""
        self._mysql_engine = mysql_engine
        try:
            with mysql_engine.connect() as conn:
                result = conn.execute(text(
                    "SELECT param_key, param_value FROM sys_param WHERE is_active = 1"
                ))
                for row in result:
                    self._parse_param(row[0], row[1])
            print(f"[INFO] 从 sys_param 加载配置完成，共 {len(self._params)} 项")
        except Exception as e:
            raise RuntimeError(f"从 sys_param 加载配置失败: {e}")
        
        # 解析复杂类型
        self._parse_complex_params()
        self._apply_defaults_and_normalize()
    
    def _parse_param(self, key: str, value: str):
        """解析参数值（自动转换类型）"""
        if key in self.FORCE_STRING_KEYS:
            self._params[key] = "" if value is None else str(value).strip()
            return

        if value is None:
            self._params[key] = ""
            return

        value = str(value).strip()

        # 布尔类型
        if value.lower() in ('true', '1', 'yes', 'on'):
            self._params[key] = True
        elif value.lower() in ('false', '0', 'no', 'off'):
            self._params[key] = False
        # 整数类型
        elif value.isdigit():
            self._params[key] = int(value)
        # 浮点数类型
        else:
            try:
                self._params[key] = float(value)
            except ValueError:
                self._params[key] = value
    
    def _parse_complex_params(self):
        """解析复杂类型参数（列表等）"""
        # K线周期名称列表
        if "KLINE_PERIOD_NAMES" in self._params:
            if isinstance(self._params["KLINE_PERIOD_NAMES"], str):
                self._params["KLINE_PERIOD_NAMES"] = [x.strip() for x in self._params["KLINE_PERIOD_NAMES"].split(",")]
        
        # 技术指标目标周期列表
        if "TARGET_PERIODS" in self._params:
            if isinstance(self._params["TARGET_PERIODS"], str):
                self._params["TARGET_PERIODS"] = [x.strip() for x in self._params["TARGET_PERIODS"].split(",")]
        
        # K线周期列表（用于API调用）
        kline_periods = []
        for period_name in self._params.get("KLINE_PERIOD_NAMES", []):
            if period_name in PERIOD_MAPPING:
                kline_periods.append((period_name, PERIOD_MAPPING[period_name]))
        self._params["KLINE_PERIODS"] = kline_periods

    def _apply_defaults_and_normalize(self):
        """补齐默认参数并规范化关键开关"""
        defaults = {
            "AD_USERNAME": "410500122546",
            "AD_HOST": "101.230.159.234",
            "AD_PORT": 8600,
            "MYSQL_HOST": "127.0.0.1",
            "MYSQL_PORT": 3306,
            "MYSQL_USER": "root",
            "MYSQL_PASSWORD": "",
            "MYSQL_DB": "amazingdata_etf",
            "MYSQL_CHARSET": "utf8mb4",
            "SOURCE": "AmazingData",
            "DEFAULT_CALENDAR_MARKET": "SH",
            "MAX_CODE_BATCH": 300,
            "RUN_MODE": "recent",
            "RECENT_DAYS": 1,
            "INCLUDE_TODAY": False,
            "MAX_PROCESS_DAYS": 30,
            "KLINE_PERIOD_NAMES": ["day", "week", "month", "season", "year"],
            "TARGET_PERIODS": ["day", "week", "month", "season"],
            "LOOKBACK_BARS": 500,
            "L2_UPSERT_BATCH_SIZE": 2000,
            "SAR_N": 4,
            "SAR_AF_STEP": 0.02,
            "SAR_AF_MAX": 0.20,
            "ENABLE_ETF_SECURITY_MASTER": True,
            "ENABLE_TRADE_CALENDAR": True,
            "ENABLE_ETF_MARKET_SNAPSHOT": False,
            "ENABLE_ETF_MARKET_KLINE": True,
            "ENABLE_ETF_PCF_INFO": True,
            "ENABLE_ETF_PCF_CONSTITUENT": True,
            "ENABLE_ETF_FUND_SHARE": True,
            "ENABLE_ETF_FUND_IOPV": True,
            "ENABLE_TA_INDICATOR": True,
        }

        for key, value in defaults.items():
            if key not in self._params:
                self._params[key] = value

        run_mode = str(self._params.get("RUN_MODE", "recent")).strip().lower()
        if run_mode in ("inc", "increment"):
            run_mode = "incremental"
        elif run_mode == "checkpoint":
            run_mode = "incremental"
        if run_mode not in ("full", "recent", "incremental"):
            print(f"[WARN] RUN_MODE={run_mode} 非法，自动回退为 recent")
            run_mode = "recent"
        self._params["RUN_MODE"] = run_mode

        recent_days = self._params.get("RECENT_DAYS", 1)
        try:
            recent_days = int(recent_days)
        except (TypeError, ValueError):
            recent_days = 1
        if recent_days <= 0:
            recent_days = 1
        self._params["RECENT_DAYS"] = recent_days

        max_process_days = self._params.get("MAX_PROCESS_DAYS", 30)
        try:
            max_process_days = int(max_process_days)
        except (TypeError, ValueError):
            max_process_days = 30
        if max_process_days <= 0:
            max_process_days = 30
        self._params["MAX_PROCESS_DAYS"] = max_process_days

        if isinstance(self._params.get("TARGET_PERIODS"), str):
            self._params["TARGET_PERIODS"] = [
                x.strip() for x in self._params["TARGET_PERIODS"].split(",") if x.strip()
            ]
        if isinstance(self._params.get("KLINE_PERIOD_NAMES"), str):
            self._params["KLINE_PERIOD_NAMES"] = [
                x.strip() for x in self._params["KLINE_PERIOD_NAMES"].split(",") if x.strip()
            ]

        if not self._params.get("KLINE_PERIODS"):
            kline_periods = []
            for period_name in self._params.get("KLINE_PERIOD_NAMES", []):
                if period_name in PERIOD_MAPPING:
                    kline_periods.append((period_name, PERIOD_MAPPING[period_name]))
            self._params["KLINE_PERIODS"] = kline_periods

    def is_incremental_mode(self) -> bool:
        return self.RUN_MODE in ("recent", "incremental")
    
    def get(self, key: str, default=None):
        return self._params.get(key, default)
    
    def __getattr__(self, name):
        if name in self._params:
            return self._params[name]
        raise AttributeError(f"Config has no attribute '{name}'")
    
    def get_mysql_engine(self):
        return self._mysql_engine


# 全局配置实例
config = Config()


# =========================
# 工具函数
# =========================

def get_mysql_engine_from_db():
    """
    从数据库读取 MySQL 连接配置并创建引擎
    注意：这个方法需要在 config 加载之前使用，用于加载 config 本身
    """
    # 先使用默认连接信息读取配置
    temp_engine = None
    try:
        # 尝试从默认连接读取（假设已经初始化过）
        temp_engine = create_engine(
            "mysql+pymysql://root@127.0.0.1:3306/amazingdata_etf?charset=utf8mb4",
            pool_pre_ping=True
        )
        with temp_engine.connect() as conn:
            result = conn.execute(text(
                "SELECT param_key, param_value FROM sys_param WHERE param_key IN ('MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_USER', 'MYSQL_PASSWORD', 'MYSQL_DB', 'MYSQL_CHARSET') AND is_active = 1"
            ))
            mysql_config = {}
            for row in result:
                mysql_config[row[0]] = row[1]
            
            if mysql_config:
                url = (
                    f"mysql+pymysql://{mysql_config.get('MYSQL_USER', 'root')}:{mysql_config.get('MYSQL_PASSWORD', '')}"
                    f"@{mysql_config.get('MYSQL_HOST', '127.0.0.1')}:{mysql_config.get('MYSQL_PORT', 3306)}/{mysql_config.get('MYSQL_DB', 'amazingdata_etf')}"
                    f"?charset={mysql_config.get('MYSQL_CHARSET', 'utf8mb4')}"
                )
                return create_engine(url, pool_pre_ping=True, future=True)
    except Exception as e:
        print(f"[WARN] 无法从数据库读取MySQL配置: {e}")
    finally:
        if temp_engine:
            temp_engine.dispose()
    
    # 使用默认配置
    url = "mysql+pymysql://root:Dwb5201314.@127.0.0.1:3306/amazingdata_etf?charset=utf8mb4"
    return create_engine(url, pool_pre_ping=True, future=True)


def get_mysql_engine_from_config():
    """从已加载的 config 创建 MySQL 引擎"""
    url = (
        f"mysql+pymysql://{config.MYSQL_USER}:{config.MYSQL_PASSWORD}"
        f"@{config.MYSQL_HOST}:{config.MYSQL_PORT}/{config.MYSQL_DB}"
        f"?charset={config.MYSQL_CHARSET}"
    )
    return create_engine(url, pool_pre_ping=True, future=True)


def safe_date(v):
    if v is None:
        return None
    if isinstance(v, date) and not isinstance(v, datetime):
        return v
    if isinstance(v, datetime):
        return v.date()
    if pd.isna(v):
        return None
    if isinstance(v, (int, float)):
        s = str(int(v))
        if len(s) == 8:
            return datetime.strptime(s, "%Y%m%d").date()
    if isinstance(v, str):
        v = v.strip()
        if not v:
            return None
        for fmt in ("%Y-%m-%d", "%Y%m%d", "%Y/%m/%d"):
            try:
                return datetime.strptime(v, fmt).date()
            except Exception:
                pass
    return None


def safe_datetime(v):
    if v is None or pd.isna(v):
        return None
    if isinstance(v, datetime):
        return v
    if isinstance(v, pd.Timestamp):
        return v.to_pydatetime()
    if isinstance(v, date):
        return datetime.combine(v, time.min)
    if isinstance(v, str):
        v = v.strip()
        if not v:
            return None
        for fmt in (
            "%Y-%m-%d %H:%M:%S.%f",
            "%Y-%m-%d %H:%M:%S",
            "%Y/%m/%d %H:%M:%S",
            "%Y-%m-%d",
            "%Y%m%d",
            "%Y/%m/%d",
        ):
            try:
                dt = datetime.strptime(v, fmt)
                if fmt in ("%Y-%m-%d", "%Y%m%d", "%Y/%m/%d"):
                    return datetime.combine(dt.date(), time.min)
                return dt
            except Exception:
                pass
    if isinstance(v, (int, float)):
        s = str(int(v))
        if len(s) == 8:
            d = datetime.strptime(s, "%Y%m%d").date()
            return datetime.combine(d, time.min)
    return None


def safe_decimal(v):
    if v is None or pd.isna(v):
        return None
    try:
        f = float(v)
        if math.isinf(f) or math.isnan(f):
            return None
        return f
    except Exception:
        return None


def safe_int(v):
    if v is None or pd.isna(v):
        return None
    try:
        return int(v)
    except Exception:
        return None


def split_code_market(full_code: str) -> Tuple[str, str]:
    if not full_code:
        return full_code, None
    if "." in full_code:
        _, market = full_code.split(".", 1)
        return full_code, market.upper()
    return full_code, None


def chunked(lst: List, n: int):
    for i in range(0, len(lst), n):
        yield lst[i:i + n]


def normalize_dataframe(df: pd.DataFrame) -> pd.DataFrame:
    if df is None:
        return pd.DataFrame()
    if isinstance(df, pd.Series):
        df = df.to_frame().T
    df = df.copy()
    df.columns = [str(c).strip() for c in df.columns]
    return df


def deduplicate_df(df: pd.DataFrame, subset: List[str]) -> pd.DataFrame:
    if df is None or df.empty:
        return df
    subset = [c for c in subset if c in df.columns]
    if not subset:
        return df
    return df.drop_duplicates(subset=subset, keep="last").reset_index(drop=True)


def log_df_preview(name: str, df: pd.DataFrame, n: int = 1):
    if df is None:
        print(f"[INFO] {name} = None")
        return
    if not isinstance(df, pd.DataFrame):
        print(f"[INFO] {name} type={type(df)}")
        return
    print(f"[INFO] {name} rows={len(df)}")
    print(f"[INFO] {name} columns={list(df.columns)}")
    if len(df) > 0:
        try:
            print(f"[INFO] {name} first_row={df.head(n).to_dict('records')[0]}")
        except Exception as e:
            print(f"[WARN] {name} first_row 打印失败: {e}")


def pick_trade_time_from_row(r):
    candidate_cols = [
        "trade_time", "TRADE_TIME", "kline_time", "KLINE_TIME",
        "datetime", "DATETIME", "time", "TIME", "trade_date",
        "TRADE_DATE", "date", "DATE", "index",
    ]
    for col in candidate_cols:
        if col in r.index:
            v = r.get(col)
            dt = safe_datetime(v)
            if dt is not None:
                return dt
            d = safe_date(v)
            if d is not None:
                return datetime.combine(d, time.min)
    return None


def pick_change_date_from_row(r):
    candidate_cols = [
        "change_date", "CHANGE_DATE", "date", "DATE",
        "trade_date", "TRADE_DATE", "index",
    ]
    for col in candidate_cols:
        if col in r.index:
            d = safe_date(r.get(col))
            if d is not None:
                return d
            dt = safe_datetime(r.get(col))
            if dt is not None:
                return dt.date()
    return None


def pick_etf_code_column(df: pd.DataFrame):
    candidate_cols = [
        "etf_code", "ETF_CODE", "code_market", "CODE_MARKET",
        "code", "CODE", "wind_code", "WIND_CODE", "security_code",
        "SECURITY_CODE", "ticker", "TICKER", "symbol_code",
        "SYMBOL_CODE", "index",
    ]
    for col in candidate_cols:
        if col in df.columns:
            return col
    return None


def print_df_basic(name: str, df: pd.DataFrame):
    print(f"[INFO] {name} rows={len(df)}")
    print(f"[INFO] {name} columns={list(df.columns)}")
    if not df.empty:
        try:
            print(f"[INFO] {name} first_row={df.head(1).to_dict('records')[0]}")
        except Exception as e:
            print(f"[WARN] {name} first_row 打印失败: {e}")


def is_duplicate_key_error(err: Exception) -> bool:
    msg = str(getattr(err, "orig", err))
    return ("1062" in msg) or ("Duplicate entry" in msg)


def get_latest_batch_no(engine) -> str:
    """获取 etf_market_kline 表中最新的批次号"""
    sql = """
    SELECT etl_batch_no 
    FROM etf_market_kline 
    WHERE etl_batch_no IS NOT NULL 
    ORDER BY created_at DESC, etl_batch_no DESC 
    LIMIT 1
    """
    try:
        result = pd.read_sql(text(sql), engine)
        if not result.empty:
            batch_no = result.iloc[0]['etl_batch_no']
            print(f"[INFO] 获取到最新批次号: {batch_no}")
            return batch_no
        else:
            print("[WARN] 未找到任何批次号，将使用全量模式计算技术指标")
            return None
    except Exception as e:
        print(f"[WARN] 获取最新批次号失败: {e}")
        return None


# =========================
# 跑批状态管理
# =========================

class BatchStatusManager:
    """跑批状态管理器，负责记录和查询跑批进度"""
    
    def __init__(self, engine):
        self.engine = engine
    
    def get_last_success_trade_date(self, checkpoint_key: str = "ETF_KLINE_DAY") -> Optional[int]:
        """获取最后成功处理的交易日"""
        sql = "SELECT last_trade_date FROM etl_checkpoint WHERE checkpoint_key = :key"
        try:
            result = pd.read_sql(text(sql), self.engine, params={"key": checkpoint_key})
            if not result.empty:
                return int(result.iloc[0]['last_trade_date'])
        except Exception as e:
            print(f"[WARN] 获取检查点失败: {e}")
        return None
    
    def update_checkpoint(self, checkpoint_key: str, trade_date: int, batch_no: str):
        """更新检查点"""
        sql = """
        INSERT INTO etl_checkpoint (id, checkpoint_key, last_trade_date, last_batch_no)
        VALUES (:id, :key, :trade_date, :batch_no)
        ON DUPLICATE KEY UPDATE
            last_trade_date = VALUES(last_trade_date),
            last_batch_no = VALUES(last_batch_no)
        """
        with self.engine.begin() as conn:
            conn.execute(text(sql), {
                "id": generate_bigint_id(),
                "key": checkpoint_key,
                "trade_date": trade_date,
                "batch_no": batch_no
            })
        print(f"[INFO] 更新检查点: {checkpoint_key} -> {trade_date} (batch: {batch_no})")
    
    def start_batch(self, batch_no: str, trade_date_start: int, trade_date_end: int) -> bool:
        """开始一个新的批次"""
        check_sql = """
        SELECT id FROM etl_batch_status 
        WHERE job_name = :job_name AND status = 'RUNNING'
        """
        with self.engine.connect() as conn:
            result = conn.execute(text(check_sql), {"job_name": JOB_NAME})
            if result.fetchone():
                print("[WARN] 存在未完成的批次，请先处理或手动清理")
                return False
        
        sql = """
        INSERT INTO etl_batch_status 
        (id, batch_no, job_name, start_time, status, trade_date_start, trade_date_end)
        VALUES (:id, :batch_no, :job_name, :start_time, 'RUNNING', :start_date, :end_date)
        """
        with self.engine.begin() as conn:
            conn.execute(text(sql), {
                "id": generate_bigint_id(),
                "batch_no": batch_no,
                "job_name": JOB_NAME,
                "start_time": datetime.now(),
                "start_date": trade_date_start,
                "end_date": trade_date_end
            })
        print(f"[INFO] 开始批次: {batch_no}, 处理日期范围: {trade_date_start} - {trade_date_end}")
        return True
    
    def finish_batch(self, batch_no: str, status: str, error_message: str = None, 
                     etf_count: int = None, kline_count: int = None):
        """完成一个批次"""
        sql = """
        UPDATE etl_batch_status 
        SET end_time = :end_time, status = :status, error_message = :error_msg,
            etf_count = :etf_count, kline_count = :kline_count
        WHERE batch_no = :batch_no
        """
        with self.engine.begin() as conn:
            conn.execute(text(sql), {
                "end_time": datetime.now(),
                "status": status,
                "error_msg": error_message,
                "etf_count": etf_count,
                "kline_count": kline_count,
                "batch_no": batch_no
            })
        print(f"[INFO] 批次完成: {batch_no}, 状态: {status}")
    
    def add_kline_progress(self, batch_no: str, delta: int):
        """累加批次的已写入条数（供前端实时查看进度）"""
        if not delta:
            return
        sql = """
        UPDATE etl_batch_status
        SET kline_count = COALESCE(kline_count, 0) + :delta
        WHERE batch_no = :batch_no
        """
        with self.engine.begin() as conn:
            conn.execute(text(sql), {"delta": delta, "batch_no": batch_no})

    def get_failed_batches(self) -> List[dict]:
        """获取失败的批次"""
        sql = """
        SELECT batch_no, trade_date_start, trade_date_end, error_message
        FROM etl_batch_status 
        WHERE job_name = :job_name AND status IN ('FAILED', 'PARTIAL')
        ORDER BY id ASC
        """
        result = pd.read_sql(text(sql), self.engine, params={"job_name": JOB_NAME})
        return result.to_dict('records')


# =========================
# 日期计算器
# =========================

class TradeDateCalculator:
    """交易日计算器，根据检查点自动计算需要处理的日期范围"""
    
    def __init__(self, engine, extractor):
        self.engine = engine
        self.extractor = extractor
        self.batch_manager = BatchStatusManager(engine)
    
    def get_trade_dates_to_run(self) -> Tuple[List[int], int, int]:
        """
        获取需要处理的交易日列表
        返回: (trade_dates, start_date, end_date)
        """
        if self.extractor.calendar is None:
            self.extractor.init_calendar()
        all_trade_dates = self.extractor.calendar
        
        if not all_trade_dates:
            raise ValueError("无法获取交易日历")
        
        run_mode = str(config.RUN_MODE).strip().lower()
        include_today = bool(config.INCLUDE_TODAY)
        today_int = int(datetime.now().strftime("%Y%m%d"))
        candidate_dates = all_trade_dates[:] if include_today else [d for d in all_trade_dates if d != today_int]

        if not candidate_dates:
            print("[INFO] 可用交易日为空，退出")
            return [], None, None

        if run_mode == "full":
            trade_dates = candidate_dates
            start_date = trade_dates[0]
            end_date = trade_dates[-1]
            print(f"[INFO] full 模式: {len(trade_dates)} 天, 范围: {start_date} - {end_date}")
            return trade_dates, start_date, end_date

        if run_mode == "recent":
            recent_days = config.RECENT_DAYS
            trade_dates = candidate_dates[-recent_days:]
            start_date = trade_dates[0]
            end_date = trade_dates[-1]
            print(f"[INFO] recent 模式: {len(trade_dates)} 天, 范围: {start_date} - {end_date}")
            return trade_dates, start_date, end_date

        # incremental 模式：按检查点断点续跑 + 失败批次重试
        last_success_date = self.batch_manager.get_last_success_trade_date()

        if last_success_date is None:
            start_idx = 0
            print("[INFO] incremental 模式首次运行，从第一个交易日开始")
        else:
            try:
                last_idx = candidate_dates.index(last_success_date)
                start_idx = last_idx + 1
                print(f"[INFO] 断点续跑：上次成功处理到 {last_success_date}，从下一个交易日开始")
            except ValueError:
                start_idx = 0
                print(f"[WARN] 检查点日期 {last_success_date} 不在当前交易日范围，从第一个交易日开始")

        failed_batches = self.batch_manager.get_failed_batches()
        if failed_batches:
            print(f"[INFO] 发现 {len(failed_batches)} 个失败的批次，将重试")
            earliest_fail_start = min(b["trade_date_start"] for b in failed_batches)
            try:
                fail_start_idx = candidate_dates.index(earliest_fail_start)
                if fail_start_idx < start_idx:
                    start_idx = fail_start_idx
                    print(f"[INFO] 重试模式：从 {earliest_fail_start} 开始重新处理")
            except ValueError:
                pass

        end_idx = len(candidate_dates) - 1
        if start_idx > end_idx:
            print("[INFO] 没有需要处理的新交易日")
            return [], None, None

        trade_dates = candidate_dates[start_idx:end_idx + 1]
        start_date = trade_dates[0]
        end_date = trade_dates[-1]

        max_days = config.MAX_PROCESS_DAYS
        if len(trade_dates) > max_days:
            trade_dates = trade_dates[-max_days:]
            start_date = trade_dates[0]
            print(f"[WARN] 待处理交易日超过 {max_days} 天，限制为最近 {max_days} 天")

        print(f"[INFO] incremental 模式: {len(trade_dates)} 天, 范围: {start_date} - {end_date}")
        return trade_dates, start_date, end_date


# =========================
# AmazingData 登录 / 退出
# =========================

def login_amazingdata():
    print("[INFO] 登录 AmazingData...")
    username = "" if config.AD_USERNAME is None else str(config.AD_USERNAME).strip()
    password = "" if config.AD_PASSWORD is None else str(config.AD_PASSWORD)
    host = "" if config.AD_HOST is None else str(config.AD_HOST).strip()
    if not username:
        raise ValueError("AD_USERNAME 为空，请检查 sys_param")

    ad.login(
        username=username,
        password=password,
        host=host,
        port=config.AD_PORT,
    )
    print("[INFO] 登录成功")


def logout_amazingdata():
    print("[INFO] 尝试退出 AmazingData...")
    try:
        if hasattr(ad, "logout") and callable(ad.logout):
            ad.logout(config.AD_USERNAME)
            print("[INFO] AmazingData 已退出（logout）")
            return
    except Exception as e:
        print(f"[WARN] 调用 ad.logout() 失败: {e}")

    try:
        if hasattr(ad, "close") and callable(ad.close):
            ad.close()
            print("[INFO] AmazingData 已关闭（close）")
            return
    except Exception as e:
        print(f"[WARN] 调用 ad.close() 失败: {e}")

    try:
        if hasattr(ad, "disconnect") and callable(ad.disconnect):
            ad.disconnect()
            print("[INFO] AmazingData 已断开（disconnect）")
            return
    except Exception as e:
        print(f"[WARN] 调用 ad.disconnect() 失败: {e}")

    print("[WARN] 未找到可用的 AmazingData 退出方法，跳过退出")


# =========================
# 数据抽取
# =========================

class AmazingDataETFExtractor:

    def __init__(self):
        self.base_data = ad.BaseData()
        self.info_data = ad.InfoData()
        self.calendar = None
        self.market_data = None
        self._kline_debug_printed = False
        self._pcf_info_debug_printed = False
        self._fund_share_debug_printed = False
        self._fund_iopv_debug_printed = False
        self.batch_no = datetime.now().strftime("%Y%m%d_%H%M%S")

    def init_calendar(self):
        print(f"[INFO] 开始获取交易日历 market={config.DEFAULT_CALENDAR_MARKET}")
        cal = self.base_data.get_calendar(data_type='str', market=config.DEFAULT_CALENDAR_MARKET)

        if cal is None:
            raise ValueError("get_calendar 返回 None")

        print(f"[INFO] 交易日历长度: {len(cal)}")
        print(f"[INFO] 交易日历前5项: {list(cal[:5])}")

        converted = []
        for x in cal:
            if isinstance(x, int):
                converted.append(x)
            else:
                s = str(x).strip().replace("-", "").replace("/", "")
                if len(s) == 8 and s.isdigit():
                    converted.append(int(s))
                else:
                    raise ValueError(f"交易日格式无法识别: {x}")

        self.calendar = converted
        print(f"[INFO] 转换后交易日历长度: {len(self.calendar)}")
        print(f"[INFO] 转换后最后一个交易日: {self.calendar[-1]}")

        self.market_data = ad.MarketData(cal)
        print("[INFO] MarketData 初始化成功")
        return self.calendar

    def get_latest_trade_date(self) -> int:
        if self.calendar is None:
            self.init_calendar()
        return self.calendar[-1]

    def get_etf_code_list(self) -> List[str]:
        codes_sh = self.base_data.get_code_list(security_type='ETF') or []
        codes_sz = self.base_data.get_code_list(security_type='EXTRA_ETF') or []
        codes = list(set(codes_sh + codes_sz))
        print(f"[INFO] get_etf_code_list first_code={codes[0] if codes else None}")
        return codes

    def get_code_info(self) -> pd.DataFrame:
        df = self.base_data.get_code_info(security_type='EXTRA_ETF')
        if df is None:
            return pd.DataFrame()

        df = normalize_dataframe(df)
        df = df.reset_index()

        found_code_col = pick_etf_code_column(df)
        if found_code_col is not None and found_code_col != "etf_code":
            df = df.rename(columns={found_code_col: "etf_code"})

        if "etf_code" not in df.columns:
            print("[WARN] get_code_info 未识别到 etf_code 列")
            print("[WARN] 当前列名:", list(df.columns))
            df["etf_code"] = None

        log_df_preview("get_code_info", df)
        return df

    def get_trade_calendar(self, market='SH') -> pd.DataFrame:
        cal = self.base_data.get_calendar(data_type='str', market=market)
        rows = [{"market": market, "trade_date": safe_date(x), "is_open": 1} for x in cal]
        df = pd.DataFrame(rows)
        log_df_preview(f"get_trade_calendar[{market}]", df)
        return df

    def get_kline_for_dates(self, etf_codes: List[str], trade_dates: List[int]) -> pd.DataFrame:
        if not trade_dates:
            return pd.DataFrame()

        if self.market_data is None:
            raise RuntimeError("market_data 未初始化，无法拉取K线；请检查 ENABLE_ETF_MARKET_KLINE 开关是否被意外开启（例如 --ta-only 模式覆盖顺序问题）")

        begin_date = min(trade_dates)
        end_date = max(trade_dates)
        all_rows = []
        skipped_null_trade_time = 0

        print(f"[INFO] 拉取K线区间 begin_date={begin_date}, end_date={end_date}")

        for period_name, period_value in config.KLINE_PERIODS:
            print(f"[INFO] 拉取K线周期 period={period_name}")
            for code_batch in chunked(etf_codes, config.MAX_CODE_BATCH):
                kline_dict = self.market_data.query_kline(
                    code_batch,
                    begin_date=begin_date,
                    end_date=end_date,
                    period=period_value
                )
                for etf_code, df in kline_dict.items():
                    if df is None or len(df) == 0:
                        continue

                    if not self._kline_debug_printed:
                        print(f"[DEBUG] K线原始样例 period={period_name}, etf_code={etf_code}")
                        self._kline_debug_printed = True

                    df = normalize_dataframe(df)
                    df = df.reset_index()

                    for _, r in df.iterrows():
                        trade_time = pick_trade_time_from_row(r)
                        if trade_time is None:
                            skipped_null_trade_time += 1
                            continue

                        all_rows.append({
                            "etf_code": etf_code,
                            "period": period_name,
                            "trade_time": trade_time,
                            "open_price": safe_decimal(r.get("open") or r.get("OPEN")),
                            "high_price": safe_decimal(r.get("high") or r.get("HIGH")),
                            "low_price": safe_decimal(r.get("low") or r.get("LOW")),
                            "close_price": safe_decimal(r.get("close") or r.get("CLOSE")),
                            "volume": safe_int(r.get("volume") or r.get("VOLUME")),
                            "amount": safe_decimal(r.get("amount") or r.get("AMOUNT")),
                            "source": config.SOURCE,
                            "etl_batch_no": self.batch_no,
                        })

        print(f"[INFO] K线跳过空 trade_time 记录数: {skipped_null_trade_time}")
        df = pd.DataFrame(all_rows)
        log_df_preview("get_kline_for_dates", df)
        return deduplicate_df(df, ["etf_code", "period", "trade_time"])

    def get_etf_pcf_latest(self, etf_codes: List[str]) -> Tuple[pd.DataFrame, pd.DataFrame]:
        latest_trade_date = self.get_latest_trade_date()
        info_rows = []
        constituent_rows = []

        for code_batch in chunked(etf_codes, config.MAX_CODE_BATCH):
            pcf_info, pcf_constituent = self.base_data.get_etf_pcf(code_batch)

            if pcf_info is not None:
                if not self._pcf_info_debug_printed:
                    print(f"[DEBUG] pcf_info 原始 type={type(pcf_info)}")
                    self._pcf_info_debug_printed = True

                if len(pcf_info) > 0:
                    pcf_info = normalize_dataframe(pcf_info)
                    pcf_info = pcf_info.reset_index()

                    found_code_col = pick_etf_code_column(pcf_info)
                    if found_code_col is not None and found_code_col != "etf_code":
                        pcf_info = pcf_info.rename(columns={found_code_col: "etf_code"})

                    if "etf_code" not in pcf_info.columns:
                        pcf_info["etf_code"] = None

                    for _, r in pcf_info.iterrows():
                        etf_code = r.get("etf_code")
                        if etf_code is None or str(etf_code).strip() == "" or str(etf_code).lower() == "nan":
                            continue

                        info_rows.append({
                            "etf_code": str(etf_code).strip(),
                            "trading_day": safe_date(
                                r.get("trading_day") or r.get("TRADING_DAY") or latest_trade_date
                            ),
                            "pre_trading_day": safe_date(r.get("pre_trading_day") or r.get("PRE_TRADING_DAY")),
                            "creation_redemption_unit": safe_int(r.get("creation_redemption_unit") or r.get("CREATION_REDEMPTION_UNIT")),
                            "max_cash_ratio": safe_decimal(r.get("max_cash_ratio") or r.get("MAX_CASH_RATIO")),
                            "publish": r.get("publish") or r.get("PUBLISH"),
                            "creation": r.get("creation") or r.get("CREATION"),
                            "redemption": r.get("redemption") or r.get("REDEMPTION"),
                            "creation_redemption_switch": r.get("creation_redemption_switch") or r.get("CREATION_REDEMPTION_SWITCH"),
                            "record_num": safe_int(r.get("record_num") or r.get("RECORD_NUM")),
                            "total_record_num": safe_int(r.get("total_record_num") or r.get("TOTAL_RECORD_NUM")),
                            "estimate_cash_component": safe_decimal(r.get("estimate_cash_component") or r.get("ESTIMATE_CASH_COMPONENT")),
                            "cash_component": safe_decimal(r.get("cash_component") or r.get("CASH_COMPONENT")),
                            "nav_per_cu": safe_decimal(r.get("nav_per_cu") or r.get("NAV_PER_CU")),
                            "nav": safe_decimal(r.get("nav") or r.get("NAV")),
                            "symbol": r.get("symbol") or r.get("SYMBOL"),
                            "fund_management_company": r.get("fund_management_company") or r.get("FUND_MANAGEMENT_COMPANY"),
                            "underlying_security_id": r.get("underlying_security_id") or r.get("UNDERLYING_SECURITY_ID"),
                            "underlying_security_id_source": r.get("underlying_security_id_source") or r.get("UNDERLYING_SECURITY_ID_SOURCE"),
                            "dividend_per_cu": safe_decimal(r.get("dividend_per_cu") or r.get("DIVIDEND_PER_CU")),
                            "creation_limit": safe_decimal(r.get("creation_limit") or r.get("CREATION_LIMIT")),
                            "redemption_limit": safe_decimal(r.get("redemption_limit") or r.get("REDEMPTION_LIMIT")),
                            "creation_limit_per_user": safe_decimal(r.get("creation_limit_per_user") or r.get("CREATION_LIMIT_PER_USER")),
                            "redemption_limit_per_user": safe_decimal(r.get("redemption_limit_per_user") or r.get("REDEMPTION_LIMIT_PER_USER")),
                            "net_creation_limit": safe_decimal(r.get("net_creation_limit") or r.get("NET_CREATION_LIMIT")),
                            "net_redemption_limit": safe_decimal(r.get("net_redemption_limit") or r.get("NET_REDEMPTION_LIMIT")),
                            "net_creation_limit_per_user": safe_decimal(r.get("net_creation_limit_per_user") or r.get("NET_CREATION_LIMIT_PER_USER")),
                            "net_redemption_limit_per_user": safe_decimal(r.get("net_redemption_limit_per_user") or r.get("NET_REDEMPTION_LIMIT_PER_USER")),
                            "source": config.SOURCE,
                            "etl_batch_no": self.batch_no,
                        })

            if isinstance(pcf_constituent, dict):
                for etf_code, df in pcf_constituent.items():
                    if df is None or len(df) == 0:
                        continue
                    df = normalize_dataframe(df)
                    for _, r in df.iterrows():
                        constituent_code = (
                            r.get("index") or r.get("constituent_code") or r.get("CONSTITUENT_CODE")
                            or r.get("underlying_security_id") or r.get("UNDERLYING_SECURITY_ID")
                            or r.get("MARKET_CODE")
                        )
                        if not constituent_code:
                            continue
                        constituent_rows.append({
                            "etf_code": etf_code,
                            "trading_day": safe_date(latest_trade_date),
                            "constituent_code": str(constituent_code),
                            "underlying_symbol": r.get("underlying_symbol") or r.get("UNDERLYING_SYMBOL"),
                            "component_share": safe_decimal(r.get("component_share") or r.get("COMPONENT_SHARE")),
                            "substitute_flag": r.get("substitute_flag") or r.get("SUBSTITUTE_FLAG"),
                            "premium_ratio": safe_decimal(r.get("premium_ratio") or r.get("PREMIUM_RATIO")),
                            "discount_ratio": safe_decimal(r.get("discount_ratio") or r.get("DISCOUNT_RATIO")),
                            "creation_cash_substitute": safe_decimal(r.get("creation_cash_substitute") or r.get("CREATION_CASH_SUBSTITUTE")),
                            "redemption_cash_substitute": safe_decimal(r.get("redemption_cash_substitute") or r.get("REDEMPTION_CASH_SUBSTITUTE")),
                            "substitution_cash_amount": safe_decimal(r.get("substitution_cash_amount") or r.get("SUBSTITUTION_CASH_AMOUNT")),
                            "underlying_security_id": r.get("underlying_security_id") or r.get("UNDERLYING_SECURITY_ID"),
                            "source": config.SOURCE,
                            "etl_batch_no": self.batch_no,
                        })

        info_df = pd.DataFrame(info_rows)
        constituent_df = pd.DataFrame(constituent_rows)

        log_df_preview("get_etf_pcf_latest.info", info_df)
        log_df_preview("get_etf_pcf_latest.constituent", constituent_df)

        info_df = deduplicate_df(info_df, ["etf_code", "trading_day"])
        constituent_df = deduplicate_df(constituent_df, ["etf_code", "trading_day", "constituent_code"])
        return info_df, constituent_df

    def get_fund_share(self, etf_codes: List[str], trade_dates: List[int]) -> pd.DataFrame:
        rows = []
        min_date = min(trade_dates)
        max_date = max(trade_dates)

        for code_batch in chunked(etf_codes, config.MAX_CODE_BATCH):
            fund_share = self.info_data.get_fund_share(
                code_batch, is_local=False, begin_date=min_date, end_date=max_date
            )
            if isinstance(fund_share, dict):
                for etf_code, df in fund_share.items():
                    if df is None or len(df) == 0:
                        continue

                    if not self._fund_share_debug_printed:
                        print(f"[DEBUG] fund_share 原始样例 etf_code={etf_code}")
                        self._fund_share_debug_printed = True

                    df = normalize_dataframe(df)
                    df = df.reset_index()

                    for _, r in df.iterrows():
                        change_date = pick_change_date_from_row(r)
                        rows.append({
                            "etf_code": etf_code,
                            "change_date": change_date,
                            "ann_date": safe_date(r.get("ANN_DATE") or r.get("ann_date")),
                            "fund_share": safe_decimal(r.get("FUND_SHARE") or r.get("fund_share")),
                            "total_share": safe_decimal(r.get("TOTAL_SHARE") or r.get("total_share")),
                            "float_share": safe_decimal(r.get("FLOAT_SHARE") or r.get("float_share")),
                            "change_reason": r.get("CHANGE_REASON") or r.get("change_reason"),
                            "is_consolidated_data": safe_int(r.get("IS_CONSOLIDATED_DATA") or r.get("is_consolidated_data")),
                            "source": config.SOURCE,
                            "etl_batch_no": self.batch_no,
                        })

        df = pd.DataFrame(rows)
        log_df_preview("get_fund_share", df)
        return deduplicate_df(df, ["etf_code", "change_date"])

    def get_fund_iopv(self, etf_codes: List[str], trade_dates: List[int]) -> pd.DataFrame:
        rows = []
        min_date = min(trade_dates)
        max_date = max(trade_dates)

        for code_batch in chunked(etf_codes, config.MAX_CODE_BATCH):
            fund_iopv = self.info_data.get_fund_iopv(
                code_batch, is_local=False, begin_date=min_date, end_date=max_date
            )
            if isinstance(fund_iopv, dict):
                for etf_code, df in fund_iopv.items():
                    if df is None or len(df) == 0:
                        continue

                    if not self._fund_iopv_debug_printed:
                        print(f"[DEBUG] fund_iopv 原始样例 etf_code={etf_code}")
                        self._fund_iopv_debug_printed = True

                    df = normalize_dataframe(df)
                    for _, r in df.iterrows():
                        rows.append({
                            "etf_code": etf_code,
                            "price_date": safe_date(r.get("PRICE_DATE") or r.get("price_date")),
                            "iopv_nav": safe_decimal(r.get("IOPV_NAV") or r.get("iopv_nav")),
                            "source": config.SOURCE,
                            "etl_batch_no": self.batch_no,
                        })

        df = pd.DataFrame(rows)
        log_df_preview("get_fund_iopv", df)
        return deduplicate_df(df, ["etf_code", "price_date"])


# =========================
# 落库
# =========================

class MysqlWriter:
    def __init__(self, engine):
        self.engine = engine

    def execute_many(self, sql: str, rows: List[dict]):
        if not rows:
            return
        with self.engine.begin() as conn:
            conn.execute(text(sql), rows)

    def upsert_etf_security_master(self, df: pd.DataFrame, batch_no: str):
        if df.empty:
            print("[WARN] etf_security_master 没有可写入数据，跳过")
            return

        rows = []
        skipped = 0
        for _, r in df.iterrows():
            etf_code = r.get("etf_code")
            if etf_code is None or str(etf_code).strip() == "" or str(etf_code).lower() == "nan":
                skipped += 1
                continue

            full_code, market = split_code_market(str(etf_code).strip())
            rows.append({
                "id": generate_bigint_id(),
                "etf_code": full_code,
                "market": market,
                "symbol": None if pd.isna(r.get("symbol")) else r.get("symbol"),
                "security_status": None if pd.isna(r.get("security_status")) else str(r.get("security_status")),
                "pre_close": safe_decimal(r.get("pre_close")),
                "high_limited": safe_decimal(r.get("high_limited")),
                "low_limited": safe_decimal(r.get("low_limited")),
                "price_tick": safe_decimal(r.get("price_tick")),
                "is_active": 1,
                "source": config.SOURCE,
                "etl_batch_no": batch_no,
            })

        print(f"[INFO] etf_security_master 待写入 rows={len(rows)}, skipped_null_code={skipped}")
        if not rows:
            return

        sql = """
        INSERT INTO etf_security_master
        (id, etf_code, market, symbol, security_status, pre_close, high_limited, low_limited, price_tick, is_active, source, etl_batch_no)
        VALUES
        (:id, :etf_code, :market, :symbol, :security_status, :pre_close, :high_limited, :low_limited, :price_tick, :is_active, :source, :etl_batch_no)
        ON DUPLICATE KEY UPDATE
            market = VALUES(market), symbol = VALUES(symbol), security_status = VALUES(security_status),
            pre_close = VALUES(pre_close), high_limited = VALUES(high_limited), low_limited = VALUES(low_limited),
            price_tick = VALUES(price_tick), is_active = VALUES(is_active), source = VALUES(source),
            etl_batch_no = VALUES(etl_batch_no), updated_at = CURRENT_TIMESTAMP
        """
        self.execute_many(sql, rows)

    def upsert_trade_calendar(self, df: pd.DataFrame, batch_no: str):
        if df.empty:
            print("[WARN] trade_calendar 没有可写入数据，跳过")
            return

        rows = []
        for _, r in df.iterrows():
            rows.append({
                "id": generate_bigint_id(),
                "market": None if pd.isna(r.get("market")) else r.get("market"),
                "trade_date": safe_date(r.get("trade_date")),
                "is_open": safe_int(r.get("is_open")) or 1,
                "source": config.SOURCE,
                "etl_batch_no": batch_no,
            })

        print(f"[INFO] trade_calendar 待写入 rows={len(rows)}")

        sql = """
        INSERT INTO trade_calendar
        (id, market, trade_date, is_open, source, etl_batch_no)
        VALUES
        (:id, :market, :trade_date, :is_open, :source, :etl_batch_no)
        ON DUPLICATE KEY UPDATE
            is_open = VALUES(is_open), source = VALUES(source),
            etl_batch_no = VALUES(etl_batch_no), updated_at = CURRENT_TIMESTAMP
        """
        self.execute_many(sql, rows)

    def upsert_etf_market_kline(self, df: pd.DataFrame, batch_no: str):
        if df.empty:
            print("[WARN] etf_market_kline 没有可写入数据，跳过")
            return

        rows = []
        skipped = 0

        for r in df.to_dict("records"):
            if r.get("trade_time") is None:
                skipped += 1
                continue

            cleaned = {
                "id": generate_bigint_id(),
                "etf_code": None if pd.isna(r.get("etf_code")) else r.get("etf_code"),
                "period": None if pd.isna(r.get("period")) else r.get("period"),
                "trade_time": safe_datetime(r.get("trade_time")),
                "open_price": safe_decimal(r.get("open_price")),
                "high_price": safe_decimal(r.get("high_price")),
                "low_price": safe_decimal(r.get("low_price")),
                "close_price": safe_decimal(r.get("close_price")),
                "volume": safe_int(r.get("volume")),
                "amount": safe_decimal(r.get("amount")),
                "source": config.SOURCE,
                "etl_batch_no": batch_no,
            }
            rows.append(cleaned)

        print(f"[INFO] etf_market_kline 待写入 rows={len(rows)}, skipped_null_trade_time={skipped}")
        if not rows:
            return

        sql = """
        INSERT INTO etf_market_kline (
            id, etf_code, period, trade_time, open_price, high_price, low_price,
            close_price, volume, amount, source, etl_batch_no
        ) VALUES (
            :id, :etf_code, :period, :trade_time, :open_price, :high_price,
            :low_price, :close_price, :volume, :amount, :source, :etl_batch_no
        )
        ON DUPLICATE KEY UPDATE
            open_price = VALUES(open_price), high_price = VALUES(high_price), low_price = VALUES(low_price),
            close_price = VALUES(close_price), volume = VALUES(volume), amount = VALUES(amount),
            source = VALUES(source), etl_batch_no = VALUES(etl_batch_no), updated_at = CURRENT_TIMESTAMP
        """
        self.execute_many(sql, rows)

    def upsert_etf_pcf_info(self, df: pd.DataFrame, batch_no: str):
        if df.empty:
            print("[WARN] etf_pcf_info 没有可写入数据，跳过")
            return

        rows = []
        skipped = 0

        for r in df.to_dict("records"):
            if r.get("etf_code") is None or r.get("trading_day") is None:
                skipped += 1
                continue

            cleaned = {
                "id": generate_bigint_id(),
                "etf_code": None if pd.isna(r.get("etf_code")) else r.get("etf_code"),
                "trading_day": safe_date(r.get("trading_day")),
                "pre_trading_day": safe_date(r.get("pre_trading_day")),
                "creation_redemption_unit": safe_int(r.get("creation_redemption_unit")),
                "max_cash_ratio": safe_decimal(r.get("max_cash_ratio")),
                "publish": None if pd.isna(r.get("publish")) else r.get("publish"),
                "creation": None if pd.isna(r.get("creation")) else r.get("creation"),
                "redemption": None if pd.isna(r.get("redemption")) else r.get("redemption"),
                "creation_redemption_switch": None if pd.isna(r.get("creation_redemption_switch")) else r.get("creation_redemption_switch"),
                "record_num": safe_int(r.get("record_num")),
                "total_record_num": safe_int(r.get("total_record_num")),
                "estimate_cash_component": safe_decimal(r.get("estimate_cash_component")),
                "cash_component": safe_decimal(r.get("cash_component")),
                "nav_per_cu": safe_decimal(r.get("nav_per_cu")),
                "nav": safe_decimal(r.get("nav")),
                "symbol": None if pd.isna(r.get("symbol")) else r.get("symbol"),
                "fund_management_company": None if pd.isna(r.get("fund_management_company")) else r.get("fund_management_company"),
                "underlying_security_id": None if pd.isna(r.get("underlying_security_id")) else r.get("underlying_security_id"),
                "underlying_security_id_source": None if pd.isna(r.get("underlying_security_id_source")) else r.get("underlying_security_id_source"),
                "dividend_per_cu": safe_decimal(r.get("dividend_per_cu")),
                "creation_limit": safe_decimal(r.get("creation_limit")),
                "redemption_limit": safe_decimal(r.get("redemption_limit")),
                "creation_limit_per_user": safe_decimal(r.get("creation_limit_per_user")),
                "redemption_limit_per_user": safe_decimal(r.get("redemption_limit_per_user")),
                "net_creation_limit": safe_decimal(r.get("net_creation_limit")),
                "net_redemption_limit": safe_decimal(r.get("net_redemption_limit")),
                "net_creation_limit_per_user": safe_decimal(r.get("net_creation_limit_per_user")),
                "net_redemption_limit_per_user": safe_decimal(r.get("net_redemption_limit_per_user")),
                "source": config.SOURCE,
                "etl_batch_no": batch_no,
            }
            rows.append(cleaned)

        print(f"[INFO] etf_pcf_info 待写入 rows={len(rows)}, skipped_invalid={skipped}")
        if not rows:
            return

        sql = """
        INSERT INTO etf_pcf_info (
            id, etf_code, trading_day, pre_trading_day, creation_redemption_unit, max_cash_ratio,
            publish, creation, redemption, creation_redemption_switch, record_num, total_record_num,
            estimate_cash_component, cash_component, nav_per_cu, nav, symbol, fund_management_company,
            underlying_security_id, underlying_security_id_source, dividend_per_cu, creation_limit,
            redemption_limit, creation_limit_per_user, redemption_limit_per_user, net_creation_limit,
            net_redemption_limit, net_creation_limit_per_user, net_redemption_limit_per_user,
            source, etl_batch_no
        ) VALUES (
            :id, :etf_code, :trading_day, :pre_trading_day, :creation_redemption_unit, :max_cash_ratio,
            :publish, :creation, :redemption, :creation_redemption_switch, :record_num, :total_record_num,
            :estimate_cash_component, :cash_component, :nav_per_cu, :nav, :symbol, :fund_management_company,
            :underlying_security_id, :underlying_security_id_source, :dividend_per_cu, :creation_limit,
            :redemption_limit, :creation_limit_per_user, :redemption_limit_per_user, :net_creation_limit,
            :net_redemption_limit, :net_creation_limit_per_user, :net_redemption_limit_per_user,
            :source, :etl_batch_no
        )
        ON DUPLICATE KEY UPDATE
            pre_trading_day = VALUES(pre_trading_day), creation_redemption_unit = VALUES(creation_redemption_unit),
            max_cash_ratio = VALUES(max_cash_ratio), publish = VALUES(publish), creation = VALUES(creation),
            redemption = VALUES(redemption), creation_redemption_switch = VALUES(creation_redemption_switch),
            record_num = VALUES(record_num), total_record_num = VALUES(total_record_num),
            estimate_cash_component = VALUES(estimate_cash_component), cash_component = VALUES(cash_component),
            nav_per_cu = VALUES(nav_per_cu), nav = VALUES(nav), symbol = VALUES(symbol),
            fund_management_company = VALUES(fund_management_company),
            underlying_security_id = VALUES(underlying_security_id),
            underlying_security_id_source = VALUES(underlying_security_id_source),
            dividend_per_cu = VALUES(dividend_per_cu), creation_limit = VALUES(creation_limit),
            redemption_limit = VALUES(redemption_limit), creation_limit_per_user = VALUES(creation_limit_per_user),
            redemption_limit_per_user = VALUES(redemption_limit_per_user), net_creation_limit = VALUES(net_creation_limit),
            net_redemption_limit = VALUES(net_redemption_limit),
            net_creation_limit_per_user = VALUES(net_creation_limit_per_user),
            net_redemption_limit_per_user = VALUES(net_redemption_limit_per_user),
            source = VALUES(source), etl_batch_no = VALUES(etl_batch_no), updated_at = CURRENT_TIMESTAMP
        """
        self.execute_many(sql, rows)

    def upsert_etf_pcf_constituent(self, df: pd.DataFrame, batch_no: str):
        if df.empty:
            print("[WARN] etf_pcf_constituent 没有可写入数据，跳过")
            return

        rows = []
        skipped = 0

        for r in df.to_dict("records"):
            if r.get("etf_code") is None or r.get("trading_day") is None or r.get("constituent_code") is None:
                skipped += 1
                continue

            cleaned = {
                "id": generate_bigint_id(),
                "etf_code": None if pd.isna(r.get("etf_code")) else r.get("etf_code"),
                "trading_day": safe_date(r.get("trading_day")),
                "constituent_code": None if pd.isna(r.get("constituent_code")) else str(r.get("constituent_code")),
                "underlying_symbol": None if pd.isna(r.get("underlying_symbol")) else r.get("underlying_symbol"),
                "component_share": safe_decimal(r.get("component_share")),
                "substitute_flag": None if pd.isna(r.get("substitute_flag")) else r.get("substitute_flag"),
                "premium_ratio": safe_decimal(r.get("premium_ratio")),
                "discount_ratio": safe_decimal(r.get("discount_ratio")),
                "creation_cash_substitute": safe_decimal(r.get("creation_cash_substitute")),
                "redemption_cash_substitute": safe_decimal(r.get("redemption_cash_substitute")),
                "substitution_cash_amount": safe_decimal(r.get("substitution_cash_amount")),
                "underlying_security_id": None if pd.isna(r.get("underlying_security_id")) else r.get("underlying_security_id"),
                "source": config.SOURCE,
                "etl_batch_no": batch_no,
            }
            rows.append(cleaned)

        print(f"[INFO] etf_pcf_constituent 待写入 rows={len(rows)}, skipped_invalid={skipped}")
        if not rows:
            return

        sql = """
        INSERT INTO etf_pcf_constituent (
            id, etf_code, trading_day, constituent_code, underlying_symbol, component_share,
            substitute_flag, premium_ratio, discount_ratio, creation_cash_substitute,
            redemption_cash_substitute, substitution_cash_amount, underlying_security_id,
            source, etl_batch_no
        ) VALUES (
            :id, :etf_code, :trading_day, :constituent_code, :underlying_symbol, :component_share,
            :substitute_flag, :premium_ratio, :discount_ratio, :creation_cash_substitute,
            :redemption_cash_substitute, :substitution_cash_amount, :underlying_security_id,
            :source, :etl_batch_no
        )
        ON DUPLICATE KEY UPDATE
            underlying_symbol = VALUES(underlying_symbol), component_share = VALUES(component_share),
            substitute_flag = VALUES(substitute_flag), premium_ratio = VALUES(premium_ratio),
            discount_ratio = VALUES(discount_ratio), creation_cash_substitute = VALUES(creation_cash_substitute),
            redemption_cash_substitute = VALUES(redemption_cash_substitute),
            substitution_cash_amount = VALUES(substitution_cash_amount),
            underlying_security_id = VALUES(underlying_security_id), source = VALUES(source),
            etl_batch_no = VALUES(etl_batch_no), updated_at = CURRENT_TIMESTAMP
        """
        self.execute_many(sql, rows)

    def upsert_etf_fund_share(self, df: pd.DataFrame, batch_no: str):
        if df.empty:
            print("[WARN] etf_fund_share 没有可写入数据，跳过")
            return

        rows = []
        skipped = 0

        for r in df.to_dict("records"):
            if r.get("change_date") is None:
                skipped += 1
                continue

            cleaned = {
                "id": generate_bigint_id(),
                "etf_code": None if pd.isna(r.get("etf_code")) else r.get("etf_code"),
                "change_date": safe_date(r.get("change_date")),
                "ann_date": safe_date(r.get("ann_date")),
                "fund_share": safe_decimal(r.get("fund_share")),
                "total_share": safe_decimal(r.get("total_share")),
                "float_share": safe_decimal(r.get("float_share")),
                "change_reason": None if pd.isna(r.get("change_reason")) else r.get("change_reason"),
                "is_consolidated_data": safe_int(r.get("is_consolidated_data")),
                "source": config.SOURCE,
                "etl_batch_no": batch_no,
            }
            rows.append(cleaned)

        print(f"[INFO] etf_fund_share 待写入 rows={len(rows)}, skipped_null_change_date={skipped}")
        if not rows:
            return

        sql = """
        INSERT INTO etf_fund_share (
            id, etf_code, change_date, ann_date, fund_share, total_share, float_share,
            change_reason, is_consolidated_data, source, etl_batch_no
        ) VALUES (
            :id, :etf_code, :change_date, :ann_date, :fund_share, :total_share, :float_share,
            :change_reason, :is_consolidated_data, :source, :etl_batch_no
        )
        ON DUPLICATE KEY UPDATE
            ann_date = VALUES(ann_date), fund_share = VALUES(fund_share), total_share = VALUES(total_share),
            float_share = VALUES(float_share), change_reason = VALUES(change_reason),
            is_consolidated_data = VALUES(is_consolidated_data), source = VALUES(source),
            etl_batch_no = VALUES(etl_batch_no), updated_at = CURRENT_TIMESTAMP
        """
        self.execute_many(sql, rows)

    def upsert_etf_fund_iopv(self, df: pd.DataFrame, batch_no: str):
        if df.empty:
            print("[WARN] etf_fund_iopv 没有可写入数据，跳过")
            return

        rows = []
        skipped = 0

        for r in df.to_dict("records"):
            if r.get("price_date") is None:
                skipped += 1
                continue

            cleaned = {
                "id": generate_bigint_id(),
                "etf_code": None if pd.isna(r.get("etf_code")) else r.get("etf_code"),
                "price_date": safe_date(r.get("price_date")),
                "iopv_nav": safe_decimal(r.get("iopv_nav")),
                "source": config.SOURCE,
                "etl_batch_no": batch_no,
            }
            rows.append(cleaned)

        print(f"[INFO] etf_fund_iopv 待写入 rows={len(rows)}, skipped_null_price_date={skipped}")
        if not rows:
            return

        sql = """
        INSERT INTO etf_fund_iopv (
            id, etf_code, price_date, iopv_nav, source, etl_batch_no
        ) VALUES (
            :id, :etf_code, :price_date, :iopv_nav, :source, :etl_batch_no
        )
        ON DUPLICATE KEY UPDATE
            iopv_nav = VALUES(iopv_nav), source = VALUES(source),
            etl_batch_no = VALUES(etl_batch_no), updated_at = CURRENT_TIMESTAMP
        """
        self.execute_many(sql, rows)

    def upsert_etf_ta_indicator(self, df: pd.DataFrame, batch_no: str = None, batch_manager=None, upsert_batch_size: int = 2000):
        """技术指标写入：分批 upsert，每批提交后同步更新 etl_batch_status.kline_count

        batch_no + batch_manager 同时提供时，每个子批提交后立即把批次进度
        累加写入 etl_batch_status.kline_count，前端可实时看到已插入条数。
        """
        if df.empty:
            print("[WARN] etf_ta_indicator 没有可写入数据，跳过")
            return 0

        rows = []
        for _, r in df.iterrows():
            rows.append({
                "etf_code": r["etf_code"],
                "period": r["period"],
                "trade_time": safe_datetime(r.get("trade_time")),
                "open_price": safe_decimal(r.get("open_price")),
                "high_price": safe_decimal(r.get("high_price")),
                "low_price": safe_decimal(r.get("low_price")),
                "close_price": safe_decimal(r.get("close_price")),
                "volume": safe_int(r.get("volume")),
                "amount": safe_decimal(r.get("amount")),
                "dif": safe_decimal(r.get("dif")),
                "dea": safe_decimal(r.get("dea")),
                "macd": safe_decimal(r.get("macd")),
                "is_macd_golden_cross": safe_int(r.get("is_macd_golden_cross")) or 0,
                "is_macd_dead_cross": safe_int(r.get("is_macd_dead_cross")) or 0,
                "is_macd_golden_state": safe_int(r.get("is_macd_golden_state")) or 0,
                "is_macd_positive": safe_int(r.get("is_macd_positive")) or 0,
                "is_macd_red": safe_int(r.get("is_macd_red")) or 0,
                "macd_hist_direction": safe_int(r.get("macd_hist_direction")) or 0,
                "sar_value": safe_decimal(r.get("sar_value")),
                "is_sar_bullish": safe_int(r.get("is_sar_bullish")) or 0,
                "sar_trend": safe_int(r.get("sar_trend")) or 0,
                "ma5": safe_decimal(r.get("ma5")),
                "ma10": safe_decimal(r.get("ma10")),
                "ma20": safe_decimal(r.get("ma20")),
                "ma30": safe_decimal(r.get("ma30")),
                "ma60": safe_decimal(r.get("ma60")),
                "is_ma5_above_ma10": safe_int(r.get("is_ma5_above_ma10")) or 0,
                "is_ma10_above_ma20": safe_int(r.get("is_ma10_above_ma20")) or 0,
                "is_close_above_ma20": safe_int(r.get("is_close_above_ma20")) or 0,
                "is_close_above_ma60": safe_int(r.get("is_close_above_ma60")) or 0,
                "rsi6": safe_decimal(r.get("rsi6")),
                "rsi12": safe_decimal(r.get("rsi12")),
                "rsi24": safe_decimal(r.get("rsi24")),
                "k_value": safe_decimal(r.get("k_value")),
                "d_value": safe_decimal(r.get("d_value")),
                "j_value": safe_decimal(r.get("j_value")),
                "boll_mid": safe_decimal(r.get("boll_mid")),
                "boll_upper": safe_decimal(r.get("boll_upper")),
                "boll_lower": safe_decimal(r.get("boll_lower")),
                "atr14": safe_decimal(r.get("atr14")),
                "adx14": safe_decimal(r.get("adx14")),
                "signal_trend_long": safe_int(r.get("signal_trend_long")) or 0,
                "signal_momentum_long": safe_int(r.get("signal_momentum_long")) or 0,
                "signal_warning": safe_int(r.get("signal_warning")) or 0,
                "source": r.get("source") if pd.notna(r.get("source")) and r.get("source") not in (None, "") else config.SOURCE,
                "etl_batch_no": r.get("etl_batch_no"),
                "created_at": safe_datetime(r.get("created_at")),
                "updated_at": safe_datetime(r.get("updated_at")),
            })

        insert_sql = """
        INSERT INTO etf_ta_indicator ( 
            etf_code, period, trade_time, open_price, high_price, low_price, close_price, volume, amount, 
            dif, dea, macd, is_macd_golden_cross, is_macd_dead_cross, is_macd_golden_state, is_macd_positive, is_macd_red, macd_hist_direction, 
            sar_value, is_sar_bullish, sar_trend, 
            ma5, ma10, ma20, ma30, ma60, is_ma5_above_ma10, is_ma10_above_ma20, is_close_above_ma20, is_close_above_ma60, 
            rsi6, rsi12, rsi24, k_value, d_value, j_value, 
            boll_mid, boll_upper, boll_lower, 
            atr14, adx14, 
            signal_trend_long, signal_momentum_long, signal_warning, 
            source, etl_batch_no, created_at, updated_at 
        ) VALUES ( 
            :etf_code, :period, :trade_time, :open_price, :high_price, :low_price, :close_price, :volume, :amount, 
            :dif, :dea, :macd, :is_macd_golden_cross, :is_macd_dead_cross, :is_macd_golden_state, :is_macd_positive, :is_macd_red, :macd_hist_direction, 
            :sar_value, :is_sar_bullish, :sar_trend, 
            :ma5, :ma10, :ma20, :ma30, :ma60, :is_ma5_above_ma10, :is_ma10_above_ma20, :is_close_above_ma20, :is_close_above_ma60, 
            :rsi6, :rsi12, :rsi24, :k_value, :d_value, :j_value, 
            :boll_mid, :boll_upper, :boll_lower, 
            :atr14, :adx14, 
            :signal_trend_long, :signal_momentum_long, :signal_warning, 
            :source, :etl_batch_no, :created_at, :updated_at 
        )
        ON DUPLICATE KEY UPDATE
            open_price = VALUES(open_price), high_price = VALUES(high_price),
            low_price = VALUES(low_price), close_price = VALUES(close_price),
            volume = VALUES(volume), amount = VALUES(amount),
            dif = VALUES(dif), dea = VALUES(dea), macd = VALUES(macd),
            is_macd_golden_cross = VALUES(is_macd_golden_cross),
            is_macd_dead_cross = VALUES(is_macd_dead_cross),
            is_macd_golden_state = VALUES(is_macd_golden_state),
            is_macd_positive = VALUES(is_macd_positive), is_macd_red = VALUES(is_macd_red),
            macd_hist_direction = VALUES(macd_hist_direction),
            sar_value = VALUES(sar_value), is_sar_bullish = VALUES(is_sar_bullish),
            sar_trend = VALUES(sar_trend), ma5 = VALUES(ma5), ma10 = VALUES(ma10),
            ma20 = VALUES(ma20), ma30 = VALUES(ma30), ma60 = VALUES(ma60),
            is_ma5_above_ma10 = VALUES(is_ma5_above_ma10),
            is_ma10_above_ma20 = VALUES(is_ma10_above_ma20),
            is_close_above_ma20 = VALUES(is_close_above_ma20),
            is_close_above_ma60 = VALUES(is_close_above_ma60),
            rsi6 = VALUES(rsi6), rsi12 = VALUES(rsi12), rsi24 = VALUES(rsi24),
            k_value = VALUES(k_value), d_value = VALUES(d_value), j_value = VALUES(j_value),
            boll_mid = VALUES(boll_mid), boll_upper = VALUES(boll_upper),
            boll_lower = VALUES(boll_lower), atr14 = VALUES(atr14), adx14 = VALUES(adx14),
            signal_trend_long = VALUES(signal_trend_long),
            signal_momentum_long = VALUES(signal_momentum_long),
            signal_warning = VALUES(signal_warning), source = VALUES(source),
            etl_batch_no = VALUES(etl_batch_no), updated_at = CURRENT_TIMESTAMP
        """

        ok = 0
        with self.engine.begin() as conn:
            conn.execute(text(insert_sql), rows)
            ok = len(rows)

        print(f"[INFO] etf_ta_indicator 写入完成: ok={ok}, total={len(rows)}")


# =========================
# 技术指标计算
# =========================

class TechnicalIndicatorCalculator:
    """技术指标计算器"""

    @staticmethod
    def normalize_kline_df(df: pd.DataFrame) -> pd.DataFrame:
        if df.empty:
            return df

        df = df.copy()
        df.columns = [str(c).strip() for c in df.columns]

        rename_map = {}
        for col in df.columns:
            low = col.lower()
            if low in ("etf_code", "period", "trade_time", "open_price", "high_price",
                       "low_price", "close_price", "volume", "amount", "source", "etl_batch_no",
                       "created_at", "updated_at"):
                rename_map[col] = low

        df = df.rename(columns=rename_map)

        for col in ["trade_time", "created_at", "updated_at"]:
            if col in df.columns:
                df[col] = pd.to_datetime(df[col], errors="coerce")

        for col in ["open_price", "high_price", "low_price", "close_price", "volume", "amount"]:
            if col in df.columns:
                df[col] = pd.to_numeric(df[col], errors="coerce")

        sort_cols = [c for c in ["etf_code", "period", "trade_time"] if c in df.columns]
        if sort_cols:
            df = df.sort_values(sort_cols).reset_index(drop=True)

        return df

    @staticmethod
    def calc_macd(df: pd.DataFrame) -> pd.DataFrame:
        close = df["close_price"]
        ema12 = close.ewm(span=12, adjust=False).mean()
        ema26 = close.ewm(span=26, adjust=False).mean()
        dif = ema12 - ema26
        dea = dif.ewm(span=9, adjust=False).mean()
        macd = 2 * (dif - dea)

        df["dif"] = dif
        df["dea"] = dea
        df["macd"] = macd

        prev_dif = df["dif"].shift(1)
        prev_dea = df["dea"].shift(1)
        prev_macd = df["macd"].shift(1)

        df["is_macd_golden_cross"] = ((df["dif"] > df["dea"]) & (prev_dif <= prev_dea)).astype(int)
        df["is_macd_dead_cross"] = ((df["dif"] < df["dea"]) & (prev_dif >= prev_dea)).astype(int)
        df["is_macd_golden_state"] = (df["dif"] > df["dea"]).astype(int)
        df["is_macd_positive"] = (df["macd"] > 0).astype(int)
        df["is_macd_red"] = (df["macd"] > 0).astype(int)

        df["macd_hist_direction"] = np.where(
            prev_macd.isna(), 0,
            np.where(df["macd"] > prev_macd, 1, np.where(df["macd"] < prev_macd, -1, 0))
        )
        return df

    @staticmethod
    def calc_ma(df: pd.DataFrame) -> pd.DataFrame:
        close = df["close_price"]
        df["ma5"] = close.rolling(window=5, min_periods=5).mean()
        df["ma10"] = close.rolling(window=10, min_periods=10).mean()
        df["ma20"] = close.rolling(window=20, min_periods=20).mean()
        df["ma30"] = close.rolling(window=30, min_periods=30).mean()
        df["ma60"] = close.rolling(window=60, min_periods=60).mean()

        df["is_ma5_above_ma10"] = (df["ma5"] > df["ma10"]).astype(int)
        df["is_ma10_above_ma20"] = (df["ma10"] > df["ma20"]).astype(int)
        df["is_close_above_ma20"] = (df["close_price"] > df["ma20"]).astype(int)
        df["is_close_above_ma60"] = (df["close_price"] > df["ma60"]).astype(int)
        return df

    @staticmethod
    def calc_rsi(series: pd.Series, period: int) -> pd.Series:
        delta = series.diff()
        gain = delta.clip(lower=0)
        loss = -delta.clip(upper=0)

        avg_gain = pd.Series(np.nan, index=series.index, dtype="float64")
        avg_loss = pd.Series(np.nan, index=series.index, dtype="float64")
        if len(series) <= period:
            return avg_gain

        first = period
        avg_gain.iloc[first] = gain.iloc[1:first + 1].mean()
        avg_loss.iloc[first] = loss.iloc[1:first + 1].mean()
        for i in range(first + 1, len(series)):
            avg_gain.iloc[i] = ((period - 1) * avg_gain.iloc[i - 1] + gain.iloc[i]) / period
            avg_loss.iloc[i] = ((period - 1) * avg_loss.iloc[i - 1] + loss.iloc[i]) / period

        rsi = pd.Series(np.nan, index=series.index, dtype="float64")
        no_loss = avg_loss == 0
        no_change = no_loss & (avg_gain == 0)
        rsi[no_loss & ~no_change] = 100.0
        rsi[no_change] = 50.0
        valid = ~no_loss
        rs = avg_gain[valid] / avg_loss[valid]
        rsi.loc[valid] = 100 - (100 / (1 + rs))
        return rsi

    @staticmethod
    def calc_rsi_group(df: pd.DataFrame) -> pd.DataFrame:
        close = df["close_price"]
        df["rsi6"] = TechnicalIndicatorCalculator.calc_rsi(close, 6)
        df["rsi12"] = TechnicalIndicatorCalculator.calc_rsi(close, 12)
        df["rsi24"] = TechnicalIndicatorCalculator.calc_rsi(close, 24)
        return df

    @staticmethod
    def calc_kdj(df: pd.DataFrame, n=9) -> pd.DataFrame:
        low_n = df["low_price"].rolling(window=n, min_periods=n).min()
        high_n = df["high_price"].rolling(window=n, min_periods=n).max()

        rsv = np.where((high_n - low_n) == 0, 50, (df["close_price"] - low_n) / (high_n - low_n) * 100)
        rsv = pd.Series(rsv, index=df.index, dtype="float64")

        first_valid = n - 1

        # 向量化递推：K = (2/3)K_prev + (1/3)RSV，D = (2/3)D_prev + (1/3)K
        # 用 ewm(alpha=1/3, adjust=False) 等价实现 prev*(2/3)+cur*(1/3)，避免逐行循环
        k = rsv.ewm(alpha=1 / 3, adjust=False).mean()
        d = k.ewm(alpha=1 / 3, adjust=False).mean()
        # 不足完整窗口的预热位置置空；首个有效位置从初值 50 起步
        k.iloc[:first_valid] = np.nan
        d.iloc[:first_valid] = np.nan
        j = 3 * k - 2 * d

        df["k_value"] = k
        df["d_value"] = d
        df["j_value"] = j
        return df

    @staticmethod
    def calc_boll(df: pd.DataFrame, n=20, k=2) -> pd.DataFrame:
        mid = df["close_price"].rolling(window=n, min_periods=n).mean()
        std = df["close_price"].rolling(window=n, min_periods=n).std(ddof=0)
        df["boll_mid"] = mid
        df["boll_upper"] = mid + k * std
        df["boll_lower"] = mid - k * std
        return df

    @staticmethod
    def calc_atr_adx(df: pd.DataFrame, n=14) -> pd.DataFrame:
        high = df["high_price"]
        low = df["low_price"]
        close = df["close_price"]

        prev_close = close.shift(1)
        prev_high = high.shift(1)
        prev_low = low.shift(1)

        tr1 = high - low
        tr2 = (high - prev_close).abs()
        tr3 = (low - prev_close).abs()
        tr = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)

        up_move = high - prev_high
        down_move = prev_low - low

        plus_dm = np.where((up_move > down_move) & (up_move > 0), up_move, 0.0)
        minus_dm = np.where((down_move > up_move) & (down_move > 0), down_move, 0.0)
        plus_dm = pd.Series(plus_dm, index=df.index)
        minus_dm = pd.Series(minus_dm, index=df.index)

        atr = tr.rolling(window=n, min_periods=n).mean()
        smoothed_plus_dm = plus_dm.rolling(window=n, min_periods=n).mean()
        smoothed_minus_dm = minus_dm.rolling(window=n, min_periods=n).mean()
        for i in range(n, len(df)):
            atr.iloc[i] = (atr.iloc[i - 1] * (n - 1) + tr.iloc[i]) / n
            smoothed_plus_dm.iloc[i] = (smoothed_plus_dm.iloc[i - 1] * (n - 1) + plus_dm.iloc[i]) / n
            smoothed_minus_dm.iloc[i] = (smoothed_minus_dm.iloc[i - 1] * (n - 1) + minus_dm.iloc[i]) / n

        plus_di = 100 * smoothed_plus_dm / atr.replace(0, np.nan)
        minus_di = 100 * smoothed_minus_dm / atr.replace(0, np.nan)

        dx = 100 * (plus_di - minus_di).abs() / (plus_di + minus_di).replace(0, np.nan)
        adx = pd.Series(np.nan, index=df.index, dtype="float64")
        first_adx = 2 * n - 2
        if len(df) > first_adx:
            adx.iloc[first_adx] = dx.iloc[n - 1:first_adx + 1].mean()
            for i in range(first_adx + 1, len(df)):
                adx.iloc[i] = (adx.iloc[i - 1] * (n - 1) + dx.iloc[i]) / n

        df["atr14"] = atr
        df["adx14"] = adx
        return df

    @staticmethod
    def calc_sar(df: pd.DataFrame, n: int = None, af_step: float = None, af_max: float = None) -> pd.DataFrame:
        af_step = af_step or config.SAR_AF_STEP
        af_max = af_max or config.SAR_AF_MAX

        high = df["high_price"].to_numpy(dtype=float)
        low = df["low_price"].to_numpy(dtype=float)
        close = df["close_price"].to_numpy(dtype=float)
        length = len(df)

        sar = np.full(length, np.nan, dtype=float)
        trend = np.full(length, 0, dtype=int)

        if length < 2:
            df["sar_value"] = sar
            df["sar_trend"] = trend
            df["is_sar_bullish"] = 0
            return df

        bull = close[1] >= close[0]
        sar[0] = low[0] if bull else high[0]
        ep = high[0] if bull else low[0]
        trend[0] = 1 if bull else -1
        af = af_step

        for i in range(1, length):
            prev_sar = sar[i - 1]

            if bull:
                cur_sar = prev_sar + af * (ep - prev_sar)
                cur_sar = min(cur_sar, low[i - 1], low[i - 2] if i > 1 else low[i - 1])

                if low[i] < cur_sar:
                    bull = False
                    sar[i] = ep
                    ep = low[i]
                    af = af_step
                    trend[i] = -1
                else:
                    sar[i] = cur_sar
                    if high[i] > ep:
                        ep = high[i]
                        af = min(af + af_step, af_max)
                    trend[i] = 1
            else:
                cur_sar = prev_sar + af * (ep - prev_sar)
                cur_sar = max(cur_sar, high[i - 1], high[i - 2] if i > 1 else high[i - 1])

                if high[i] > cur_sar:
                    bull = True
                    sar[i] = ep
                    ep = high[i]
                    af = af_step
                    trend[i] = 1
                else:
                    sar[i] = cur_sar
                    if low[i] < ep:
                        ep = low[i]
                        af = min(af + af_step, af_max)
                    trend[i] = -1

        df["sar_value"] = sar
        df["sar_trend"] = trend
        df["is_sar_bullish"] = (df["sar_trend"] == 1).astype(int)
        return df

    @staticmethod
    def calc_signals(df: pd.DataFrame) -> pd.DataFrame:
        df["signal_trend_long"] = (
            (df["is_macd_golden_state"] == 1) &
            (df["is_sar_bullish"] == 1) &
            (df["is_close_above_ma20"] == 1)
        ).astype(int)

        df["signal_momentum_long"] = (
            (df["is_macd_red"] == 1) &
            (df["rsi12"] > 50) &
            (df["k_value"] > df["d_value"])
        ).astype(int)

        df["signal_warning"] = (
            (df["is_macd_dead_cross"] == 1) |
            (df["is_close_above_ma20"] == 0) |
            (df["is_sar_bullish"] == 0)
        ).astype(int)
        return df

    @staticmethod
    def calculate_for_group(group: pd.DataFrame, etf_code: str, period: str) -> pd.DataFrame:
        group = group.copy()
        group = group.sort_values("trade_time").reset_index(drop=True)

        group = TechnicalIndicatorCalculator.calc_macd(group)
        group = TechnicalIndicatorCalculator.calc_ma(group)
        group = TechnicalIndicatorCalculator.calc_rsi_group(group)
        group = TechnicalIndicatorCalculator.calc_kdj(group)
        group = TechnicalIndicatorCalculator.calc_boll(group)
        group = TechnicalIndicatorCalculator.calc_atr_adx(group)
        group = TechnicalIndicatorCalculator.calc_sar(group)
        group = TechnicalIndicatorCalculator.calc_signals(group)

        group["etf_code"] = etf_code
        group["period"] = period

        front_cols = ["etf_code", "period", "trade_time"]
        other_cols = [c for c in group.columns if c not in front_cols]
        return group[front_cols + other_cols]

    @staticmethod
    def calculate(kline_df: pd.DataFrame) -> pd.DataFrame:
        result_list = []
        grouped = kline_df.groupby(["etf_code", "period"], sort=False)
        print(f"[INFO] 技术指标计算: total_groups={grouped.ngroups}")

        for (etf_code, period), group in grouped:
            result_list.append(
                TechnicalIndicatorCalculator.calculate_for_group(group, etf_code, period)
            )

        if not result_list:
            return pd.DataFrame()
        return pd.concat(result_list, ignore_index=True)


# =========================
# 技术指标数据加载
# =========================

def load_kline_for_indicators(engine, latest_batch_no: str) -> pd.DataFrame:
    """加载K线数据用于技术指标计算"""
    target_periods = config.TARGET_PERIODS
    period_sql = ",".join([f"'{x}'" for x in target_periods])

    if config.is_incremental_mode() and latest_batch_no:
        sql_groups = f"""
        SELECT DISTINCT etf_code, period
        FROM etf_market_kline
        WHERE period IN ({period_sql})
          AND etl_batch_no = :batch_no
        """
        groups_df = pd.read_sql(text(sql_groups), engine, params={"batch_no": latest_batch_no})
        if groups_df.empty:
            print(f"[WARN] 增量模式：批次 {latest_batch_no} 无数据")
            return pd.DataFrame()

        groups_df.columns = [str(c).strip().lower() for c in groups_df.columns]
        pairs = groups_df[["etf_code", "period"]].dropna().drop_duplicates().values.tolist()
        print(f"[INFO] 增量模式：本批次涉及 groups={len(pairs)}，回看 LOOKBACK_BARS={config.LOOKBACK_BARS}")

        sql_one = f"""
        SELECT
            etf_code, period, trade_time, open_price, high_price, low_price, close_price,
            volume, amount, source, etl_batch_no, created_at, updated_at
        FROM (
            SELECT
                etf_code, period, trade_time, open_price, high_price, low_price, close_price,
                volume, amount, source, etl_batch_no, created_at, updated_at,
                ROW_NUMBER() OVER (PARTITION BY etf_code, period ORDER BY trade_time DESC) rn
            FROM etf_market_kline
            WHERE etf_code = :etf_code AND period = :period
        ) t
        WHERE rn <= :lookback
        ORDER BY etf_code, period, trade_time
        """

        result = []
        for etf_code, period in pairs:
            df = pd.read_sql(text(sql_one), engine, params={
                "etf_code": etf_code, "period": period, "lookback": config.LOOKBACK_BARS
            })
            if not df.empty:
                result.append(df)

        if not result:
            return pd.DataFrame()
        df_all = pd.concat(result, ignore_index=True)
    else:
        sql = f"""
        SELECT
            etf_code, period, trade_time, open_price, high_price, low_price, close_price,
            volume, amount, source, etl_batch_no, created_at, updated_at
        FROM etf_market_kline
        WHERE period IN ({period_sql})
        ORDER BY etf_code, period, trade_time
        """
        print("[INFO] 读取K线数据（full：全量）...")
        df_all = pd.read_sql(text(sql), engine)

    df_all = TechnicalIndicatorCalculator.normalize_kline_df(df_all)
    df_all["source"] = df_all["source"].fillna(config.SOURCE)
    return df_all


def load_kline_for_indicators_by_dates(engine, start_date: int, end_date: int) -> pd.DataFrame:
    """按日期范围从 etf_market_kline 取数据计算指标（--ta-only 专用）

    周/月/季线的 trade_time 是周期起始日，可能早于窗口起点。
    因此取数时放宽到 start_date 前 90 天，确保窗口内每天的周/月/季线都能被取到。
    """
    period_sql = ",".join([f"'{x}'" for x in config.TARGET_PERIODS])
    # 周/月/季线周期起始日可能早于窗口，放宽取数范围
    fetch_start = pd.to_datetime(str(start_date), format="%Y%m%d") - pd.Timedelta(days=90)
    fetch_start_int = int(fetch_start.strftime("%Y%m%d"))

    sql = f"""
    SELECT
        etf_code, period, trade_time, open_price, high_price, low_price, close_price,
        volume, amount, source, etl_batch_no, created_at, updated_at
    FROM etf_market_kline
    WHERE period IN ({period_sql})
      AND DATE(trade_time) BETWEEN :start_date AND :end_date
    ORDER BY etf_code, period, trade_time
    """
    print(f"[INFO] --ta-only 按日期取K线: {start_date} - {end_date}（周/月/季线放宽到 {fetch_start_int} 起）")
    df = pd.read_sql(text(sql), engine, params={"start_date": str(fetch_start_int), "end_date": str(end_date)})
    if df.empty:
        return df
    df = TechnicalIndicatorCalculator.normalize_kline_df(df)
    df["source"] = df["source"].fillna(config.SOURCE)
    return df


def filter_rows_by_date_range(df: pd.DataFrame, start_date: int, end_date: int) -> pd.DataFrame:
    """只保留日期范围内的指标行（ta-only 模式：每天4条，10天=40条）"""
    if df.empty:
        return df
    mask = (df["trade_time"].dt.strftime("%Y%m%d").astype(int) >= start_date) & \
           (df["trade_time"].dt.strftime("%Y%m%d").astype(int) <= end_date)
    return df[mask].copy().reset_index(drop=True)


def load_kline_full_for_daily_ta(engine) -> pd.DataFrame:
    """--ta-daily 专用：拉 day/week/month/season 全部 K 线（用于按天对齐算指标）。

    不限制日期范围：为了让窗口内最早一天的指标也能量化到正确的值（MACD/RSI/KDJ 等
    需要回看 100+ 个根），需要每个 (etf, period) 都有尽可能全的历史。
    """
    period_sql = ",".join([f"'{x}'" for x in config.TARGET_PERIODS])
    sql = f"""
    SELECT
        etf_code, period, trade_time, open_price, high_price, low_price, close_price,
        volume, amount, source, etl_batch_no, created_at, updated_at
    FROM etf_market_kline
    WHERE period IN ({period_sql})
    ORDER BY etf_code, period, trade_time
    """
    print(f"[INFO] --ta-daily 加载全部 K 线（period IN ({period_sql})）用于回看计算")
    df = pd.read_sql(text(sql), engine)
    if df.empty:
        return df
    df = TechnicalIndicatorCalculator.normalize_kline_df(df)
    df["source"] = df["source"].fillna(config.SOURCE)
    return df


def align_ta_to_daily(ta_df: pd.DataFrame, trade_dates: List[int], etf_codes: List[str] = None) -> pd.DataFrame:
    """把按周期记的指标结果按"每个交易日 4 周期"对齐后写回 etf_ta_indicator。

    关键：复用原表的 (etf_code, period, trade_time) 唯一键，因此"按天对齐"时把 4 个 period 的
    trade_time 全部写成"当天的 00:00:00"——这样每天每个 ETF 4 周期各占 1 条 (etf, period, trade_time)
    不冲突；10 天窗口就是 40 条/ETF。

    查询时按 trade_time 的日期部分筛：WHERE DATE(trade_time) = '2026-06-01'
    """
    if ta_df.empty or not trade_dates:
        return pd.DataFrame()

    df = ta_df.copy()
    df["trade_date_int"] = df["trade_time"].dt.strftime("%Y%m%d").astype(int)

    # 仅保留窗口内会出现"有效 K 线"的 period
    df = df[df["period"].isin(config.TARGET_PERIODS)].copy()
    if etf_codes:
        df = df[df["etf_code"].isin(etf_codes)].copy()

    out_rows = []
    for td in trade_dates:
        # 当日 day 周期的所有 ETF 指标
        today_day = df[(df["period"] == "day") & (df["trade_date_int"] == td)]

        # 截至 D 为止，week/month/season 各 ETF 最近的指标行
        for period in ("week", "month", "season"):
            sub = df[(df["period"] == period) & (df["trade_date_int"] <= td)]
            if sub.empty:
                continue
            latest = sub.sort_values("trade_time").groupby("etf_code", sort=False).tail(1)
            latest = latest.copy()
            # 把 trade_time 改为当天的 00:00:00，与 day 行保持同一天（unique key 不会冲突）
            latest["trade_time"] = pd.to_datetime(str(td), format="%Y%m%d")
            out_rows.append(latest)

        if not today_day.empty:
            day = today_day.copy()
            day["trade_time"] = pd.to_datetime(str(td), format="%Y%m%d")
            out_rows.append(day)

    if not out_rows:
        return pd.DataFrame()

    aligned = pd.concat(out_rows, ignore_index=True)
    return aligned


def filter_latest_batch_rows(df: pd.DataFrame, latest_batch_no: str) -> pd.DataFrame:
    """增量模式下只保留本批次那根K线"""
    if df.empty:
        return df
    return df[df["etl_batch_no"] == latest_batch_no].copy().reset_index(drop=True)


def keep_only_latest_row_per_group(df: pd.DataFrame) -> pd.DataFrame:
    """每组只保留最新一条记录"""
    if df.empty:
        return df
    return (
        df.sort_values(["etf_code", "period", "trade_time"])
          .groupby(["etf_code", "period"], sort=False, as_index=False)
          .tail(1)
          .reset_index(drop=True)
    )


# =========================
# 主流程
# =========================

def main():
    print("=" * 60)
    print("ETF 数据落库 + 技术指标计算一体化脚本")
    print(f"运行时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    # 0. 命令行参数：
    #    --import-only  银河证券数据导入（K 线/PCF/份额/IOPV 等），不算指标
    #    --ta-only      L2 指标计算，基于 etf_market_kline 已有数据重算 day/week/month/season
    #    两者互斥；日期窗口/全量/增量 配置复用 sys_param 中的 RUN_MODE/RECENT_DAYS/INCLUDE_TODAY/MAX_PROCESS_DAYS
    ta_only_mode = "--ta-only" in sys.argv
    ta_daily_mode = "--ta-daily" in sys.argv
    import_only_mode = "--import-only" in sys.argv
    if sum([ta_only_mode, ta_daily_mode, import_only_mode]) > 1:
        print("[ERROR] --ta-only / --ta-daily / --import-only 三者互斥，只能选一个")
        return

    # 1. 获取 MySQL 连接（先使用默认连接读取配置）
    mysql_engine = get_mysql_engine_from_db()
    
    # 2. 加载配置
    config.load_from_db(mysql_engine)

    # 2.1 模式开关覆盖必须放在 load_from_db 之后：
    #     否则 sys_param 中的 ENABLE_* 会把下面设置的 False 重新覆盖回 True，
    #     导致 --ta-only 模式误执行 K 线抽取（market_data 未初始化 -> query_kline 报 NoneType）
    if ta_only_mode:
        print("[INFO] --ta-only 模式：仅计算 L2 技术指标，跳过 K 线/PCF/份额/IOPV 抽取")
        print("[INFO] 日期窗口/全量/增量 配置复用 sys_param：RUN_MODE/RECENT_DAYS/INCLUDE_TODAY/MAX_PROCESS_DAYS")
        # 关闭除技术指标外的所有抽取步骤
        config._params["ENABLE_ETF_SECURITY_MASTER"] = False
        config._params["ENABLE_TRADE_CALENDAR"] = False
        config._params["ENABLE_ETF_MARKET_SNAPSHOT"] = False
        config._params["ENABLE_ETF_MARKET_KLINE"] = False
        config._params["ENABLE_ETF_PCF_INFO"] = False
        config._params["ENABLE_ETF_PCF_CONSTITUENT"] = False
        config._params["ENABLE_ETF_FUND_SHARE"] = False
        config._params["ENABLE_ETF_FUND_IOPV"] = False
        config._params["ENABLE_TA_INDICATOR"] = True
        # 技术指标必须覆盖四个周期
        config._params["TARGET_PERIODS"] = ["day", "week", "month", "season"]
        config._params["KLINE_PERIODS"] = [(p, PERIOD_MAPPING[p]) for p in ["day", "week", "month", "season"] if p in PERIOD_MAPPING]
    elif ta_daily_mode:
        print("[INFO] --ta-daily 模式：按天对齐 L2 技术指标，每个交易日 4 周期各一条")
        print("[INFO] 日期窗口/全量/增量 配置复用 sys_param：RUN_MODE/RECENT_DAYS/INCLUDE_TODAY/MAX_PROCESS_DAYS")
        # 关闭除技术指标外的所有抽取步骤
        config._params["ENABLE_ETF_SECURITY_MASTER"] = False
        config._params["ENABLE_TRADE_CALENDAR"] = False
        config._params["ENABLE_ETF_MARKET_SNAPSHOT"] = False
        config._params["ENABLE_ETF_MARKET_KLINE"] = False
        config._params["ENABLE_ETF_PCF_INFO"] = False
        config._params["ENABLE_ETF_PCF_CONSTITUENT"] = False
        config._params["ENABLE_ETF_FUND_SHARE"] = False
        config._params["ENABLE_ETF_FUND_IOPV"] = False
        config._params["ENABLE_TA_INDICATOR"] = True
        # 技术指标必须覆盖四个周期
        config._params["TARGET_PERIODS"] = ["day", "week", "month", "season"]
        config._params["KLINE_PERIODS"] = [(p, PERIOD_MAPPING[p]) for p in ["day", "week", "month", "season"] if p in PERIOD_MAPPING]
    elif import_only_mode:
        print("[INFO] --import-only 模式：仅导入 K 线/PCF/份额/IOPV，不计算技术指标")
        config._params["ENABLE_TA_INDICATOR"] = False
    
    # 3. 重新创建 MySQL 引擎（使用配置中的连接信息）
    mysql_engine = get_mysql_engine_from_config()
    config._mysql_engine = mysql_engine

    # 4. 登录 AmazingData
    login_amazingdata()

    # 5. 初始化组件
    extractor = AmazingDataETFExtractor()
    writer = MysqlWriter(mysql_engine)
    batch_manager = BatchStatusManager(mysql_engine)
    date_calculator = TradeDateCalculator(mysql_engine, extractor)

    # 6. 获取需要处理的日期范围
    if ta_only_mode or ta_daily_mode:
        # --ta-only / --ta-daily：日期窗口复用 sys_param 配置（RUN_MODE/RECENT_DAYS/INCLUDE_TODAY/MAX_PROCESS_DAYS），
        # 但交易日从 etf_market_kline 自身读取（period='day'），避免联网拉 AmazingData 日历且与"导入"按钮的 K 线对账
        run_mode = str(config.RUN_MODE).strip().lower()
        include_today = bool(config.INCLUDE_TODAY)
        today_int = int(datetime.now().strftime("%Y%m%d"))
        max_days = int(config.MAX_PROCESS_DAYS) if int(config.MAX_PROCESS_DAYS) > 0 else 30
        recent_days = int(config.RECENT_DAYS) if int(config.RECENT_DAYS) > 0 else 1

        sql_dates = """
        SELECT DISTINCT DATE_FORMAT(trade_time, '%Y%m%d') AS d
        FROM etf_market_kline
        WHERE period = 'day'
        ORDER BY d
        """
        df_db_dates = pd.read_sql(text(sql_dates), mysql_engine)
        all_db_dates = sorted(int(x) for x in df_db_dates['d'].tolist() if x)
        if not all_db_dates:
            print("[INFO] etf_market_kline 没有 day 周期数据，无法计算 L2 指标，退出")
            logout_amazingdata()
            return

        candidate_dates = all_db_dates if include_today else [d for d in all_db_dates if d != today_int]
        if not candidate_dates:
            print("[INFO] 候选交易日为空，退出")
            logout_amazingdata()
            return

        if run_mode == "full":
            trade_dates = candidate_dates[-max_days:] if len(candidate_dates) > max_days else candidate_dates
        elif run_mode == "recent":
            trade_dates = candidate_dates[-recent_days:] if recent_days > 0 else candidate_dates
        else:  # incremental：ta-only/ta-daily 没有外部检查点概念，按 recent 处理
            trade_dates = candidate_dates[-recent_days:]

        start_date = trade_dates[0]
        end_date = trade_dates[-1]
        mode_tag = "--ta-daily" if ta_daily_mode else "--ta-only"
        print(f"[INFO] {mode_tag} 日期窗口（与导入按钮共用 sys_param）: {start_date} - {end_date} ({len(trade_dates)} 天, RUN_MODE={run_mode})")
    else:
        trade_dates, start_date, end_date = date_calculator.get_trade_dates_to_run()
    
    if not trade_dates:
        print("[INFO] 没有需要处理的新交易日，退出")
        logout_amazingdata()
        return

    # 7. 开始批次记录
    # 使用最新交易日期作为批次号前缀，确保 etl_batch_no 与实际交易日期一致
    batch_no = f"{end_date}_{datetime.now().strftime('%H%M%S')}"
    if not batch_manager.start_batch(batch_no, start_date, end_date):
        print("[ERROR] 无法开始新批次，可能存在未完成的批次")
        logout_amazingdata()
        return

    try:
        # 8. 获取ETF代码列表
        print("[INFO] 获取ETF代码列表...")
        etf_codes = extractor.get_etf_code_list()
        print(f"[INFO] ETF数量: {len(etf_codes)}")

        # 9. 打印执行开关
        print("[INFO] 当前执行开关配置：")
        print(f"       ENABLE_ETF_SECURITY_MASTER  = {config.ENABLE_ETF_SECURITY_MASTER}")
        print(f"       ENABLE_TRADE_CALENDAR       = {config.ENABLE_TRADE_CALENDAR}")
        print(f"       ENABLE_ETF_MARKET_SNAPSHOT  = {config.ENABLE_ETF_MARKET_SNAPSHOT}")
        print(f"       ENABLE_ETF_MARKET_KLINE     = {config.ENABLE_ETF_MARKET_KLINE}")
        print(f"       ENABLE_ETF_PCF_INFO         = {config.ENABLE_ETF_PCF_INFO}")
        print(f"       ENABLE_ETF_PCF_CONSTITUENT  = {config.ENABLE_ETF_PCF_CONSTITUENT}")
        print(f"       ENABLE_ETF_FUND_SHARE       = {config.ENABLE_ETF_FUND_SHARE}")
        print(f"       ENABLE_ETF_FUND_IOPV        = {config.ENABLE_ETF_FUND_IOPV}")
        print(f"       ENABLE_TA_INDICATOR         = {config.ENABLE_TA_INDICATOR}")

        kline_count = 0

        # 10. ETF基础信息
        if config.ENABLE_ETF_SECURITY_MASTER:
            print("[INFO] 抽取 etf_security_master ...")
            df_code_info = extractor.get_code_info()
            writer.upsert_etf_security_master(df_code_info, batch_no)

        # 11. 交易日历
        if config.ENABLE_TRADE_CALENDAR:
            print("[INFO] 抽取 trade_calendar ...")
            df_calendar_sh = extractor.get_trade_calendar("SH")
            df_calendar_sz = extractor.get_trade_calendar("SZ")
            df_calendar = pd.concat([df_calendar_sh, df_calendar_sz], ignore_index=True)
            writer.upsert_trade_calendar(df_calendar, batch_no)

        # 12. 行情快照（跳过）
        if config.ENABLE_ETF_MARKET_SNAPSHOT:
            print("[INFO] 抽取 etf_market_snapshot ...")
            print("[WARN] 当前环境下 query_snapshot 与 pandas 版本不兼容，临时跳过")

        # 13. K线数据（核心数据）
        if config.ENABLE_ETF_MARKET_KLINE:
            print("[INFO] 抽取 etf_market_kline ...")
            df_kline = extractor.get_kline_for_dates(etf_codes, trade_dates)
            kline_count = len(df_kline)
            writer.upsert_etf_market_kline(df_kline, batch_no)

        # 14. PCF数据
        if config.ENABLE_ETF_PCF_INFO or config.ENABLE_ETF_PCF_CONSTITUENT:
            print("[INFO] 抽取 etf_pcf_info / etf_pcf_constituent ...")
            df_pcf_info, df_pcf_constituent = extractor.get_etf_pcf_latest(etf_codes)

            if config.ENABLE_ETF_PCF_INFO:
                writer.upsert_etf_pcf_info(df_pcf_info, batch_no)

            if config.ENABLE_ETF_PCF_CONSTITUENT:
                writer.upsert_etf_pcf_constituent(df_pcf_constituent, batch_no)

        # 15. 基金份额
        if config.ENABLE_ETF_FUND_SHARE:
            print("[INFO] 抽取 etf_fund_share ...")
            df_fund_share = extractor.get_fund_share(etf_codes, trade_dates)
            writer.upsert_etf_fund_share(df_fund_share, batch_no)

        # 16. IOPV
        if config.ENABLE_ETF_FUND_IOPV:
            print("[INFO] 抽取 etf_fund_iopv ...")
            df_iopv = extractor.get_fund_iopv(etf_codes, trade_dates)
            writer.upsert_etf_fund_iopv(df_iopv, batch_no)

        # 17. 更新检查点（K线数据成功写入后）
        if trade_dates and kline_count > 0:
            batch_manager.update_checkpoint("ETF_KLINE_DAY", trade_dates[-1], batch_no)

        # 18. 技术指标计算
        if config.ENABLE_TA_INDICATOR:
            print("[INFO] 计算技术指标...")
            latest_batch_no = get_latest_batch_no(mysql_engine)
            if ta_daily_mode:
                # ta-daily：加载全部 day/week/month/season 历史 K 线，确保 MACD/RSI/KDJ 等
                # 指标在窗口内最早一天也能量化为正确值，再按"每天 4 周期"对齐
                kline_for_ta = load_kline_full_for_daily_ta(mysql_engine)
            elif ta_only_mode:
                # ta-only：直接按日期窗口从 K 线表取数计算，与导入按钮脱钩
                kline_for_ta = load_kline_for_indicators_by_dates(mysql_engine, start_date, end_date)
            else:
                kline_for_ta = load_kline_for_indicators(mysql_engine, latest_batch_no)

            if not kline_for_ta.empty:
                result_df = TechnicalIndicatorCalculator.calculate(kline_for_ta)

                if ta_daily_mode:
                    # --ta-daily：把 (etf, period) 的 K 线指标结果按"每个交易日"对齐为 4 条/天/ETF，
                    # 4 个 period 的 trade_time 都用当天 00:00:00，复用 etf_ta_indicator 原表唯一键 (etf, period, trade_time) 不冲突。
                    aligned = align_ta_to_daily(result_df, trade_dates)
                    if aligned.empty:
                        print("[WARN] --ta-daily 对齐后无数据，跳过写入")
                    else:
                        aligned["etl_batch_no"] = batch_no
                        print(f"[INFO] --ta-daily 对齐后 rows={len(aligned)} (≈{len(trade_dates)} 天 × 4 周期 × ETF 数)")
                        writer.upsert_etf_ta_indicator(aligned, batch_no=batch_no, batch_manager=batch_manager)
                    print_df_basic("按天对齐结果", aligned)
                elif ta_only_mode:
                    # --ta-only 模式：按日期窗口过滤计算结果，只保留窗口内的指标行
                    # 每天4条（day/week/month/season），10天=40条，而非整个回看窗口的全部历史
                    result_df = filter_rows_by_date_range(result_df, start_date, end_date)
                    print(f"[INFO] --ta-only 模式：按日期窗口过滤后 rows={len(result_df)}")
                    print_df_basic("技术指标结果", result_df)
                else:
                    if config.is_incremental_mode() and latest_batch_no:
                        result_df = filter_latest_batch_rows(result_df, latest_batch_no)
                        result_df = keep_only_latest_row_per_group(result_df)
                    print_df_basic("技术指标结果", result_df)

                # 分批写入并同步更新批次进度（前端可实时看到累计条数）
                # 注意：--ta-daily 已经在上面把 aligned 写入了 etf_ta_indicator，
                # 此处不能再次写入 result_df，否则会把"按周期记"的数据也写进去，破坏按天对齐
                if not ta_daily_mode:
                    writer.upsert_etf_ta_indicator(result_df, batch_no=batch_no, batch_manager=batch_manager)
            else:
                print("[WARN] 无K线数据，跳过技术指标计算")

        # 19. 标记批次成功
        # kline_count 在 L2 分批写入时已通过 add_kline_progress 累加，这里不再重复传入，避免覆盖
        batch_manager.finish_batch(batch_no, "SUCCESS", etf_count=len(etf_codes))
        print("[INFO] 全部完成")

    except Exception as e:
        error_msg = str(e)
        print(f"[ERROR] 脚本执行失败: {error_msg}")
        traceback.print_exc()
        batch_manager.finish_batch(batch_no, "FAILED", error_message=error_msg)
        raise
    finally:
        logout_amazingdata()


if __name__ == "__main__":
    main()
