package com.demo.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.demo.dto.AiChatRequest;
import com.demo.entity.SysParam;
import com.demo.mapper.SysParamMapper;
import com.demo.service.EtfAiDataService;
import com.demo.vo.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * ETF智能助手对话接口
 *
 * 兼容MiniMax API (MiniMax-M2.7)
 */
@RestController
@RequestMapping("/api/ai")
public class EtfAiChatController {

    private static final Logger log = LoggerFactory.getLogger(EtfAiChatController.class);
    private volatile String lastError = null;

    private static final String MINIMAX_URL =
            "https://api.minimaxi.com/anthropic/v1/chat/completions";
    private static final String MODEL = "MiniMax-M2.7";
    /** 工具调用最大迭代次数，防止无限循环 */
    private static final int MAX_ITERATIONS = 6;

    @Autowired private SysParamMapper   sysParamMapper;
    @Autowired private EtfAiDataService etfAiDataService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @PostMapping("/chat")
    public R<Map<String, Object>> chat(@RequestBody AiChatRequest request) {
        lastError = null;
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return R.error("消息内容不能为空");
        }

        // 从sys_param 动态加载 API Key
        QueryWrapper<SysParam> qw = new QueryWrapper<>();
        qw.eq("param_key", "ai.dashscope.api-key").eq("is_active", 1);
        SysParam param = sysParamMapper.selectOne(qw);
        if (param == null || !org.springframework.util.StringUtils.hasText(param.getParamValue())) {
            return R.error("AI 助手尚未配置 API Key，请先前往系统管理 > 参数设置新增参数：param_key = ai.dashscope.api-key，param_value = 您的密钥 API Key");
        }
        String apiKey = param.getParamValue().trim();

        // 构建初始消息列表
        JSONArray messages = buildMessages(request);
        JSONArray tools    = buildToolDefinitions();

        List<Map<String, Object>> toolsUsed = new ArrayList<>();
        String finalReply = null;

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            JSONObject aiResp = callDashScope(apiKey, messages, tools);
            if (aiResp == null) {
                String msg = lastError != null ? lastError : "调用 AI 服务失败，请检查 API Key 是否正确";
                return R.error(msg);
            }
            // 检查 API 错误
            if (aiResp.containsKey("error")) {
                String errMsg = aiResp.getJSONObject("error").getString("message");
                return R.error("MiniMax API 错误：" + errMsg);
            }

            JSONArray choices = aiResp.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return R.error("AI 服务返回数据异常");
            }

            JSONObject choice      = choices.getJSONObject(0);
            String     finishReason = choice.getString("finish_reason");
            JSONObject assistantMsg = choice.getJSONObject("message");

            if ("tool_calls".equals(finishReason)) {
                // 将assistant 消息（含 tool_calls）加入对话历史
                messages.add(assistantMsg);

                JSONArray toolCalls = assistantMsg.getJSONArray("tool_calls");
                if (toolCalls != null) {
                    for (int j = 0; j < toolCalls.size(); j++) {
                        JSONObject tc       = toolCalls.getJSONObject(j);
                        String     toolId   = tc.getString("id");
                        String     toolName = tc.getJSONObject("function").getString("name");
                        String     argsStr  = tc.getJSONObject("function").getString("arguments");
                        JSONObject args     = org.springframework.util.StringUtils.hasText(argsStr)
                                             ? JSON.parseObject(argsStr) : new JSONObject();

                        String toolResult = executeTool(toolName, args);

                        // 记录工具调用结果
                        Map<String, Object> record = new LinkedHashMap<>();
                        record.put("name", toolName);
                        record.put("args", args);
                        record.put("result", toolResult.length() > 600
                                ? toolResult.substring(0, 600) + "（已截断）" : toolResult);
                        toolsUsed.add(record);

                        // 添加 tool响应消息
                        JSONObject toolMsg = new JSONObject();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", toolId);
                        toolMsg.put("content", toolResult);
                        messages.add(toolMsg);
                    }
                }
            } else {
                // 最终回复
                finalReply = assistantMsg.getString("content");
                break;
            }
        }

        if (finalReply == null) {
            finalReply = "抱歉，当前对话超出处理范围，建议再试一次更具体的提问。";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", finalReply);
        result.put("toolsUsed", toolsUsed);
        return R.ok(result);
    }

    private String executeTool(String name, JSONObject args) {
        try {
            switch (name) {
                case "query_ta_indicator":
                    return etfAiDataService.queryTaIndicator(
                            args.getString("etfCode"),
                            args.getString("period"),
                            args.getString("tradeDate"),
                            args.getInteger("signalTrendLong"),
                            args.getInteger("signalWarning"),
                            args.getInteger("limit"));
                case "query_five_dimension_resonance":
                    return etfAiDataService.queryFiveDimensionResonance(
                            args.getString("etfCode"),
                            args.getInteger("isTodayTriggered"),
                            args.getInteger("isYesterdayTriggered"),
                            args.getInteger("limit"));
                case "query_fund_flow":
                    return etfAiDataService.queryFundFlow(
                            args.getString("etfCode"),
                            args.getString("startDate"),
                            args.getString("endDate"),
                            args.getInteger("limit"));
                case "query_fund_share":
                    return etfAiDataService.queryFundShare(
                            args.getString("etfCode"),
                            args.getString("startDate"),
                            args.getString("endDate"),
                            args.getInteger("limit"));
                default:
                    return "链式调用: " + name;
            }
        } catch (Exception e) {
            return "执行调用异常: " + e.getMessage();
        }
    }

    private JSONObject callDashScope(String apiKey, JSONArray messages, JSONArray tools) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("messages", messages);
            body.put("tools", tools);
            body.put("stream", false);

            log.info("MiniMax API request body: {}", body.toJSONString());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MINIMAX_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            String respBody = resp.body();
            log.info("MiniMax API response status: {}, body: {}", resp.statusCode(), respBody);
            if (resp.statusCode() != 200) {
                lastError = "MiniMax API 返回错误(HTTP " + resp.statusCode() + "): " + respBody;
                log.error("MiniMax API error: {}", respBody);
            }
            return JSON.parseObject(respBody);
        } catch (Exception e) {
            lastError = "MiniMax API 调用异常: " + e.getMessage();
            log.error("调用 MiniMax API 异常: {}", e.getMessage(), e);
            return null;
        }
    }

    private JSONArray buildMessages(AiChatRequest request) {
        JSONArray msgs = new JSONArray();

        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT);
        msgs.add(sys);

        if (request.getHistory() != null) {
            for (Map<String, Object> h : request.getHistory()) {
                msgs.add(h);
            }
        }

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", request.getMessage().trim());
        msgs.add(user);

        return msgs;
    }

    // 工具定义 - 尝试OpenAI风格的functions格式
    private static final JSONArray TOOL_DEFINITIONS = JSON.parseArray(
        "[\n" +
        "  {\"name\":\"query_ta_indicator\",\"description\":\"查询ETF技术分析指标，包含MACD、SAR、趋势信号、风险预警等\",\"parameters\":{\"type\":\"object\",\"properties\":{\"etfCode\":{\"type\":\"string\",\"description\":\"ETF代码\"},\"period\":{\"type\":\"string\",\"description\":\"周期：day/week/month等\"},\"tradeDate\":{\"type\":\"string\",\"description\":\"交易日期 yyyy-MM-dd\"},\"signalTrendLong\":{\"type\":\"integer\",\"description\":\"综合趋势多头信号筛选：1=是 0=否\"},\"signalWarning\":{\"type\":\"integer\",\"description\":\"风险预警筛选：1=有风险 0=无风险\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回条数，默认20，最大100\"}}}},\n" +
        "  {\"name\":\"query_five_dimension_resonance\",\"description\":\"查询ETF五维共振信号，综合K线均线+MACD+SAR+RSI+KDJ等五大维度的共振触发信号\",\"parameters\":{\"type\":\"object\",\"properties\":{\"etfCode\":{\"type\":\"string\",\"description\":\"ETF代码\"},\"isTodayTriggered\":{\"type\":\"integer\",\"description\":\"最新K线日是否触发五维共振：1=是 0=否\"},\"isYesterdayTriggered\":{\"type\":\"integer\",\"description\":\"昨日是否触发五维共振：1=是 0=否\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回条数，默认20，最大100\"}}}},\n" +
        "  {\"name\":\"query_fund_flow\",\"description\":\"查询ETF资金流向汇总，包含资金净流入/流出、累计流向金额等\",\"parameters\":{\"type\":\"object\",\"properties\":{\"etfCode\":{\"type\":\"string\",\"description\":\"ETF代码\"},\"startDate\":{\"type\":\"string\",\"description\":\"开始日期 yyyy-MM-dd\"},\"endDate\":{\"type\":\"string\",\"description\":\"结束日期 yyyy-MM-dd\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回条数，默认20，最大100\"}}}},\n" +
        "  {\"name\":\"query_fund_share\",\"description\":\"查询ETF基金份额变动，包含总份额、流通份额、变动原因等\",\"parameters\":{\"type\":\"object\",\"properties\":{\"etfCode\":{\"type\":\"string\",\"description\":\"ETF代码\"},\"startDate\":{\"type\":\"string\",\"description\":\"开始日期 yyyy-MM-dd\"},\"endDate\":{\"type\":\"string\",\"description\":\"结束日期 yyyy-MM-dd\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回条数，默认20，最大100\"}}}}"
        +
        "]"
    );

    private JSONArray buildToolDefinitions() {
        return TOOL_DEFINITIONS;
    }

    private static final String SYSTEM_PROMPT =
        "你是一个专业的ETF分析助手，只能使用以下四个表作为信息来源：\n" +
        "1. ETF基金份额表\n" +
        "2. ETF技术指标表\n" +
        "3. ETF资金流向统计表\n" +
        "4. ETF五维共振分析结果表\n" +
        "严格禁止使用其它表格或额外数据来源。\n" +
        "当生成ETF智能助手分析或最新交易日报告时，禁止给出'大盘涨跌预测'或任何宏观市场涨跌结论。\n" +
        "你只能使用下列工具函数查询数据，并且必须通过工具调用来获取信息：\n" +
        "- query_fund_share\n" +
        "- query_ta_indicator\n" +
        "- query_fund_flow\n" +
        "- query_five_dimension_resonance\n" +
        "如果问题无法从这四个表中直接回答，请说明可用数据不足，不要编造答案。\n" +
        "所有返回内容都应当基于实际查询结果，不要凭空推测。\n";

}