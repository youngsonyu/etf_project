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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * ETF 鏅鸿兘鍔╂墜瀵硅瘽鎺ュ彛銆?
 *
 * 娉細褰撳墠椤圭洰浣跨敤 Spring Boot 2.7.18 + Java 11锛孲pring AI Alibaba 1.x 闇€瑕?Spring Boot 3.x锛?
 * 鍥犳鐩存帴璋冪敤 DashScope OpenAI 鍏煎鎺ュ彛锛坔ttps://dashscope.aliyuncs.com/compatible-mode/v1锛夛紝
 * 涓?Spring AI Alibaba 搴曞眰绛変环锛屾敮鎸侀€氫箟鍗冮棶 Function Calling銆?
 *
 * API Key 閫氳繃 sys_param 琛ㄧ殑 key = "ai.dashscope.api-key" 鍔ㄦ€佸姞杞斤紝鏃犻渶閲嶅惎鏈嶅姟銆?
 */
@RestController
@RequestMapping("/api/ai")
public class EtfAiChatController {

    private static final String DASHSCOPE_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String MODEL = "qwen-max";
    /** 宸ュ叿璋冪敤鏈€澶ц疆娆★紝闃叉鏃犻檺寰幆 */
    private static final int MAX_ITERATIONS = 6;

    @Autowired private SysParamMapper   sysParamMapper;
    @Autowired private EtfAiDataService etfAiDataService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    // 瀵硅瘽鎺ュ彛
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @PostMapping("/chat")
    public R<Map<String, Object>> chat(@RequestBody AiChatRequest request) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return R.error("娑堟伅鍐呭涓嶈兘涓虹┖");
        }

        // 浠?sys_param 鍔ㄦ€佸姞杞?API Key
        QueryWrapper<SysParam> qw = new QueryWrapper<>();
        qw.eq("param_key", "ai.dashscope.api-key").eq("is_active", 1);
        SysParam param = sysParamMapper.selectOne(qw);
        if (param == null || !org.springframework.util.StringUtils.hasText(param.getParamValue())) {
            return R.error("AI 鍔╂墜灏氭湭閰嶇疆 API Key锛岃鍓嶅線銆愮郴缁熺鐞?鈫?鍙傛暟閰嶇疆銆戞柊澧炲弬鏁帮細param_key = ai.dashscope.api-key锛宲aram_value = 鎮ㄧ殑鐧剧偧 API Key");
        }
        String apiKey = param.getParamValue().trim();

        // 鏋勫缓鍒濆娑堟伅鍒楄〃
        JSONArray messages = buildMessages(request);
        JSONArray tools    = buildToolDefinitions();

        List<Map<String, Object>> toolsUsed = new ArrayList<>();
        String finalReply = null;

        // 鈹€鈹€ Agent 寰幆 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            JSONObject aiResp = callDashScope(apiKey, messages, tools);
            if (aiResp == null) {
                return R.error("调用 AI 服务失败，请检查 API Key 是否正确");
            }
            // 检查 API 错误
            if (aiResp.containsKey("error")) {
                String errMsg = aiResp.getJSONObject("error").getString("message");
                return R.error("DashScope 错误：" + errMsg);
            }

            JSONArray choices = aiResp.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return R.error("AI 鏈嶅姟杩斿洖鏁版嵁寮傚父");
            }

            JSONObject choice      = choices.getJSONObject(0);
            String     finishReason = choice.getString("finish_reason");
            JSONObject assistantMsg = choice.getJSONObject("message");

            if ("tool_calls".equals(finishReason)) {
                // 灏?assistant 娑堟伅锛堝惈 tool_calls锛夊姞鍏ュ璇濆巻鍙?
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

                        // 璁板綍渚涘墠绔睍绀?
                        Map<String, Object> record = new LinkedHashMap<>();
                        record.put("name", toolName);
                        record.put("args", args);
                        record.put("result", toolResult.length() > 600
                                ? toolResult.substring(0, 600) + "鈥︼紙宸叉埅鏂級" : toolResult);
                        toolsUsed.add(record);

                        // 娣诲姞 tool 鍝嶅簲娑堟伅
                        JSONObject toolMsg = new JSONObject();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", toolId);
                        toolMsg.put("content", toolResult);
                        messages.add(toolMsg);
                    }
                }
            } else {
                // 鏈€缁堝洖绛?
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

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    // 宸ュ叿鍒嗗彂鎵ц
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    // DashScope HTTP 璋冪敤
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private JSONObject callDashScope(String apiKey, JSONArray messages, JSONArray tools) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("messages", messages);
            body.put("tools", tools);
            body.put("stream", false);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(DASHSCOPE_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return JSON.parseObject(resp.body());
        } catch (Exception e) {
            return null;
        }
    }

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    // 鏋勫缓瀵硅瘽娑堟伅鍒楄〃
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    // 宸ュ叿瀹氫箟锛團unction Calling Schema锛?
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private static final JSONArray TOOL_DEFINITIONS = JSON.parseArray(
        "[\n" +
        "  {\"type\":\"function\",\"function\":{\"name\":\"query_ta_indicator\",\"description\":\"查询ETF技术分析指标，包含MACD、SAR、趋势信号、风险预警等\",\"parameters\":{\"type\":\"object\",\"properties\":{\"etfCode\":{\"type\":\"string\",\"description\":\"ETF代码\"},\"period\":{\"type\":\"string\",\"description\":\"周期：day/week/month等\"},\"tradeDate\":{\"type\":\"string\",\"description\":\"交易日期 yyyy-MM-dd\"},\"signalTrendLong\":{\"type\":\"integer\",\"description\":\"综合趋势多头信号筛选：1=是 0=否\"},\"signalWarning\":{\"type\":\"integer\",\"description\":\"风险预警筛选：1=有风险 0=无风险\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回条数，默认20，最大100\"}}}}}," +
        "  {\"type\":\"function\",\"function\":{\"name\":\"query_five_dimension_resonance\",\"description\":\"查询ETF五维共振信号，综合K线均线 + MACD + SAR + RSI + KDJ 等五大维度的共振触发信号\",\"parameters\":{\"type\":\"object\",\"properties\":{\"etfCode\":{\"type\":\"string\",\"description\":\"ETF代码\"},\"isTodayTriggered\":{\"type\":\"integer\",\"description\":\"最新K线日是否触发五维共振：1=是 0=否\"},\"isYesterdayTriggered\":{\"type\":\"integer\",\"description\":\"昨日是否触发五维共振：1=是 0=否\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回条数，默认20，最大100\"}}}}}," +
        "  {\"type\":\"function\",\"function\":{\"name\":\"query_fund_flow\",\"description\":\"查询ETF资金流向汇总，包含资金净流入/流出、累计流向金额等\",\"parameters\":{\"type\":\"object\",\"properties\":{\"etfCode\":{\"type\":\"string\",\"description\":\"ETF代码\"},\"startDate\":{\"type\":\"string\",\"description\":\"开始日期 yyyy-MM-dd\"},\"endDate\":{\"type\":\"string\",\"description\":\"结束日期 yyyy-MM-dd\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回条数，默认20，最大100\"}}}}}," +
        "  {\"type\":\"function\",\"function\":{\"name\":\"query_fund_share\",\"description\":\"查询ETF基金份额变动，包含总份额、流通份额、变动原因等\",\"parameters\":{\"type\":\"object\",\"properties\":{\"etfCode\":{\"type\":\"string\",\"description\":\"ETF代码\"},\"startDate\":{\"type\":\"string\",\"description\":\"开始日期 yyyy-MM-dd\"},\"endDate\":{\"type\":\"string\",\"description\":\"结束日期 yyyy-MM-dd\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回条数，默认20，最大100\"}}}}}"
        +
        "]"
    );

    private JSONArray buildToolDefinitions() {
        return TOOL_DEFINITIONS;
    }

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    // 绯荤粺鎻愮ず璇?
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private static final String SYSTEM_PROMPT =
        "你是一个专业的ETF分析助手，只能使用以下四个表作为信息来源：\n" +
        "1. ETF基金份额表\n" +
        "2. ETF技术指标表\n" +
        "3. ETF资金流向统计表\n" +
        "4. ETF五维共振分析结果表\n" +
        "严格禁止使用其它表格或额外数据来源。\n" +
        "当生成ETF智能助手分析或最新交易日报告时，禁止给出“大盘涨跌预测”或任何宏观市场涨跌结论。\n" +
        "你只能使用下列工具函数查询数据，并且必须通过工具调用来获取信息：\n" +
        "- query_fund_share\n" +
        "- query_ta_indicator\n" +
        "- query_fund_flow\n" +
        "- query_five_dimension_resonance\n" +
        "如果问题无法从这四个表中直接回答，请说明可用数据不足，不要编造答案。\n" +
        "所有返回内容都应当基于实际查询结果，不要凭空推测。\n";

}
