package com.demo.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.demo.entity.EtfFiveDimensionReport;
import com.demo.entity.EtfFiveDimensionResonance;
import com.demo.entity.EtfFundFlowSummary;
import com.demo.entity.EtfFundShare;
import com.demo.entity.EtfMarketSnapshot;
import com.demo.entity.EtfTaIndicator;
import com.demo.entity.SysParam;
import com.demo.entity.TradeCalendar;
import com.demo.mapper.EtfFiveDimensionResonanceMapper;
import com.demo.mapper.EtfFundFlowSummaryMapper;
import com.demo.mapper.EtfFundShareMapper;
import com.demo.mapper.EtfMarketSnapshotMapper;
import com.demo.mapper.EtfTaIndicatorMapper;
import com.demo.mapper.TradeCalendarMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EtfFiveDimensionReportGenerationService {

    private static final String MODEL = "MiniMax-M2.7";
    private static final String MINIMAX_URL = "https://api.minimaxi.com/v1/chat/completions";
    private static final int ROW_LIMIT = 12;
    private static final int SYSTEM_PROMPT_MAX_CHARS = 1500;
    private static final int USER_CONTENT_MAX_CHARS = 28000;
    private static final int REPORT_MAX_CHARS = 3500;
            private static final String[] REPORT_TABLES = new String[] {
                "ETF基金份额表",
                "ETF技术指标表",
                "ETF资金流向统计表"
        };

    @Autowired
    private SysParamService sysParamService;
    @Autowired
    private EtlProgressService etlProgressService;
    @Autowired
    private EtfFiveDimensionReportService reportService;
    @Autowired
    private EtfFiveDimensionResonanceMapper resonanceMapper;
    @Autowired
    private EtfFundFlowSummaryMapper fundFlowSummaryMapper;
    @Autowired
    private EtfFundShareMapper fundShareMapper;
    @Autowired
    private EtfTaIndicatorMapper taIndicatorMapper;
    @Autowired
    private EtfMarketSnapshotMapper marketSnapshotMapper;
    @Autowired
    private TradeCalendarMapper tradeCalendarMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public EtfFiveDimensionReport generateLatestReport(String publisher) {
        String apiKey = readParamValue("ai.dashscope.api-key");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("AI 助手尚未配置 API Key，请先在参数配置中填写 ai.dashscope.api-key");
        }

        LocalDate latestTradeDate = resolveLatestTradeDate();
        if (latestTradeDate == null) {
            throw new IllegalStateException("未找到可用于生成报告的最新交易日数据");
        }

        JSONObject context = buildLatestTradeDateContext(latestTradeDate);

        String markdown = callAiForReport(apiKey, latestTradeDate, context);
        if (!StringUtils.hasText(markdown)) {
            throw new IllegalStateException("AI 未返回有效报告内容");
        }

        String normalizedMarkdown = normalizeMarkdown(markdown);
        normalizedMarkdown = stripThinkingBlocks(normalizedMarkdown);
        normalizedMarkdown = removeLeadingTitle(normalizedMarkdown);
        normalizedMarkdown = stripFiveDimensionDefinition(normalizedMarkdown);
        normalizedMarkdown = appendDataSourceSummary(normalizedMarkdown, latestTradeDate);
        normalizedMarkdown = trimToMaxChars(normalizedMarkdown, REPORT_MAX_CHARS);
        normalizedMarkdown = enforceKeyConclusionsBold(normalizedMarkdown);
        String title = "ETF五维共振技术指标分析报告（" + latestTradeDate + "）";

        EtfFiveDimensionReport entity = new EtfFiveDimensionReport();
        entity.setTitle(title);
        entity.setContentMd(normalizedMarkdown);
        entity.setPublisher(StringUtils.hasText(publisher) ? publisher.trim() : "AI智能体");
        // 发布时间：用 latestTradeDate 当天 00:00:00（与标题日期对齐，时分秒=0）
        entity.setPublishTime(latestTradeDate.atStartOfDay());
        entity.setIsActive(1);
        reportService.save(entity);
        return entity;
    }

    private LocalDate resolveLatestTradeDate() {
        // 优先级：fundFlow（资金流向，通常每日更新）> taIndicator（技术指标）> checkpoint > tradeCalendar
        // 不要用 resonance（已弃用，可能长期空）
        LocalDate fromFlow = parseToLocalDate(selectMaxDateFromFundFlow());
        if (fromFlow != null) {
            return fromFlow;
        }
        LocalDate fromTa = parseToLocalDate(selectMaxDateFromTaIndicator());
        if (fromTa != null) {
            return fromTa;
        }
        LocalDate fromProgress = parseFromYmdInt(etlProgressService.getCurrentTradeDate());
        if (fromProgress != null) {
            return fromProgress;
        }
        return parseToLocalDate(selectMaxDateFromTradeCalendar());
    }

    private Object selectMaxDateFromTaIndicator() {
        try {
            QueryWrapper<EtfTaIndicator> wrapper = new QueryWrapper<>();
            wrapper.select("MAX(DATE(trade_time))");
            wrapper.last("LIMIT 1");
            List<Object> rows = taIndicatorMapper.selectObjs(wrapper);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    private Object selectMaxDateFromResonance() {
        try {
            QueryWrapper<EtfFiveDimensionResonance> wrapper = new QueryWrapper<>();
            wrapper.select("MAX(trade_date)");
            wrapper.last("LIMIT 1");
            List<Object> rows = resonanceMapper.selectObjs(wrapper);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    private Object selectMaxDateFromFundFlow() {
        try {
            QueryWrapper<EtfFundFlowSummary> wrapper = new QueryWrapper<>();
            wrapper.select("MAX(trade_date)");
            wrapper.last("LIMIT 1");
            List<Object> rows = fundFlowSummaryMapper.selectObjs(wrapper);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    private Object selectMaxDateFromTradeCalendar() {
        try {
            QueryWrapper<TradeCalendar> wrapper = new QueryWrapper<>();
            wrapper.eq("is_open", 1);
            wrapper.select("MAX(trade_date)");
            wrapper.last("LIMIT 1");
            List<Object> rows = tradeCalendarMapper.selectObjs(wrapper);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    private JSONObject buildLatestTradeDateContext(LocalDate latestTradeDate) {
        JSONObject context = new JSONObject();
        context.put("latestTradeDate", latestTradeDate.toString());

        List<EtfFiveDimensionResonance> resonanceRows = safeQuery(() -> {
            QueryWrapper<EtfFiveDimensionResonance> w = new QueryWrapper<>();
            w.eq("trade_date", latestTradeDate)
                    .orderByDesc("is_today_triggered")
                    .orderByDesc("close_price")
                    .last("LIMIT " + ROW_LIMIT);
            return resonanceMapper.selectList(w);
        });

        long todayTriggered = resonanceRows.stream()
                .filter(r -> r.getIsTodayTriggered() != null && r.getIsTodayTriggered() == 1)
                .count();
        long warningCount = resonanceRows.stream()
                .filter(r -> r.getSignalWarning() != null && r.getSignalWarning() == 1)
                .count();

        List<EtfFundFlowSummary> fundFlowInTop = safeQuery(() -> {
            // 净流入 Top 5（fund_flow DESC）
            QueryWrapper<EtfFundFlowSummary> w = new QueryWrapper<>();
            w.eq("trade_date", latestTradeDate)
                    .gt("fund_flow", 0)
                    .orderByDesc("fund_flow")
                    .last("LIMIT 5");
            return fundFlowSummaryMapper.selectList(w);
        }, "fundFlowInTop5");

        List<EtfFundFlowSummary> fundFlowOutTop = safeQuery(() -> {
            // 净流出 Top 5（fund_flow ASC，取负值最大）
            QueryWrapper<EtfFundFlowSummary> w = new QueryWrapper<>();
            w.eq("trade_date", latestTradeDate)
                    .lt("fund_flow", 0)
                    .orderByAsc("fund_flow")
                    .last("LIMIT 5");
            return fundFlowSummaryMapper.selectList(w);
        }, "fundFlowOutTop5");

        // 兼容旧的 fundFlowRows（避免大量下游引用改动）
        List<EtfFundFlowSummary> fundFlowRows = new ArrayList<>();
        fundFlowRows.addAll(fundFlowInTop);
        fundFlowRows.addAll(fundFlowOutTop);

        List<EtfFundShare> fundShareRows = safeQuery(() -> {
            QueryWrapper<EtfFundShare> w = new QueryWrapper<>();
            w.eq("change_date", latestTradeDate)
                    .orderByDesc("fund_share")
                    .last("LIMIT " + ROW_LIMIT);
            return fundShareMapper.selectList(w);
        });

        // 先算 marketStats（拿五维共振代码），再算 taRows（避免重复查询）
        Map<String, Object> marketStats = safeQueryMap(() -> computeMarketStats(latestTradeDate), "marketStats");

        // taRows = 关键 ETF 列表的 4 周期数据
        // "关键 ETF" = 资金 Top1 流入 + 资金 Top1 流出 + 五维共振标的 + top 趋势多头 + top 风险预警
        // 不再取全市场 5000+ 条（AI 看不完），只针对代表性 ETF 取数
        String dayStart = latestTradeDate + " 00:00:00";
        Set<String> keyEtfCodes = new LinkedHashSet<>();
        // 资金 Top1 流入 + Top1 流出（避免 token 超限）
        if (!fundFlowInTop.isEmpty()) keyEtfCodes.add(fundFlowInTop.get(0).getEtfCode());
        if (!fundFlowOutTop.isEmpty()) keyEtfCodes.add(fundFlowOutTop.get(0).getEtfCode());
        // 五维共振代码从 marketStats 取
        Object resonanceCodesObj = marketStats.get("五维共振标的代码列表");
        if (resonanceCodesObj instanceof List) {
            for (Object o : (List<?>) resonanceCodesObj) keyEtfCodes.add(String.valueOf(o));
        }
        // top 趋势多头 + top 风险预警（从 marketStats 衍生不够方便，直接 query）
        List<String> topTrendCodes = safeQuery(() -> {
            QueryWrapper<EtfTaIndicator> w = new QueryWrapper<>();
            w.eq("trade_time", dayStart)
                    .eq("period", "day")
                    .eq("signal_trend_long", 1)
                    .orderByDesc("close_price")
                    .select("DISTINCT etf_code")
                    .last("LIMIT 5");
            return taIndicatorMapper.selectList(w).stream().map(EtfTaIndicator::getEtfCode).toList();
        }, "topTrendCodes");
        keyEtfCodes.addAll(topTrendCodes);
        List<String> topWarningCodes = safeQuery(() -> {
            QueryWrapper<EtfTaIndicator> w = new QueryWrapper<>();
            w.eq("trade_time", dayStart)
                    .eq("period", "day")
                    .eq("signal_warning", 1)
                    .orderByAsc("close_price")
                    .select("DISTINCT etf_code")
                    .last("LIMIT 5");
            return taIndicatorMapper.selectList(w).stream().map(EtfTaIndicator::getEtfCode).toList();
        }, "topWarningCodes");
        keyEtfCodes.addAll(topWarningCodes);

        List<EtfTaIndicator> taRows = safeQuery(() -> {
            if (keyEtfCodes.isEmpty()) {
                return List.<EtfTaIndicator>of();
            }
            QueryWrapper<EtfTaIndicator> w = new QueryWrapper<>();
            w.eq("trade_time", dayStart)
                    .in("period", "day", "week", "month", "season")
                    .in("etf_code", keyEtfCodes);
            return taIndicatorMapper.selectList(w);
        }, "taRows[keyEtfs]");

        // 全市场汇总已经提前算过（marketStats 在 taRows 之前）

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resonanceRowCount", resonanceRows.size());
        summary.put("todayTriggeredCount", todayTriggered);
        summary.put("warningCount", warningCount);
        summary.put("fundFlowRowCount", fundFlowRows.size());
        summary.put("taRowCount", taRows.size());
        summary.put("marketStats", marketStats);

        context.put("summary", summary);
        context.put("resonanceRows", compactResonanceRows(resonanceRows));
        context.put("fundFlowRows", compactFundFlowRows(fundFlowRows));
        context.put("fundShareRows", compactFundShareRows(fundShareRows));
        context.put("taRows", compactTaRows(taRows));

        // 检查资金 Top1 流入/流出 ETF 是否在 taRows（day 周期）中有数据
        Set<String> taRowDayCodes = new HashSet<>();
        for (EtfTaIndicator r : taRows) {
            if ("day".equals(r.getPeriod())) {
                taRowDayCodes.add(r.getEtfCode());
            }
        }
        List<String> missingDayData = new ArrayList<>();
        for (EtfFundFlowSummary f : fundFlowInTop) {
            if (!taRowDayCodes.contains(f.getEtfCode())) {
                missingDayData.add(f.getEtfCode());
            }
        }
        for (EtfFundFlowSummary f : fundFlowOutTop) {
            if (!taRowDayCodes.contains(f.getEtfCode())) {
                missingDayData.add(f.getEtfCode());
            }
        }
        context.put("taIndicatorMissing", missingDayData);
        return context;
    }

    private String callAiForReport(String apiKey,
                                   LocalDate latestTradeDate,
                                   JSONObject context) {
        JSONObject payload = new JSONObject();
        payload.put("latestTradeDate", latestTradeDate.toString());
        payload.put("dataset", context);

        String systemPrompt = trimToMaxChars(
            "# 角色\n"
                + "你是ETF技术分析师，根据 etf_ta_indicator 和 etf_fund_share 两张表，输出当日《ETF五维共振技术指标分析报告》。以技术面为主，不编造数据，不做回测。\n"
                + "\n# 报告结构（按此5段顺序输出，不要漏段）\n"
                + "1. 全市场温度计：趋势多头、动量多头、转弱预警、四周期共振的数量与占比，一句话总结。\n"
                + "2. 四周期共振名单：列出代码；若没有则说明市场处于周期分歧状态。\n"
                + "3. 代表性ETF拆解：取【当日资金净流入 Top1 + 当日资金净流出 Top1】，紧凑列出代码/收盘/SAR方向/MACD/技术评分。\n"
                + "4. 综合研判：全市场评分（0-10）、倾向、关键触发、操作参考。\n"
                + "5. 风险提示：含风险预警=是的标的、资金面双重风险标的，至少 2-3 条。\n"
                + "\n# 约束\n"
                + "- 不要联网；不要写新闻政策；不要预测大盘点位；不出现\"必涨/必跌\"；关键结论加粗。\n"
                + "- 数据字段已翻译成中文，直接用；缺失数据标注\"缺失\"，不编造。\n"
                + "- 全文 1000-1500 字。\n"
                + "- 不要用 # / ## 等 Markdown 标题行；用 \"一、二、三、...\"或\"1./2./...\"。\n"
                + "- 资金段不超过全文30%。",
            SYSTEM_PROMPT_MAX_CHARS
        );
        String userDataJson = JSON.toJSONString(payload);

        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        messages.add(sys);

        String userContent = "请按 system prompt 的【报告结构】直接输出最终报告。\n\n"
            + "硬约束（违反任何一条都视为失败）：\n"
            + "1) 不要输出任何 <think>...</think> 块、思考过程、用户要求复述、约束说明等元信息；正文不要标题行（不要用 # / ## 等 Markdown 标题），用 \"一、二、三、...\" 或 \"1./2./...\"。\n"
            + "2) 所有数字、ETF 名称、份额、净流入、累计、字段值都必须能溯源到下面的 JSON 数据；JSON 数据里没有的字段或 ETF 一律不得出现在报告中（禁止编造、补全、外推）。\n"
            + "3) 不要使用 【】、---、*** 等装饰性符号分段；用空行分段。\n"
            + "4) **正文严禁出现英文字段名**：禁止出现 signal_warning、trendLong、momentumLong、is_macd_red、is_sar_bullish、MACD_红柱 等英文/拼音术语；必须写成\"风险预警触发\"\"趋势多头\"\"动量多头\"\"MACD 红柱\"\"SAR 多头\"。\n"
            + "5) 数据上下文：marketStats 是全市场聚合统计；taRows 是关键 ETF 的 4 周期数据（资金 Top1 流入/流出 + 五维共振 + top 趋势多头 + top 风险预警，约 20-50 只）；fundFlowRows 是净流入Top5 + 净流出Top5；fundShareRows 是份额。\n"
            + "6) 不要联网；不要写新闻/政策/网址。\n\n"
            + "数据如下(JSON)：\n" + userDataJson;
        userContent = trimToMaxChars(userContent, USER_CONTENT_MAX_CHARS);

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", userContent);
        messages.add(user);

        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("stream", false);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2000);
        body.put("messages", messages);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MINIMAX_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(180))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status != 200) {
                // 截断 body 避免超长错误信息
                String respBody = response.body() == null ? "" : response.body();
                String preview = respBody.length() > 500 ? respBody.substring(0, 500) + "..." : respBody;
                throw new IllegalStateException("AI HTTP " + status + ": " + preview);
            }
            JSONObject result = JSON.parseObject(response.body());
            if (result == null) {
                throw new IllegalStateException("AI 返回为空");
            }
            if (result.containsKey("error")) {
                JSONObject error = result.getJSONObject("error");
                String message = error == null ? "AI 服务错误" : error.getString("message");
                throw new IllegalStateException(message);
            }
            JSONArray choices = result.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("AI 返回结构缺少 choices");
            }
            JSONObject first = choices.getJSONObject(0);
            JSONObject message = first == null ? null : first.getJSONObject("message");
            String content = message == null ? null : message.getString("content");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("AI 未返回有效文本");
            }
            return content;
        } catch (Exception ex) {
            throw new IllegalStateException("调用AI生成报告失败: " + ex.getMessage(), ex);
        }
    }

    private String normalizeMarkdown(String text) {
        String content = text == null ? "" : text.trim();
        if (content.startsWith("```")) {
            int firstBreak = content.indexOf('\n');
            if (firstBreak > 0) {
                content = content.substring(firstBreak + 1);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            content = content.trim();
        }
        return content;
    }

    private List<Map<String, Object>> compactResonanceRows(List<EtfFiveDimensionResonance> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EtfFiveDimensionResonance r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r.getEtfCode());
            m.put("name", r.getEtfName());
            m.put("tradeDate", r.getTradeDate());
            m.put("close", r.getClosePrice());
            m.put("todayTriggered", r.getIsTodayTriggered());
            m.put("trendLong", r.getSignalTrendLong());
            m.put("momentumLong", r.getSignalMomentumLong());
            m.put("warning", r.getSignalWarning());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> compactFundFlowRows(List<EtfFundFlowSummary> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EtfFundFlowSummary r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("代码", r.getEtfCode());
            m.put("名称", r.getEtfName());
            m.put("交易日", r.getTradeDate());
            m.put("今日净流入_元", r.getFundFlow());
            m.put("累计净流入_元", r.getCumulativeFlow());
            m.put("份额变化_份", r.getShareChange());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> compactFundShareRows(List<EtfFundShare> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EtfFundShare r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("代码", r.getEtfCode());
            m.put("份额变动日", r.getChangeDate());
            m.put("公告日", r.getAnnDate());
            m.put("份额_万份", r.getFundShare());
            m.put("流通份额_万份", r.getFloatShare());
            m.put("变动原因", r.getChangeReason());
            m.put("是否合并数据", r.getIsConsolidatedData());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> compactTaRows(List<EtfTaIndicator> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EtfTaIndicator r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("代码", r.getEtfCode());
            m.put("周期", r.getPeriod() == null ? null : periodLabel(r.getPeriod()));
            m.put("收盘价", r.getClosePrice());
            // MACD 系列
            m.put("MACD_DIF", r.getDif());
            m.put("MACD_DEA", r.getDea());
            m.put("MACD_柱值", r.getMacd());
            m.put("MACD_红柱", yesNo(r.getIsMacdRed()));
            m.put("MACD_柱方向", histDirLabel(r.getMacdHistDirection()));
            // SAR 系列
            m.put("SAR_值", r.getSarValue());
            m.put("SAR_多头", yesNo(r.getIsSarBullish()));
            m.put("SAR_趋势", sarTrendLabel(r.getSarTrend()));
            // 复合信号
            m.put("趋势多头", yesNo(r.getSignalTrendLong()));
            m.put("动量多头", yesNo(r.getSignalMomentumLong()));
            m.put("风险预警", yesNo(r.getSignalWarning()));
            out.add(m);
        }
        return out;
    }

    private static String yesNo(Integer v) {
        if (v == null) return null;
        return v == 1 ? "是" : "否";
    }

    private static String periodLabel(String p) {
        if (p == null) return null;
        switch (p) {
            case "day": return "日线";
            case "week": return "周线";
            case "month": return "月线";
            case "season": return "季线";
            default: return p;
        }
    }

    private static String histDirLabel(Integer v) {
        if (v == null) return null;
        if (v == 1) return "柱变长(动能增强)";
        if (v == -1) return "柱变短(动能衰减)";
        return String.valueOf(v);
    }

    private static String sarTrendLabel(Integer v) {
        if (v == null) return null;
        if (v == 1) return "上升";
        if (v == -1) return "下降";
        return String.valueOf(v);
    }

    private List<Map<String, Object>> compactSnapshotRows(List<EtfMarketSnapshot> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EtfMarketSnapshot r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r.getEtfCode());
            m.put("tradeTime", r.getTradeTime());
            m.put("lastPrice", r.getLastPrice());
            m.put("amount", r.getAmount());
            m.put("volume", r.getVolume());
            out.add(m);
        }
        return out;
    }

    private String trimToMaxChars(String text, int maxChars) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String content = text.trim();
        if (content.length() <= maxChars) {
            return content;
        }
        int reserve = 80;
        int target = Math.max(0, maxChars - reserve);
        return content.substring(0, target)
                + "\n\n[内容过长，已自动截断以满足模型输入上限]";
    }

    private String enforceKeyConclusionsBold(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return markdown;
        }

        String[] lines = markdown.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i];
            String trimmed = line.trim();

            if (shouldBoldLine(trimmed)) {
                line = makeBoldLine(line);
            }

            if (i > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    private boolean shouldBoldLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        if (line.startsWith("#")) {
            return false;
        }
        if (line.contains("**")) {
            return false;
        }
        return line.contains("一句话总结")
                || line.contains("大盘涨跌预测")
                || line.contains("方向预测")
                || line.contains("关键结论")
                || line.contains("结论")
                || line.contains("建议");
    }

    private String makeBoldLine(String line) {
        int colonIdx = line.indexOf('：');
        if (colonIdx < 0) {
            colonIdx = line.indexOf(':');
        }

        if (colonIdx > 0 && colonIdx < line.length() - 1) {
            String prefix = line.substring(0, colonIdx + 1);
            String suffix = line.substring(colonIdx + 1).trim();
            if (!suffix.isEmpty()) {
                return prefix + " **" + suffix + "**";
            }
        }
        return "**" + line.trim() + "**";
    }

    /**
     * 去掉 AI 输出的 <think>...</think> 思维链块（即使 prompt 限制了，有些模型仍会输出）
     */
    private String stripThinkingBlocks(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return markdown;
        }
        // 1) 完整 think块
        String result = markdown.replaceAll("(?s)<think>.*?</think>", "").trim();
        // 2) 未闭合的 think 块（AI 输出 <think> 后没写 </think> 就直接进入正文）
        //    策略：以 "</think>" 切割，取最后一段；没有 </think> 但有 <think> 时，找第一个中文标点或换行截断
        int thinkOpen = result.indexOf("<think>");
        if (thinkOpen >= 0) {
            int after = result.indexOf("</think>", thinkOpen);
            if (after >= 0) {
                result = result.substring(after + "</think>".length()).trim();
            } else {
                // 未闭合：找第一个像"最新交易日"或"【"等正文起始符
                int idx = result.indexOf("最新交易日", thinkOpen);
                if (idx < 0) idx = result.indexOf("【", thinkOpen);
                if (idx < 0) idx = result.indexOf("\n", thinkOpen);
                if (idx > thinkOpen) {
                    result = result.substring(idx).trim();
                }
            }
        }
        return result;
    }

    private String stripFiveDimensionDefinition(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return markdown;
        }
        String[] lines = markdown.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.contains("五维共振定义")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString().trim();
    }

    private String appendDataSourceSummary(String markdown, LocalDate latestTradeDate) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(markdown)) {
            sb.append(markdown.trim());
            if (!markdown.trim().endsWith("\n")) {
                sb.append('\n');
            }
            sb.append('\n');
        }
        sb.append("数据来源说明：本报告按最新交易日期 ")
                .append(latestTradeDate)
                .append(" 锁定分析。参考表：")
                .append(String.join("、", REPORT_TABLES))
                .append("。");
        return sb.toString();
    }

    private String removeLeadingTitle(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return markdown;
        }
        String[] lines = markdown.split("\\r?\\n");
        int start = 0;
        while (start < lines.length && !StringUtils.hasText(lines[start])) {
            start++;
        }
        if (start >= lines.length) {
            return markdown.trim();
        }
        String firstLine = lines[start].trim();
        // 1) # / ## / ### 开头：去掉
        if (firstLine.startsWith("#")) {
            start++;
        }
        // 2) 首行像标题（短文本 + 含"分析报告/简报/日报/分析"等关键词，且后面有空行）
        else if (firstLine.length() <= 30
                && (firstLine.contains("分析报告") || firstLine.contains("技术指标分析")
                    || firstLine.contains("AI简报") || firstLine.endsWith("简报")
                    || firstLine.endsWith("报告"))
                && start + 1 < lines.length
                && StringUtils.hasText(lines[start + 1])) {
            // 判断第二行是"分析日期："这种元信息（也要删）
            start++;
            while (start < lines.length && !StringUtils.hasText(lines[start])) {
                start++;
            }
            if (start < lines.length && lines[start].trim().startsWith("分析日期")) {
                start++;
                while (start < lines.length && !StringUtils.hasText(lines[start])) {
                    start++;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString().trim();
    }

    private LocalDate parseFromYmdInt(Integer ymd) {
        if (ymd == null) {
            return null;
        }
        String text = String.valueOf(ymd).trim();
        if (!text.matches("\\d{8}")) {
            return null;
        }
        String iso = text.substring(0, 4) + "-" + text.substring(4, 6) + "-" + text.substring(6, 8);
        try {
            return LocalDate.parse(iso);
        } catch (Exception ex) {
            return null;
        }
    }

    private String readParamValue(String key) {
        LambdaQueryWrapper<SysParam> wrapper = new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, key)
                .eq(SysParam::getIsActive, 1)
                .orderByDesc(SysParam::getId)
                .last("LIMIT 1");
        SysParam param = sysParamService.getOne(wrapper, false);
        return param == null || param.getParamValue() == null ? "" : param.getParamValue().trim();
    }

    private LocalDate parseToLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime().toLocalDate();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.length() >= 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private <T> List<T> safeQuery(QuerySupplier<T> supplier) {
        return safeQuery(supplier, "<unnamed>");
    }

    private <T> List<T> safeQuery(QuerySupplier<T> supplier, String label) {
        try {
            List<T> rows = supplier.get();
            if (rows == null) {
                System.out.println("[WARN] safeQuery[" + label + "] returned null");
                return List.of();
            }
            System.out.println("[INFO] safeQuery[" + label + "] returned " + rows.size() + " rows");
            return rows;
        } catch (Exception ex) {
            System.err.println("[ERROR] safeQuery[" + label + "] failed: " + ex.getMessage());
            ex.printStackTrace();
            return List.of();
        }
    }

    private Map<String, Object> safeQueryMap(java.util.function.Supplier<Map<String, Object>> supplier, String label) {
        try {
            Map<String, Object> result = supplier.get();
            System.out.println("[INFO] safeQueryMap[" + label + "] computed");
            return result == null ? new LinkedHashMap<>() : result;
        } catch (Exception ex) {
            System.err.println("[ERROR] safeQueryMap[" + label + "] failed: " + ex.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 全市场 ta_indicator 统计（不限 LIMIT）：
     *   - 各周期样本数
     *   - 趋势多头/动量多头/转弱预警 数量与占比
     *   - 日+周+月+季 多周期趋势/动量共振 标的数
     */
    private Map<String, Object> computeMarketStats(LocalDate latestTradeDate) {
        Map<String, Object> stats = new LinkedHashMap<>();
        String dayStart = latestTradeDate + " 00:00:00";
        String dayEnd = latestTradeDate.plusDays(1) + " 00:00:00";

        // 各周期样本数（等值查询）
        Map<String, Integer> periodCounts = new LinkedHashMap<>();
        for (String p : new String[]{"day", "week", "month", "season"}) {
            LambdaQueryWrapper<EtfTaIndicator> qw = new LambdaQueryWrapper<>();
            qw.eq(EtfTaIndicator::getTradeTime, dayStart)
                    .eq(EtfTaIndicator::getPeriod, p);
            periodCounts.put(periodLabel(p), Math.toIntExact(taIndicatorMapper.selectCount(qw)));
        }
        stats.put("各周期样本数", periodCounts);

        // day 周期的 trendLong/momentumLong/warning 占比（等值查询）
        for (String signal : new String[]{"trendLong", "momentumLong", "warning"}) {
            Map<String, Object> one = new LinkedHashMap<>();
            for (int v : new int[]{0, 1}) {
                LambdaQueryWrapper<EtfTaIndicator> qw = new LambdaQueryWrapper<>();
                qw.eq(EtfTaIndicator::getTradeTime, dayStart)
                        .eq(EtfTaIndicator::getPeriod, "day");
                if (signal.equals("trendLong")) qw.eq(EtfTaIndicator::getSignalTrendLong, v);
                else if (signal.equals("momentumLong")) qw.eq(EtfTaIndicator::getSignalMomentumLong, v);
                else qw.eq(EtfTaIndicator::getSignalWarning, v);
                one.put(v == 1 ? "是" : "否", Math.toIntExact(taIndicatorMapper.selectCount(qw)));
            }
            String label = signal.equals("trendLong") ? "日线趋势多头"
                    : signal.equals("momentumLong") ? "日线动量多头"
                    : "日线风险预警";
            stats.put(label, one);
        }

        // 多周期共振：日+周+月 趋势同时=1（等值查询）
        List<Map<String, Object>> resonance = taIndicatorMapper.selectMaps(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EtfTaIndicator>()
                .select("etf_code")
                .apply("trade_time = {0}", dayStart)
                .apply("period IN ('day','week','month')")
                .apply("signal_trend_long = 1")
                .groupBy("etf_code")
                .having("COUNT(DISTINCT period) = 3")
        );
        stats.put("日+周+月趋势共振数", resonance == null ? 0 : resonance.size());

        // 4 周期全共振（等值查询）
        List<Map<String, Object>> resonance4 = taIndicatorMapper.selectMaps(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EtfTaIndicator>()
                .select("etf_code")
                .apply("trade_time = {0}", dayStart)
                .apply("period IN ('day','week','month','season')")
                .apply("signal_trend_long = 1")
                .groupBy("etf_code")
                .having("COUNT(DISTINCT period) = 4")
        );
        stats.put("日+周+月+季趋势共振数", resonance4 == null ? 0 : resonance4.size());

        // 5维共振：同一 etf_code 在 day/week/month/season 4 周期同时 is_sar_bullish=1 AND is_macd_red=1
        // 用等值查询 trade_time = '<latest> 00:00:00'（更直观，与报告"当前交易日"语义对齐）
        List<Map<String, Object>> fiveDimResonance = taIndicatorMapper.selectMaps(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EtfTaIndicator>()
                .select("etf_code, MAX(close_price) AS close")
                .apply("trade_time = {0}", dayStart)
                .apply("period IN ('day','week','month','season')")
                .apply("is_sar_bullish = 1")
                .apply("is_macd_red = 1")
                .groupBy("etf_code")
                .having("COUNT(DISTINCT period) = 4")
                .orderByAsc("etf_code")
        );
        List<String> fiveDimCodes = fiveDimResonance == null
            ? List.of()
            : fiveDimResonance.stream().map(m -> String.valueOf(m.get("etf_code"))).toList();
        stats.put("五维共振标的数", fiveDimCodes.size());
        stats.put("五维共振标的代码列表", fiveDimCodes);

        // 详细共振记录：每只共振 ETF 在 4 周期的 close + sar + macdRed + trendLong
        if (!fiveDimCodes.isEmpty()) {
            LambdaQueryWrapper<EtfTaIndicator> detailQ = new LambdaQueryWrapper<>();
            detailQ.in(EtfTaIndicator::getEtfCode, fiveDimCodes)
                    .eq(EtfTaIndicator::getTradeTime, dayStart)
                    .in(EtfTaIndicator::getPeriod, "day", "week", "month", "season")
                    .orderByAsc(EtfTaIndicator::getEtfCode)
                    .orderByAsc(EtfTaIndicator::getPeriod);
            List<EtfTaIndicator> detail = safeQuery(() -> taIndicatorMapper.selectList(detailQ), "fiveDimResonanceDetail");
            stats.put("五维共振标的明细", compactTaRows(detail));
        }

        return stats;
    }

    @FunctionalInterface
    private interface QuerySupplier<T> {
        List<T> get();
    }
}
