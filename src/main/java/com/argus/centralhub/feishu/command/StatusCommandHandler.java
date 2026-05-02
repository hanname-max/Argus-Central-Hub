package com.argus.centralhub.feishu.command;

import com.argus.centralhub.config.InstanceSyncProperties;
import com.argus.centralhub.domain.model.CloudInstance;
import com.argus.centralhub.feishu.FeishuApiService;
import com.argus.centralhub.feishu.card.InstanceCardBuilder;
import com.argus.centralhub.feishu.dto.MessageContext;
import com.argus.centralhub.service.CloudInstanceService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StatusCommandHandler implements FeishuCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(StatusCommandHandler.class);

    private final CloudInstanceService cloudInstanceService;
    private final FeishuApiService feishuApiService;
    private final InstanceCardBuilder instanceCardBuilder;
    private final InstanceSyncProperties syncProperties;

    public StatusCommandHandler(CloudInstanceService cloudInstanceService,
                                FeishuApiService feishuApiService,
                                InstanceCardBuilder instanceCardBuilder,
                                InstanceSyncProperties syncProperties) {
        this.cloudInstanceService = cloudInstanceService;
        this.feishuApiService = feishuApiService;
        this.instanceCardBuilder = instanceCardBuilder;
        this.syncProperties = syncProperties;
    }

    @Override
    public String getCommand() {
        return "/status";
    }

    @Override
    public String getDescription() {
        return "查询所有云实例状态，返回精美的状态卡片";
    }

    @Override
    @Async
    public void handle(MessageContext context, String[] args) {
        log.info("执行 /status 命令，chatId: {}, userId: {}", 
                context.getChatId(), context.getUserId());
        
        String replyId = context.getMessageId();
        String receiveId = context.getOpenId() != null ? context.getOpenId() : context.getChatId();
        String receiveIdType = context.getOpenId() != null ? "open_id" : "chat_id";
        
        try {
            ObjectNode loadingCard = instanceCardBuilder.buildLoadingCard();
            if (replyId != null && !replyId.isEmpty()) {
                feishuApiService.replyCardMessage(replyId, loadingCard);
            } else {
                feishuApiService.sendCardMessage(receiveIdType, receiveId, loadingCard);
            }
            
            boolean forceRefresh = args != null && args.length > 0 && "-f".equals(args[0]);
            List<CloudInstance> instances = cloudInstanceService.listAllInstances(
                    syncProperties.getDefaultRegion(), forceRefresh);
            
            ObjectNode resultCard;
            if (instances.isEmpty()) {
                resultCard = instanceCardBuilder.buildEmptyCard();
            } else {
                resultCard = instanceCardBuilder.buildInstanceStatusCard(instances);
            }
            
            boolean sent;
            if (replyId != null && !replyId.isEmpty()) {
                sent = feishuApiService.replyCardMessage(replyId, resultCard);
            } else {
                sent = feishuApiService.sendCardMessage(receiveIdType, receiveId, resultCard);
            }
            
            if (!sent) {
                log.warn("发送状态卡片失败");
            }
            
        } catch (Exception e) {
            log.error("执行 /status 命令失败", e);
            
            ObjectNode errorCard = instanceCardBuilder.buildErrorCard(e.getMessage());
            if (replyId != null && !replyId.isEmpty()) {
                feishuApiService.replyCardMessage(replyId, errorCard);
            } else {
                feishuApiService.sendCardMessage(receiveIdType, receiveId, errorCard);
            }
        }
    }
}
