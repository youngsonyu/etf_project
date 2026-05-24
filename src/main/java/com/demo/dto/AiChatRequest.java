package com.demo.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiChatRequest {
    /** 本轮用户消息 */
    private String message;
    /** 历史对话消息列表，每条格式 {role: "user"/"assistant", content: "..."} */
    private List<Map<String, Object>> history;
}
