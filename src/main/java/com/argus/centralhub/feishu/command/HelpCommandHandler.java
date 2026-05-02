package com.argus.centralhub.feishu.command;

import com.argus.centralhub.feishu.FeishuApiService;
import com.argus.centralhub.feishu.dto.MessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HelpCommandHandler implements FeishuCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(HelpCommandHandler.class);

    private final FeishuApiService feishuApiService;
    private final List<FeishuCommandHandler> commandHandlers;

    public HelpCommandHandler(FeishuApiService feishuApiService,
                              List<FeishuCommandHandler> commandHandlers) {
        this.feishuApiService = feishuApiService;
        this.commandHandlers = commandHandlers;
    }

    @Override
    public String getCommand() {
        return "/help";
    }

    @Override
    public String getDescription() {
        return "显示帮助信息，列出所有可用命令";
    }

    @Override
    public void handle(MessageContext context, String[] args) {
        log.info("执行 /help 命令，chatId: {}, userId: {}", 
                context.getChatId(), context.getUserId());
        
        StringBuilder helpText = new StringBuilder();
        helpText.append("📋 **Argus Central Hub 帮助**\n\n");
        helpText.append("可用命令:\n\n");
        
        for (FeishuCommandHandler handler : commandHandlers) {
            helpText.append("• `")
                    .append(handler.getCommand())
                    .append("` - ")
                    .append(handler.getDescription())
                    .append("\n");
        }
        
        helpText.append("\n");
        helpText.append("💡 **使用说明**:\n");
        helpText.append("• 在私聊中直接发送命令即可\n");
        helpText.append("• 在群聊中需要 @机器人 后发送命令\n");
        helpText.append("• 发送 `/status -f` 可强制刷新缓存\n");
        
        String receiveId = context.getOpenId() != null ? context.getOpenId() : context.getChatId();
        String receiveIdType = context.getOpenId() != null ? "open_id" : "chat_id";
        String replyId = context.getMessageId();
        
        boolean sent;
        if (replyId != null && !replyId.isEmpty()) {
            sent = feishuApiService.replyTextMessage(replyId, helpText.toString());
        } else {
            sent = feishuApiService.sendTextMessage(receiveIdType, receiveId, helpText.toString());
        }
        
        if (!sent) {
            log.warn("发送帮助消息失败");
        }
    }
}
