package com.argus.centralhub.feishu.command;

import com.argus.centralhub.feishu.FeishuApiService;
import com.argus.centralhub.feishu.dto.MessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FeishuCommandRouter {

    private static final Logger log = LoggerFactory.getLogger(FeishuCommandRouter.class);

    private final List<FeishuCommandHandler> commandHandlers;
    private final FeishuApiService feishuApiService;
    private final ObjectMapper objectMapper;

    private final Map<String, FeishuCommandHandler> handlerMap = new HashMap<>();

    public FeishuCommandRouter(List<FeishuCommandHandler> commandHandlers,
                                FeishuApiService feishuApiService,
                                ObjectMapper objectMapper) {
        this.commandHandlers = commandHandlers;
        this.feishuApiService = feishuApiService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        for (FeishuCommandHandler handler : commandHandlers) {
            handlerMap.put(handler.getCommand().toLowerCase(), handler);
            log.info("已注册命令处理器: {} - {}", handler.getCommand(), handler.getDescription());
        }
    }

    public void routeMessage(MessageContext context) {
        String content = context.getContent();
        if (content == null || content.isEmpty()) {
            log.debug("消息内容为空，跳过处理");
            return;
        }

        String parsedContent = parseMessageContent(content, context.getMessageType());
        if (parsedContent == null || parsedContent.isEmpty()) {
            log.debug("解析后的消息内容为空，跳过处理");
            return;
        }

        String trimmedContent = parsedContent.trim();
        
        if (!trimmedContent.startsWith("/")) {
            log.debug("消息不以 / 开头，不是命令: {}", trimmedContent);
            return;
        }

        String[] parts = trimmedContent.split("\\s+");
        String command = parts[0].toLowerCase();
        
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        FeishuCommandHandler handler = handlerMap.get(command);
        if (handler != null) {
            log.info("路由命令: {} 到处理器: {}", command, handler.getClass().getSimpleName());
            handler.handle(context, args);
        } else {
            log.info("未知命令: {}", command);
            sendUnknownCommandResponse(context, command);
        }
    }

    private String parseMessageContent(String content, String messageType) {
        try {
            if ("text".equals(messageType)) {
                JsonNode contentJson = objectMapper.readTree(content);
                if (contentJson.has("text")) {
                    return contentJson.get("text").asText();
                }
                return content;
            }
            return content;
        } catch (Exception e) {
            log.warn("解析消息内容失败: {}", content, e);
            return content;
        }
    }

    private void sendUnknownCommandResponse(MessageContext context, String command) {
        String message = "❓ 未知命令: `" + command + "`\n\n" +
                "请使用 `/help` 查看可用命令列表。";
        
        String receiveId = context.getOpenId() != null ? context.getOpenId() : context.getChatId();
        String receiveIdType = context.getOpenId() != null ? "open_id" : "chat_id";
        String replyId = context.getMessageId();

        boolean sent;
        if (replyId != null && !replyId.isEmpty()) {
            sent = feishuApiService.replyTextMessage(replyId, message);
        } else {
            sent = feishuApiService.sendTextMessage(receiveIdType, receiveId, message);
        }

        if (!sent) {
            log.warn("发送未知命令响应失败");
        }
    }
}
