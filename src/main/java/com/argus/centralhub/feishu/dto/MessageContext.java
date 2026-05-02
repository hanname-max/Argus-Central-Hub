package com.argus.centralhub.feishu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageContext {
    
    private String chatId;
    
    private String openId;
    
    private String userId;
    
    private String messageType;
    
    private String content;
    
    private String messageId;
    
    private boolean isGroupChat;
}
