package com.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.demo.entity.*;
import com.demo.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * ETF AI 智能体专用数据查询服务（只读）。
 * 所有方法均只执行 SELECT，不包含任何写入或修改操作。
 */
@Service
public class EtfAiDataService {

    @Autowired private EtfSecurityMasterMapper    securityMasterMapper;
    @Autowired private EtfMarketSnapshotMapper    marketSnapshotMapper;
    @Autowired private EtfMarketKlineMapper       marketKlineMapper;
    @Autowired private EtfTaIndicatorMapper       taIndicatorMapper;
    @Autowired private EtfFiveDimensionResonanceMapper resonanceMapper;
    @Autowired private EtfFundFlowSummaryMapper   fundFlowMapper;
    @Autowired private EtfFundShareMapper         fundShareMapper;
    @Autowired private EtfFundIopvMapper          fundIopvMapper;
    @Autowired private TradeCalendarMapper        tradeCalendarMapper;

    /** 限制最大查询条数，防止 Token 溢出 */
    private static int cap(Integer n) {
        if (n == null || n < 1) return 20;
        return Math.min(n, 100);
    }

    private static String yn(Integer v) {
        return v != null && v == 1 ? "是" : "否";
    }

    private String resolveLatestTradeDate() {
        QueryWrapper<TradeCalendar> w = new QueryWrapper<>();
        w.eq("is_open", 1)
         .orderByDesc("trade_date")
         .last("LIMIT 1");
        List<TradeCalendar> rows = tradeCalendarMapper.selectList(w);
        if (rows == null || rows.isEmpty()) return null;
        LocalDate tradeDate = rows.get(0).getTradeDate();
        return tradeDate == null ? null : tradeDate.toString();
    }

    // ─────────────────────────────────────────────
    // 1. ETF 基础信息
    // ─────────────────────────────────────────────
    public String queryEtfList(String keyword, Integer limit) {
        QueryWrapper<EtfSecurityMaster> w = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like("etf_code", keyword);
        }
        w.last("LIMIT " + cap(limit));
        List<EtfSecurityMaster> list = securityMasterMapper.selectList(w);
        if (list.isEmpty()) return "未查询到ETF基础信息数据。";
        StringBuilder sb = new StringBuilder("ETF基础信息（共 " + list.size() + " 条）：\n");
        for (EtfSecurityMaster e : list) {
            sb.append(String.format(
                "代码:%s 市场:%s 证券状态:%s 昨收:%s 涨停:%s 跌停:%s 上市日:%s 活跃:%s%n",
                e.getEtfCode(), e.getMarket(), e.getSecurityStatus(),
                e.getPreClose(), e.getHighLimited(), e.getLowLimited(),
                e.getListDate(), yn(e.getIsActive())));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // 2. ETF 行情快照
    // ─────────────────────────────────────────────
    public String queryMarketSnapshot(String etfCode, Integer limit) {
        QueryWrapper<EtfMarketSnapshot> w = new QueryWrapper<>();
        if (StringUtils.hasText(etfCode)) w.eq("etf_code", etfCode);
        String latestTradeDate = resolveLatestTradeDate();
        if (StringUtils.hasText(latestTradeDate)) {
            w.apply("DATE(trade_time) = {0}", latestTradeDate);
        }
        w.orderByDesc("trade_time").last("LIMIT " + cap(limit));
        List<EtfMarketSnapshot> list = marketSnapshotMapper.selectList(w);
        if (list.isEmpty()) return "未查询到行情快照数据。";
        StringBuilder sb = new StringBuilder("ETF行情快照（共 " + list.size() + " 条）：\n");
        for (EtfMarketSnapshot e : list) {
            sb.append(String.format(
                "代码:%s 时间:%s 最新价:%s 开盘:%s 最高:%s 最低:%s 收盘:%s 成交量:%s 成交额:%s%n",
                e.getEtfCode(), e.getTradeTime(), e.getLastPrice(),
                e.getOpenPrice(), e.getHighPrice(), e.getLowPrice(), e.getClosePrice(),
                e.getVolume(), e.getAmount()));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // 3. ETF K 线数据
    // ─────────────────────────────────────────────
    public String queryKlineData(String etfCode, String period, String startDate, String endDate, Integer limit) {
        if (!StringUtils.hasText(startDate) && !StringUtils.hasText(endDate)) {
            String latestTradeDate = resolveLatestTradeDate();
            if (StringUtils.hasText(latestTradeDate)) {
                LocalDate latest = LocalDate.parse(latestTradeDate);
                startDate = latest.minusDays(6).toString();
                endDate = latest.toString();
            }
        }

        QueryWrapper<EtfMarketKline> w = new QueryWrapper<>();
        if (StringUtils.hasText(etfCode))    w.eq("etf_code", etfCode);
        if (StringUtils.hasText(period))     w.eq("period", period);
        if (StringUtils.hasText(startDate))  w.ge("trade_time", startDate + " 00:00:00");
        if (StringUtils.hasText(endDate))    w.le("trade_time", endDate + " 23:59:59");
        w.orderByDesc("trade_time").last("LIMIT " + cap(limit));
        List<EtfMarketKline> list = marketKlineMapper.selectList(w);
        if (list.isEmpty()) return "未查询到K线数据。";
        StringBuilder sb = new StringBuilder("ETF K线数据（共 " + list.size() + " 条）：\n");
        for (EtfMarketKline e : list) {
            sb.append(String.format(
                "代码:%s 周期:%s 时间:%s 开:%s 高:%s 低:%s 收:%s 量:%s 额:%s%n",
                e.getEtfCode(), e.getPeriod(), e.getTradeTime(),
                e.getOpenPrice(), e.getHighPrice(), e.getLowPrice(), e.getClosePrice(),
                e.getVolume(), e.getAmount()));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // 4. ETF 技术指标
    // ─────────────────────────────────────────────
    public String queryTaIndicator(String etfCode, String period, String tradeDate,
                                   Integer signalTrendLong, Integer signalWarning, Integer limit) {
        if (!StringUtils.hasText(tradeDate)) {
            tradeDate = resolveLatestTradeDate();
        }

        QueryWrapper<EtfTaIndicator> w = new QueryWrapper<>();
        if (StringUtils.hasText(etfCode))  w.eq("etf_code", etfCode);
        if (StringUtils.hasText(period))   w.eq("period", period);
        if (StringUtils.hasText(tradeDate)) w.apply("DATE(trade_time) = {0}", tradeDate);
        if (signalTrendLong != null)        w.eq("signal_trend_long", signalTrendLong);
        if (signalWarning != null)          w.eq("signal_warning", signalWarning);
        w.orderByDesc("trade_time").last("LIMIT " + cap(limit));
        List<EtfTaIndicator> list = taIndicatorMapper.selectList(w);
        if (list.isEmpty()) return "未查询到技术指标数据。";
        StringBuilder sb = new StringBuilder("ETF技术指标（共 " + list.size() + " 条）：\n");
        for (EtfTaIndicator e : list) {
            String macdDir = e.getMacdHistDirection() == null ? "-"
                : (e.getMacdHistDirection() == 1 ? "增强" : (e.getMacdHistDirection() == -1 ? "减弱" : "持平"));
            String sarTrend = e.getSarTrend() == null ? "-"
                : (e.getSarTrend() == 1 ? "多头" : (e.getSarTrend() == -1 ? "空头" : "未知"));
            sb.append(String.format(
                "代码:%s 周期:%s 时间:%s MACD:%s MACD红柱:%s MACD方向:%s SAR:%s SAR多头:%s SAR趋势:%s 趋势多头:%s 动量多头:%s 风险预警:%s%n",
                e.getEtfCode(), e.getPeriod(), e.getTradeTime(),
                e.getMacd(), yn(e.getIsMacdRed()), macdDir,
                e.getSarValue(), yn(e.getIsSarBullish()), sarTrend,
                yn(e.getSignalTrendLong()), yn(e.getSignalMomentumLong()),
                e.getSignalWarning() != null && e.getSignalWarning() == 1 ? "存在风险" : "暂未发现风险"));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // 5. ETF 五维共振
    // ─────────────────────────────────────────────
    public String queryFiveDimensionResonance(String etfCode, Integer isTodayTriggered,
                                               Integer isYesterdayTriggered, Integer limit) {
        QueryWrapper<EtfFiveDimensionResonance> w = new QueryWrapper<>();
        if (StringUtils.hasText(etfCode))       w.eq("etf_code", etfCode);
        String latestTradeDate = resolveLatestTradeDate();
        if (StringUtils.hasText(latestTradeDate)) {
            w.eq("trade_date", latestTradeDate);
        }
        if (isTodayTriggered != null)            w.eq("is_today_triggered", isTodayTriggered);
        if (isYesterdayTriggered != null)        w.eq("is_yesterday_triggered", isYesterdayTriggered);
        w.orderByDesc("trade_time").last("LIMIT " + cap(limit));
        List<EtfFiveDimensionResonance> list = resonanceMapper.selectList(w);
        if (list.isEmpty()) return "未查询到五维共振数据。";
        StringBuilder sb = new StringBuilder("ETF五维共振（共 " + list.size() + " 条）：\n");
        for (EtfFiveDimensionResonance e : list) {
            String macdDir = e.getMacdHistDirection() == null ? "-"
                : (e.getMacdHistDirection() == 1 ? "增强" : (e.getMacdHistDirection() == -1 ? "减弱" : "持平"));
            String sarTrend = e.getSarTrend() == null ? "-"
                : (e.getSarTrend() == 1 ? "多头" : (e.getSarTrend() == -1 ? "空头" : "未知"));
            sb.append(String.format(
                "代码:%s 名称:%s 时间:%s 收盘:%s 趋势多头:%s 动量多头:%s 风险:%s 今触发:%s 昨触发:%s 最后触发日:%s MACD:%s MACD方向:%s SAR多头:%s SAR趋势:%s RSI6:%s%n",
                e.getEtfCode(), e.getEtfName(), e.getTradeTime(), e.getClosePrice(),
                yn(e.getSignalTrendLong()), yn(e.getSignalMomentumLong()),
                e.getSignalWarning() != null && e.getSignalWarning() == 1 ? "有风险" : "无风险",
                yn(e.getIsTodayTriggered()), yn(e.getIsYesterdayTriggered()),
                e.getLastTriggeredDate(), e.getMacd(), macdDir,
                yn(e.getIsSarBullish()), sarTrend, e.getRsi6()));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // 6. ETF 资金流向汇总
    // ─────────────────────────────────────────────
    public String queryFundFlow(String etfCode, String startDate, String endDate, Integer limit) {
        if (!StringUtils.hasText(startDate) && !StringUtils.hasText(endDate)) {
            String latestTradeDate = resolveLatestTradeDate();
            if (StringUtils.hasText(latestTradeDate)) {
                startDate = latestTradeDate;
                endDate = latestTradeDate;
            }
        }

        QueryWrapper<EtfFundFlowSummary> w = new QueryWrapper<>();
        if (StringUtils.hasText(etfCode))   w.eq("etf_code", etfCode);
        if (StringUtils.hasText(startDate)) w.ge("trade_date", startDate);
        if (StringUtils.hasText(endDate))   w.le("trade_date", endDate);
        w.orderByDesc("trade_date").last("LIMIT " + cap(limit));
        List<EtfFundFlowSummary> list = fundFlowMapper.selectList(w);
        if (list.isEmpty()) return "未查询到资金流向数据。";
        StringBuilder sb = new StringBuilder("ETF资金流向（共 " + list.size() + " 条）：\n");
        for (EtfFundFlowSummary e : list) {
            sb.append(String.format(
                "代码:%s 名称:%s 日期:%s 今日份额:%s 份额变动:%s 收盘价:%s 资金流向:%s 累计流向:%s 原因:%s%n",
                e.getEtfCode(), e.getEtfName(), e.getTradeDate(),
                e.getFundShareToday(), e.getShareChange(), e.getClosePrice(),
                e.getFundFlow(), e.getCumulativeFlow(), e.getChangeReason()));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // 7. ETF 基金份额
    // ─────────────────────────────────────────────
    public String queryFundShare(String etfCode, String startDate, String endDate, Integer limit) {
        if (!StringUtils.hasText(startDate) && !StringUtils.hasText(endDate)) {
            String latestTradeDate = resolveLatestTradeDate();
            if (StringUtils.hasText(latestTradeDate)) {
                startDate = latestTradeDate;
                endDate = latestTradeDate;
            }
        }

        QueryWrapper<EtfFundShare> w = new QueryWrapper<>();
        if (StringUtils.hasText(etfCode))   w.eq("etf_code", etfCode);
        if (StringUtils.hasText(startDate)) w.ge("change_date", startDate);
        if (StringUtils.hasText(endDate))   w.le("change_date", endDate);
        w.orderByDesc("change_date").last("LIMIT " + cap(limit));
        List<EtfFundShare> list = fundShareMapper.selectList(w);
        if (list.isEmpty()) return "未查询到份额变动数据。";
        StringBuilder sb = new StringBuilder("ETF基金份额（共 " + list.size() + " 条）：\n");
        for (EtfFundShare e : list) {
            sb.append(String.format(
                "代码:%s 变动日:%s 基金份额:%s 总份额:%s 流通份额:%s 变动原因:%s%n",
                e.getEtfCode(), e.getChangeDate(),
                e.getFundShare(), e.getTotalShare(), e.getFloatShare(), e.getChangeReason()));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // 8. ETF IOPV 净值估算
    // ─────────────────────────────────────────────
    public String queryFundIopv(String etfCode, String startDate, String endDate, Integer limit) {
        if (!StringUtils.hasText(startDate) && !StringUtils.hasText(endDate)) {
            String latestTradeDate = resolveLatestTradeDate();
            if (StringUtils.hasText(latestTradeDate)) {
                startDate = latestTradeDate;
                endDate = latestTradeDate;
            }
        }

        QueryWrapper<EtfFundIopv> w = new QueryWrapper<>();
        if (StringUtils.hasText(etfCode))   w.eq("etf_code", etfCode);
        if (StringUtils.hasText(startDate)) w.ge("price_date", startDate);
        if (StringUtils.hasText(endDate))   w.le("price_date", endDate);
        w.orderByDesc("price_date").last("LIMIT " + cap(limit));
        List<EtfFundIopv> list = fundIopvMapper.selectList(w);
        if (list.isEmpty()) return "未查询到IOPV净值数据。";
        StringBuilder sb = new StringBuilder("ETF IOPV净值（共 " + list.size() + " 条）：\n");
        for (EtfFundIopv e : list) {
            sb.append(String.format("代码:%s 日期:%s IOPV净值:%s%n",
                e.getEtfCode(), e.getPriceDate(), e.getIopvNav()));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // 9. 交易日历
    // ─────────────────────────────────────────────
    public String queryTradeCalendar(String startDate, String endDate, Integer isOpen) {
        if (!StringUtils.hasText(startDate) && !StringUtils.hasText(endDate)) {
            String latestTradeDate = resolveLatestTradeDate();
            if (StringUtils.hasText(latestTradeDate)) {
                LocalDate latest = LocalDate.parse(latestTradeDate);
                startDate = latest.minusDays(30).toString();
                endDate = latestTradeDate;
            }
        }

        QueryWrapper<TradeCalendar> w = new QueryWrapper<>();
        if (StringUtils.hasText(startDate)) w.ge("trade_date", startDate);
        if (StringUtils.hasText(endDate))   w.le("trade_date", endDate);
        if (isOpen != null)                 w.eq("is_open", isOpen);
        w.orderByAsc("trade_date").last("LIMIT 100");
        List<TradeCalendar> list = tradeCalendarMapper.selectList(w);
        if (list.isEmpty()) return "未查询到交易日历数据。";
        StringBuilder sb = new StringBuilder("交易日历（共 " + list.size() + " 条）：\n");
        for (TradeCalendar e : list) {
            sb.append(String.format("日期:%s 市场:%s 是否交易日:%s%n",
                e.getTradeDate(), e.getMarket(), e.getIsOpen() == 1 ? "是" : "否"));
        }
        return sb.toString();
    }
}
