package com.argus.centralhub.feishu.card;

import com.argus.centralhub.event.AlertType;
import com.argus.centralhub.event.ServerCriticalAlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class AlertCardBuilder {

    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AlertCardBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode buildAlertCard(ServerCriticalAlertEvent event, String llmShortComment) {
        ObjectNode card = objectMapper.createObjectNode();
        
        card.put("config", buildConfig());
        card.set("header", buildAlertHeader(event.alertType()));
        
        ArrayNode elements = objectMapper.createArrayNode();
        
        elements.add(buildInstanceInfoSection(event));
        
        if (llmShortComment != null && !llmShortComment.isBlank()) {
            elements.add(buildDivider());
            elements.add(buildLlmCommentSection(llmShortComment));
        }
        
        elements.add(buildDivider());
        elements.add(buildErrorSummarySection(event));
        
        elements.add(buildDivider());
        elements.add(buildActionButtons(event));
        
        elements.add(buildDivider());
        elements.add(buildFooter());
        
        card.set("elements", elements);
        
        return card;
    }

    public ObjectNode buildIgnoredAlertCard(ServerCriticalAlertEvent event) {
        ObjectNode card = objectMapper.createObjectNode();
        
        card.put("config", buildConfig());
        card.set("header", buildSimpleHeader("⚠️ 告警已忽略", "orange"));
        
        ArrayNode elements = objectMapper.createArrayNode();
        
        ObjectNode fields = objectMapper.createObjectNode();
        ArrayNode fieldArray = objectMapper.createArrayNode();
        
        fieldArray.add(buildField("实例ID", event.instanceId()));
        fieldArray.add(buildField("告警类型", event.alertType().getLabel()));
        fieldArray.add(buildField("时间", event.occurredAt().format(FORMATTER)));
        
        fields.set("fields", fieldArray);
        elements.add(fields);
        
        elements.add(buildNote("该告警已被忽略，如需重新处理请检查实例状态"));
        
        card.set("elements", elements);
        return card;
    }

    public ObjectNode buildRestartingAlertCard(ServerCriticalAlertEvent event) {
        ObjectNode card = objectMapper.createObjectNode();
        
        card.put("config", buildConfig());
        card.set("header", buildSimpleHeader("⏳ 实例重启中", "blue"));
        
        ArrayNode elements = objectMapper.createArrayNode();
        
        ObjectNode fields = objectMapper.createObjectNode();
        ArrayNode fieldArray = objectMapper.createArrayNode();
        
        fieldArray.add(buildField("实例ID", event.instanceId()));
        fieldArray.add(buildField("云厂商", event.cloudProvider().getLabel()));
        fieldArray.add(buildField("区域", event.region()));
        fieldArray.add(buildField("时间", event.occurredAt().format(FORMATTER)));
        
        fields.set("fields", fieldArray);
        elements.add(fields);
        
        elements.add(buildNote("正在执行实例重启操作，请稍候..."));
        
        card.set("elements", elements);
        return card;
    }

    public ObjectNode buildRestartResultCard(ServerCriticalAlertEvent event, boolean success, String message) {
        ObjectNode card = objectMapper.createObjectNode();
        
        card.put("config", buildConfig());
        
        String headerText = success ? "✅ 实例重启成功" : "❌ 实例重启失败";
        String headerColor = success ? "green" : "red";
        card.set("header", buildSimpleHeader(headerText, headerColor));
        
        ArrayNode elements = objectMapper.createArrayNode();
        
        ObjectNode fields = objectMapper.createObjectNode();
        ArrayNode fieldArray = objectMapper.createArrayNode();
        
        fieldArray.add(buildField("实例ID", event.instanceId()));
        fieldArray.add(buildField("云厂商", event.cloudProvider().getLabel()));
        fieldArray.add(buildField("区域", event.region()));
        if (event.instanceName() != null && !event.instanceName().isEmpty()) {
            fieldArray.add(buildField("实例名称", event.instanceName()));
        }
        
        fields.set("fields", fieldArray);
        elements.add(fields);
        
        elements.add(buildNote(message));
        
        card.set("elements", elements);
        return card;
    }

    private ObjectNode buildConfig() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("wide_screen_mode", true);
        config.put("enable_forward", true);
        return config;
    }

    private ObjectNode buildAlertHeader(AlertType alertType) {
        ObjectNode header = objectMapper.createObjectNode();
        
        ObjectNode title = objectMapper.createObjectNode();
        title.put("tag", "plain_text");
        
        String titleText;
        String template;
        
        if (alertType == AlertType.SERVER_DOWN) {
            titleText = "🔥 紧急告警：服务器宕机";
            template = "red";
        } else if (alertType == AlertType.CRITICAL_ERROR) {
            titleText = "⚠️ 严重异常告警";
            template = "orange";
        } else {
            titleText = "⚠️ " + alertType.getLabel();
            template = "orange";
        }
        
        title.put("content", titleText);
        header.set("title", title);
        header.put("template", template);
        
        return header;
    }

    private ObjectNode buildSimpleHeader(String title, String template) {
        ObjectNode header = objectMapper.createObjectNode();
        
        ObjectNode titleNode = objectMapper.createObjectNode();
        titleNode.put("tag", "plain_text");
        titleNode.put("content", title);
        header.set("title", titleNode);
        
        header.put("template", template);
        
        return header;
    }

    private ObjectNode buildInstanceInfoSection(ServerCriticalAlertEvent event) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("tag", "div");
        
        ObjectNode fields = objectMapper.createObjectNode();
        ArrayNode fieldArray = objectMapper.createArrayNode();
        
        String displayName = event.instanceName() != null && !event.instanceName().isEmpty()
                ? event.instanceName()
                : event.instanceId().substring(0, Math.min(12, event.instanceId().length())) + "...";
        
        fieldArray.add(buildField("实例", displayName));
        fieldArray.add(buildField("ID", event.instanceId()));
        fieldArray.add(buildField("云厂商", event.cloudProvider().getLabel()));
        fieldArray.add(buildField("区域", event.region()));
        fieldArray.add(buildField("当前状态", event.instanceStatus().getLabel()));
        fieldArray.add(buildField("发生时间", event.occurredAt().format(FORMATTER)));
        
        fields.set("fields", fieldArray);
        section.set("fields", fields);
        
        return section;
    }

    private ObjectNode buildLlmCommentSection(String llmComment) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("tag", "div");
        
        ObjectNode text = objectMapper.createObjectNode();
        text.put("tag", "lark_md");
        text.put("content", "**💢 AI毒舌吐槽：**\n" + 
                "<font color='orange'>" + escapeMarkdown(llmComment) + "</font>");
        section.set("text", text);
        
        return section;
    }

    private ObjectNode buildErrorSummarySection(ServerCriticalAlertEvent event) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("tag", "div");
        
        StringBuilder content = new StringBuilder();
        content.append("**📋 错误摘要：**\n");
        
        if (event.errorMessage() != null && !event.errorMessage().isBlank()) {
            content.append("错误信息: ").append(event.getShortErrorMessage()).append("\n");
        }
        
        content.append("告警类型: ").append(event.alertType().getLabel());
        
        ObjectNode text = objectMapper.createObjectNode();
        text.put("tag", "lark_md");
        text.put("content", content.toString());
        section.set("text", text);
        
        return section;
    }

    private ObjectNode buildActionButtons(ServerCriticalAlertEvent event) {
        ObjectNode actionSet = objectMapper.createObjectNode();
        actionSet.put("tag", "action");
        
        ArrayNode actions = objectMapper.createArrayNode();
        
        ObjectNode restartButton = objectMapper.createObjectNode();
        restartButton.put("tag", "button");
        restartButton.put("text", buildTextNode("🔄 一键重启"));
        restartButton.put("type", "danger");
        
        ObjectNode restartValue = objectMapper.createObjectNode();
        restartValue.put("action", "alert_restart");
        restartValue.put("instance_id", event.instanceId());
        restartValue.put("provider", event.cloudProvider().name());
        restartValue.put("region", event.region());
        restartValue.put("alert_type", event.alertType().name());
        restartButton.set("value", restartValue);
        
        ObjectNode restartConfirm = objectMapper.createObjectNode();
        restartConfirm.put("title", buildTextNode("确认重启"));
        restartConfirm.put("text", buildTextNode("确定要重启实例 [" + 
                (event.instanceName() != null ? event.instanceName() : event.instanceId()) + 
                "] 吗？此操作将重启实例。"));
        restartButton.set("confirm", restartConfirm);
        
        actions.add(restartButton);
        
        ObjectNode ignoreButton = objectMapper.createObjectNode();
        ignoreButton.put("tag", "button");
        ignoreButton.put("text", buildTextNode("🙈 忽略"));
        ignoreButton.put("type", "default");
        
        ObjectNode ignoreValue = objectMapper.createObjectNode();
        ignoreValue.put("action", "alert_ignore");
        ignoreValue.put("instance_id", event.instanceId());
        ignoreValue.put("provider", event.cloudProvider().name());
        ignoreValue.put("region", event.region());
        ignoreValue.put("alert_type", event.alertType().name());
        ignoreButton.set("value", ignoreValue);
        
        actions.add(ignoreButton);
        
        actionSet.set("actions", actions);
        
        return actionSet;
    }

    private ObjectNode buildTextNode(String content) {
        ObjectNode text = objectMapper.createObjectNode();
        text.put("tag", "plain_text");
        text.put("content", content);
        return text;
    }

    private ObjectNode buildField(String label, String value) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("is_short", true);
        
        ObjectNode text = objectMapper.createObjectNode();
        text.put("tag", "lark_md");
        text.put("content", "**" + label + "**\n" + (value != null ? value : "N/A"));
        field.set("text", text);
        
        return field;
    }

    private ObjectNode buildDivider() {
        ObjectNode divider = objectMapper.createObjectNode();
        divider.put("tag", "hr");
        return divider;
    }

    private ObjectNode buildNote(String content) {
        ObjectNode note = objectMapper.createObjectNode();
        note.put("tag", "note");
        
        ArrayNode elements = objectMapper.createArrayNode();
        
        ObjectNode text = objectMapper.createObjectNode();
        text.put("tag", "plain_text");
        text.put("content", content);
        elements.add(text);
        
        note.set("elements", elements);
        return note;
    }

    private ObjectNode buildFooter() {
        ObjectNode footer = objectMapper.createObjectNode();
        footer.put("tag", "note");
        
        ArrayNode elements = objectMapper.createArrayNode();
        
        ObjectNode text = objectMapper.createObjectNode();
        text.put("tag", "plain_text");
        text.put("content", "📧 完整审计报告已发送至邮箱存档");
        elements.add(text);
        
        footer.set("elements", elements);
        return footer;
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("`", "\\`")
                   .replace("[", "\\[")
                   .replace("]", "\\]");
    }
}
