package com.argus.centralhub.listener;

import com.argus.centralhub.config.FeishuBotProperties;
import com.argus.centralhub.event.AlertType;
import com.argus.centralhub.event.ServerCriticalAlertEvent;
import com.argus.centralhub.feishu.FeishuApiService;
import com.argus.centralhub.feishu.card.AlertCardBuilder;
import com.argus.centralhub.llm.LlmService;
import com.argus.centralhub.mail.MailService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

@Component
public class ServerCriticalAlertListener {

    private static final Logger log = LoggerFactory.getLogger(ServerCriticalAlertListener.class);
    
    private static final String SARCASTIC_SYSTEM_PROMPT = 
            "你是一个极度毒舌的资深运维，请用阴阳怪气的中文吐槽以下服务器报错。" +
            "要求：1. 语言风格要犀利、幽默、带点讽刺；2. 要体现出对这个错误的不屑和嘲讽；" +
            "3. 可以适当使用网络流行语；4. 分两部分输出：第一部分是简短的吐槽（100字以内），" +
            "第二部分是详细的吐槽分析（300字以内）。格式如下：\n" +
            "【简短吐槽】\n...\n\n【详细吐槽】\n...";
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LlmService llmService;
    private final FeishuApiService feishuApiService;
    private final AlertCardBuilder alertCardBuilder;
    private final MailService mailService;
    private final FeishuBotProperties feishuBotProperties;

    public ServerCriticalAlertListener(LlmService llmService,
                                        FeishuApiService feishuApiService,
                                        AlertCardBuilder alertCardBuilder,
                                        MailService mailService,
                                        FeishuBotProperties feishuBotProperties) {
        this.llmService = llmService;
        this.feishuApiService = feishuApiService;
        this.alertCardBuilder = alertCardBuilder;
        this.mailService = mailService;
        this.feishuBotProperties = feishuBotProperties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleServerCriticalAlert(ServerCriticalAlertEvent event) {
        log.info("收到严重告警事件: 实例ID={}, 告警类型={}, 时间={}",
                event.instanceId(),
                event.alertType().getLabel(),
                event.occurredAt());
        
        if (!event.alertType().isCritical()) {
            log.info("告警类型非严重级别，跳过处理: {}", event.alertType());
            return;
        }

        String llmResponse = null;
        String llmShortComment = null;
        String llmFullComment = null;
        
        try {
            String logContent = buildLogContent(event);
            llmResponse = llmService.analyzeLog(SARCASTIC_SYSTEM_PROMPT, logContent);
            
            String[] parts = parseLlmResponse(llmResponse);
            llmShortComment = parts[0];
            llmFullComment = parts[1];
            
            log.info("LLM 吐槽生成成功，简短版: {}...", 
                    llmShortComment != null ? llmShortComment.substring(0, Math.min(30, llmShortComment.length())) : "N/A");
                    
        } catch (Exception e) {
            log.error("调用 LLM 服务失败", e);
            llmShortComment = "AI 吐槽服务暂时罢工了，你自己看着办吧 😤";
            llmFullComment = "由于 LLM 服务异常，无法生成详细吐槽。错误信息: " + e.getMessage();
        }

        sendFeishuAlert(event, llmShortComment);

        sendEmailReport(event, llmShortComment, llmFullComment, llmResponse);
    }

    private String buildLogContent(ServerCriticalAlertEvent event) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=").append("=".repeat(50)).append("\n");
        sb.append("【服务器严重告警日志】\n");
        sb.append("=").append("=".repeat(50)).append("\n\n");
        
        sb.append("📌 基本信息:\n");
        sb.append("   实例ID: ").append(event.instanceId()).append("\n");
        if (event.instanceName() != null && !event.instanceName().isEmpty()) {
            sb.append("   实例名称: ").append(event.instanceName()).append("\n");
        }
        sb.append("   云厂商: ").append(event.cloudProvider().getLabel()).append("\n");
        sb.append("   区域: ").append(event.region()).append("\n");
        sb.append("   当前状态: ").append(event.instanceStatus().getLabel()).append("\n");
        sb.append("   告警类型: ").append(event.alertType().getLabel()).append("\n");
        sb.append("   发生时间: ").append(event.occurredAt().format(FORMATTER)).append("\n\n");
        
        if (event.errorMessage() != null && !event.errorMessage().isBlank()) {
            sb.append("❌ 错误信息:\n");
            sb.append("   ").append(event.errorMessage()).append("\n\n");
        }
        
        if (event.stackTrace() != null && !event.stackTrace().isBlank()) {
            sb.append("📋 堆栈追踪:\n");
            sb.append(event.stackTrace()).append("\n\n");
        }
        
        if (event.extraInfo() != null && !event.extraInfo().isBlank()) {
            sb.append("📎 附加信息:\n");
            sb.append(event.extraInfo()).append("\n");
        }
        
        sb.append("\n").append("=").append("=".repeat(50)).append("\n");
        
        return sb.toString();
    }

    private String[] parseLlmResponse(String response) {
        if (response == null || response.isBlank()) {
            return new String[]{"AI 吐槽服务暂时不可用", "无法获取详细吐槽"};
        }
        
        String shortPart = "";
        String fullPart = "";
        
        String shortMarker = "【简短吐槽】";
        String fullMarker = "【详细吐槽】";
        
        int shortIndex = response.indexOf(shortMarker);
        int fullIndex = response.indexOf(fullMarker);
        
        if (shortIndex >= 0 && fullIndex > shortIndex) {
            shortPart = response.substring(shortIndex + shortMarker.length(), fullIndex).trim();
            fullPart = response.substring(fullIndex + fullMarker.length()).trim();
        } else if (shortIndex >= 0) {
            shortPart = response.substring(shortIndex + shortMarker.length()).trim();
            fullPart = shortPart;
        } else {
            shortPart = response.length() > 100 ? response.substring(0, 100) + "..." : response;
            fullPart = response;
        }
        
        shortPart = shortPart.replaceAll("^\\s*\\n+", "").replaceAll("\\n+\\s*$", "");
        fullPart = fullPart.replaceAll("^\\s*\\n+", "").replaceAll("\\n+\\s*$", "");
        
        return new String[]{shortPart, fullPart};
    }

    private void sendFeishuAlert(ServerCriticalAlertEvent event, String llmShortComment) {
        try {
            String receiverOpenId = feishuBotProperties.getAlertReceiverOpenId();
            String receiverChatId = feishuBotProperties.getAlertReceiverChatId();
            
            if (receiverOpenId == null && receiverChatId == null) {
                log.warn("飞书告警接收者未配置，跳过飞书消息发送");
                return;
            }
            
            ObjectNode alertCard = alertCardBuilder.buildAlertCard(event, llmShortComment);
            
            if (receiverOpenId != null && !receiverOpenId.isBlank()) {
                boolean sent = feishuApiService.sendCardMessage("open_id", receiverOpenId, alertCard);
                if (sent) {
                    log.info("飞书告警卡片已发送到用户: {}", receiverOpenId);
                } else {
                    log.warn("飞书告警卡片发送失败");
                }
            }
            
            if (receiverChatId != null && !receiverChatId.isBlank()) {
                boolean sent = feishuApiService.sendCardMessage("chat_id", receiverChatId, alertCard);
                if (sent) {
                    log.info("飞书告警卡片已发送到群组: {}", receiverChatId);
                } else {
                    log.warn("飞书告警卡片发送到群组失败");
                }
            }
            
        } catch (Exception e) {
            log.error("发送飞书告警失败", e);
        }
    }

    private void sendEmailReport(ServerCriticalAlertEvent event, 
                                  String llmShortComment, 
                                  String llmFullComment,
                                  String rawLlmResponse) {
        try {
            String subject = buildEmailSubject(event);
            String htmlContent = buildHtmlEmailContent(event, llmShortComment, llmFullComment, rawLlmResponse);
            
            mailService.sendHtmlMail(subject, htmlContent);
            log.info("告警审计报告邮件已发送");
            
        } catch (Exception e) {
            log.error("发送告警邮件失败", e);
        }
    }

    private String buildEmailSubject(ServerCriticalAlertEvent event) {
        String alertTypeStr = event.alertType() == AlertType.SERVER_DOWN ? "服务器宕机" : "严重异常";
        return String.format("[紧急告警] %s - 实例: %s (%s)", 
                alertTypeStr, 
                event.instanceName() != null ? event.instanceName() : event.instanceId(),
                event.occurredAt().format(FORMATTER));
    }

    private String buildHtmlEmailContent(ServerCriticalAlertEvent event,
                                          String llmShortComment,
                                          String llmFullComment,
                                          String rawLlmResponse) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>");
        html.append("<html lang=\"zh-CN\">");
        html.append("<head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        html.append("<title>服务器告警审计报告</title>");
        html.append("<style>");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }");
        html.append(".container { max-width: 800px; margin: 0 auto; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); overflow: hidden; }");
        html.append(".header { background: linear-gradient(135deg, #ff4444 0%, #cc0000 100%); color: white; padding: 30px; text-align: center; }");
        html.append(".header h1 { margin: 0; font-size: 24px; }");
        html.append(".header .subtitle { margin-top: 10px; opacity: 0.9; font-size: 14px; }");
        html.append(".section { padding: 25px 30px; border-bottom: 1px solid #eee; }");
        html.append(".section:last-child { border-bottom: none; }");
        html.append(".section-title { font-size: 18px; font-weight: bold; color: #333; margin-bottom: 15px; padding-bottom: 10px; border-bottom: 2px solid #ff6b6b; }");
        html.append(".info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }");
        html.append(".info-item { background: #f9f9f9; padding: 12px 15px; border-radius: 6px; }");
        html.append(".info-label { font-size: 12px; color: #666; margin-bottom: 5px; }");
        html.append(".info-value { font-size: 14px; color: #333; font-weight: 500; }");
        html.append(".llm-box { background: linear-gradient(135deg, #fff5e6 0%, #ffe6cc 100%); border-left: 4px solid #ff9500; padding: 20px; border-radius: 0 6px 6px 0; margin-top: 10px; }");
        html.append(".llm-title { font-weight: bold; color: #d68910; margin-bottom: 10px; }");
        html.append(".llm-content { color: #8b4513; line-height: 1.6; white-space: pre-wrap; }");
        html.append(".error-box { background: #fff0f0; border: 1px solid #ffcccc; border-radius: 6px; padding: 15px; margin-top: 10px; }");
        html.append(".error-title { font-weight: bold; color: #cc0000; margin-bottom: 10px; }");
        html.append(".error-content { color: #660000; font-family: 'Courier New', monospace; font-size: 13px; line-height: 1.5; white-space: pre-wrap; word-break: break-all; }");
        html.append(".stack-trace { background: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 6px; font-family: 'Courier New', monospace; font-size: 12px; line-height: 1.4; overflow-x: auto; max-height: 400px; overflow-y: auto; }");
        html.append(".footer { text-align: center; padding: 20px; color: #999; font-size: 12px; background: #fafafa; }");
        html.append(".tag { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 500; }");
        html.append(".tag-danger { background: #ffebee; color: #c62828; }");
        html.append(".tag-warning { background: #fff3e0; color: #e65100; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        
        html.append("<div class=\"container\">");
        
        html.append("<div class=\"header\">");
        html.append("<h1>🔥 服务器紧急告警</h1>");
        html.append("<div class=\"subtitle\">").append(event.occurredAt().format(FORMATTER)).append("</div>");
        html.append("</div>");
        
        html.append("<div class=\"section\">");
        html.append("<div class=\"section-title\">📌 基本信息</div>");
        html.append("<div class=\"info-grid\">");
        
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">实例ID</div>");
        html.append("<div class=\"info-value\">").append(escapeHtml(event.instanceId())).append("</div>");
        html.append("</div>");
        
        if (event.instanceName() != null && !event.instanceName().isEmpty()) {
            html.append("<div class=\"info-item\">");
            html.append("<div class=\"info-label\">实例名称</div>");
            html.append("<div class=\"info-value\">").append(escapeHtml(event.instanceName())).append("</div>");
            html.append("</div>");
        }
        
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">云厂商</div>");
        html.append("<div class=\"info-value\">").append(escapeHtml(event.cloudProvider().getLabel())).append("</div>");
        html.append("</div>");
        
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">区域</div>");
        html.append("<div class=\"info-value\">").append(escapeHtml(event.region())).append("</div>");
        html.append("</div>");
        
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">当前状态</div>");
        html.append("<div class=\"info-value\"><span class=\"tag tag-danger\">").append(escapeHtml(event.instanceStatus().getLabel())).append("</span></div>");
        html.append("</div>");
        
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">告警类型</div>");
        html.append("<div class=\"info-value\"><span class=\"tag tag-danger\">").append(escapeHtml(event.alertType().getLabel())).append("</span></div>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</div>");
        
        if (llmShortComment != null && !llmShortComment.isBlank()) {
            html.append("<div class=\"section\">");
            html.append("<div class=\"section-title\">💢 AI毒舌吐槽</div>");
            
            html.append("<div class=\"llm-box\">");
            html.append("<div class=\"llm-title\">【简短吐槽】</div>");
            html.append("<div class=\"llm-content\">").append(escapeHtml(llmShortComment)).append("</div>");
            html.append("</div>");
            
            if (llmFullComment != null && !llmFullComment.isBlank() && !llmFullComment.equals(llmShortComment)) {
                html.append("<div class=\"llm-box\" style=\"margin-top: 15px;\">");
                html.append("<div class=\"llm-title\">【详细吐槽】</div>");
                html.append("<div class=\"llm-content\">").append(escapeHtml(llmFullComment)).append("</div>");
                html.append("</div>");
            }
            
            html.append("</div>");
        }
        
        if (event.errorMessage() != null && !event.errorMessage().isBlank()) {
            html.append("<div class=\"section\">");
            html.append("<div class=\"section-title\">❌ 错误信息</div>");
            html.append("<div class=\"error-box\">");
            html.append("<div class=\"error-content\">").append(escapeHtml(event.errorMessage())).append("</div>");
            html.append("</div>");
            html.append("</div>");
        }
        
        if (event.stackTrace() != null && !event.stackTrace().isBlank()) {
            html.append("<div class=\"section\">");
            html.append("<div class=\"section-title\">📋 堆栈追踪</div>");
            html.append("<div class=\"stack-trace\">").append(escapeHtml(event.stackTrace())).append("</div>");
            html.append("</div>");
        }
        
        if (event.extraInfo() != null && !event.extraInfo().isBlank()) {
            html.append("<div class=\"section\">");
            html.append("<div class=\"section-title\">📎 附加信息</div>");
            html.append("<div class=\"error-content\" style=\"background: #f5f5f5; padding: 15px; border-radius: 6px;\">").append(escapeHtml(event.extraInfo())).append("</div>");
            html.append("</div>");
        }
        
        if (rawLlmResponse != null && !rawLlmResponse.isBlank()) {
            html.append("<div class=\"section\">");
            html.append("<div class=\"section-title\">🔍 LLM原始响应</div>");
            html.append("<div class=\"stack-trace\">").append(escapeHtml(rawLlmResponse)).append("</div>");
            html.append("</div>");
        }
        
        html.append("<div class=\"footer\">");
        html.append("此报告由 Argus Central Hub 自动生成 | ").append(java.time.LocalDateTime.now().format(FORMATTER));
        html.append("</div>");
        
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
