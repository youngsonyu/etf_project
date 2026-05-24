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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EtfFiveDimensionReportGenerationService {

    private static final String MODEL = "qwen-max";
    private static final String DASHSCOPE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final int ROW_LIMIT = 8;
    private static final int SYSTEM_PROMPT_MAX_CHARS = 300;
    private static final int USER_CONTENT_MAX_CHARS = 6000;
    private static final int REPORT_MAX_CHARS = 2000;
            private static final String[] REPORT_TABLES = new String[] {
                "ETF基金份额表",
                "ETF技术指标表",
                "ETF资金流向统计表",
                "ETF五维共振分析结果表"
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
        normalizedMarkdown = removeLeadingTitle(normalizedMarkdown);
        normalizedMarkdown = stripFiveDimensionDefinition(normalizedMarkdown);
        normalizedMarkdown = appendDataSourceSummary(normalizedMarkdown, latestTradeDate);
        normalizedMarkdown = trimToMaxChars(normalizedMarkdown, REPORT_MAX_CHARS);
        normalizedMarkdown = enforceKeyConclusionsBold(normalizedMarkdown);
        String title = "ETF市场AI简报（" + latestTradeDate + "）";

        EtfFiveDimensionReport entity = new EtfFiveDimensionReport();
        entity.setTitle(title);
        entity.setContentMd(normalizedMarkdown);
        entity.setPublisher(StringUtils.hasText(publisher) ? publisher.trim() : "AI智能体");
        entity.setPublishTime(LocalDateTime.now());
        entity.setIsActive(1);
        reportService.save(entity);
        return entity;
    }

    private LocalDate resolveLatestTradeDate() {
        LocalDate fromProgress = parseFromYmdInt(etlProgressService.getCurrentTradeDate());
        if (fromProgress != null) {
            return fromProgress;
        }

        LocalDate fromResonance = parseToLocalDate(selectMaxDateFromResonance());
        if (fromResonance != null) {
            return fromResonance;
        }
        LocalDate fromFlow = parseToLocalDate(selectMaxDateFromFundFlow());
        if (fromFlow != null) {
            return fromFlow;
        }
        return parseToLocalDate(selectMaxDateFromTradeCalendar());
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

        List<EtfFundFlowSummary> fundFlowRows = safeQuery(() -> {
            QueryWrapper<EtfFundFlowSummary> w = new QueryWrapper<>();
            w.eq("trade_date", latestTradeDate)
                    .orderByDesc("fund_flow")
                    .last("LIMIT " + ROW_LIMIT);
            return fundFlowSummaryMapper.selectList(w);
        });

        List<EtfFundShare> fundShareRows = safeQuery(() -> {
            QueryWrapper<EtfFundShare> w = new QueryWrapper<>();
            w.eq("change_date", latestTradeDate)
                    .orderByDesc("fund_share")
                    .last("LIMIT " + ROW_LIMIT);
            return fundShareMapper.selectList(w);
        });

        List<EtfTaIndicator> taRows = safeQuery(() -> {
            QueryWrapper<EtfTaIndicator> w = new QueryWrapper<>();
            w.eq("period", "day")
                    .apply("DATE(trade_time) = {0}", latestTradeDate)
                    .orderByDesc("signal_trend_long")
                    .orderByDesc("signal_momentum_long")
                    .last("LIMIT " + ROW_LIMIT);
            return taIndicatorMapper.selectList(w);
        });

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resonanceRowCount", resonanceRows.size());
        summary.put("todayTriggeredCount", todayTriggered);
        summary.put("warningCount", warningCount);
        summary.put("fundFlowRowCount", fundFlowRows.size());
        summary.put("taRowCount", taRows.size());

        context.put("summary", summary);
        context.put("resonanceRows", compactResonanceRows(resonanceRows));
        context.put("fundFlowRows", compactFundFlowRows(fundFlowRows));
        context.put("fundShareRows", compactFundShareRows(fundShareRows));
        context.put("taRows", compactTaRows(taRows));
        return context;
    }

    private String callAiForReport(String apiKey,
                                   LocalDate latestTradeDate,
                                   JSONObject context) {
        JSONObject payload = new JSONObject();
        payload.put("latestTradeDate", latestTradeDate.toString());
        payload.put("dataset", context);

        String systemPrompt = trimToMaxChars(
            "你是一名A股ETF策略师。先联网搜索近24-72小时国际国内重大事件，再基于用户提供的ETF数据，生成一份策略报告。"
                + "报告字数不超过2000字。"
                + "报告必须包含："
                + "1) 最新交易日一句话总结；"
                + "2) 至少3个板块的明确方向预测；"
                + "报告不得输出大盘涨跌预测内容；"
                + "硬约束：严禁臆造新闻政策；无消息支撑时标注“资金博弈”；关键结论加粗。"
                + "消息面要求：只写真实可验证来源；可附上对应网址；如果近24-72小时没有重大事件，可不写消息面段落。"
                + "内部分析规则（不要在报告正文展示）：五维共振定义=日/周/月/季/45日线同时MACD金叉+SAR转多头，且共振≠追高。"
                + "报告结尾必须新增“数据来源说明”，写明本次按最新交易日期锁定分析，并列出参考的数据表。",
            SYSTEM_PROMPT_MAX_CHARS
        );
        String userDataJson = JSON.toJSONString(payload);

        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        messages.add(sys);

        String userContent = "请按系统要求输出策略报告，字数≤2000字。不要输出标题行（不要使用#开头），直接从正文开始。"
            + "消息面仅限真实可验证来源，可带网址；没有大事可不写消息面。若缺少可验证事件，请明确标注“资金博弈”。"
            + "\n\n数据如下(JSON)：\n" + userDataJson;
        userContent = trimToMaxChars(userContent, USER_CONTENT_MAX_CHARS);

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", userContent);
        messages.add(user);

        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("stream", false);
        body.put("temperature", 0.7);
        body.put("max_tokens", 1200);
        body.put("messages", messages);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DASHSCOPE_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(180))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
            m.put("code", r.getEtfCode());
            m.put("name", r.getEtfName());
            m.put("tradeDate", r.getTradeDate());
            m.put("fundFlow", r.getFundFlow());
            m.put("cumulativeFlow", r.getCumulativeFlow());
            m.put("shareChange", r.getShareChange());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> compactFundShareRows(List<EtfFundShare> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EtfFundShare r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r.getEtfCode());
            m.put("changeDate", r.getChangeDate());
            m.put("fundShare", r.getFundShare());
            m.put("floatShare", r.getFloatShare());
            m.put("changeReason", r.getChangeReason());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> compactTaRows(List<EtfTaIndicator> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EtfTaIndicator r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r.getEtfCode());
            m.put("tradeTime", r.getTradeTime());
            m.put("period", r.getPeriod());
            m.put("macd", r.getMacd());
            m.put("sar", r.getSarValue());
            m.put("trendLong", r.getSignalTrendLong());
            m.put("momentumLong", r.getSignalMomentumLong());
            m.put("warning", r.getSignalWarning());
            out.add(m);
        }
        return out;
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
        if (start < lines.length && lines[start].trim().startsWith("#")) {
            start++;
            while (start < lines.length && !StringUtils.hasText(lines[start])) {
                start++;
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
        try {
            return supplier.get();
        } catch (Exception ex) {
            return List.of();
        }
    }

    @FunctionalInterface
    private interface QuerySupplier<T> {
        List<T> get();
    }
}
