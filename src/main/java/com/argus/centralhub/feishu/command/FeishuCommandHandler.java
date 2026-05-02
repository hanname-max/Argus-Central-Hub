package com.argus.centralhub.feishu.command;

import com.argus.centralhub.feishu.dto.MessageContext;

public interface FeishuCommandHandler {
    
    String getCommand();
    
    String getDescription();
    
    void handle(MessageContext context, String[] args);
}
