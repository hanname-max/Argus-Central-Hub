package com.argus.centralhub.feishu.card;

import com.argus.centralhub.domain.model.CloudInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class InstanceCardBuilder {

    private final ObjectMapper objectMapper;

    public InstanceCardBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode buildInstanceStatusCard(List<CloudInstance> allInstances) {
        ObjectNode card = objectMapper.createObjectNode();
        
        card.put("config", buildConfig());
        card.set("header", buildHeader());
        
        ArrayNode elements = objectMapper.createArrayNode();
        
        List<CloudInstance> runningInstances = allInstances.stream()
                .filter(instance -> "running".equalsIgnoreCase(instance.getStatus().getCode()))
                .collect(Collectors.toList());
        
        List<CloudInstance> stoppedInstances = allInstances.stream()
                .filter(instance -> "stopped".equalsIgnoreCase(instance.getStatus().getCode()))
                .collect(Collectors.toList());
        
        List<CloudInstance> otherInstances = allInstances.stream()
                .filter(instance -> !"running".equalsIgnoreCase(instance.getStatus().getCode()) 
                        && !"stopped".equalsIgnoreCase(instance.getStatus().getCode()))
                .collect(Collectors.toList());
        
        elements.add(buildSummarySection(allInstances.size(), runningInstances.size(), 
                stoppedInstances.size(), otherInstances.size()));
        
        if (!runningInstances.isEmpty()) {
            elements.add(buildDivider());
            elements.add(buildSectionHeader("🟢 运行中的实例 (" + runningInstances.size() + ")", "green"));
            
            for (CloudInstance instance : runningInstances) {
                elements.add(buildInstanceCard(instance, true));
            }
        }
        
        if (!stoppedInstances.isEmpty()) {
            elements.add(buildDivider());
            elements.add(buildSectionHeader("⚪ 已停止的实例 (" + stoppedInstances.size() + ")", "grey"));
            
            for (CloudInstance instance : stoppedInstances) {
                elements.add(buildInstanceCard(instance, false));
            }
        }
        
        if (!otherInstances.isEmpty()) {
            elements.add(buildDivider());
            elements.add(buildSectionHeader("🔵 其他状态实例 (" + otherInstances.size() + ")", "blue"));
            
            for (CloudInstance instance : otherInstances) {
                elements.add(buildInstanceCard(instance, false));
            }
        }
        
        elements.add(buildDivider());
        elements.add(buildFooter());
        
        card.set("elements", elements);
        
        return card;
    }

    public ObjectNode buildLoadingCard() {
        ObjectNode card = objectMapper.createObjectNode();
        
        card.put("config", buildConfig());
        card.set("header", buildSimpleHeader("⏳ 正在查询实例状态...", "blue"));
        
        ArrayNode elements = objectMapper.createArrayNode();
        elements.add(buildNote("正在从各云服务商获取实例信息，请稍候..."));
        
        card.set("elements", elements);
        return card;
    }

    public ObjectNode buildEmptyCard() {
        ObjectNode card = objectMapper.createObjectNode();
        
        card.put("config", buildConfig());
        card.set("header", buildSimpleHeader("📋 实例状态查询结果", "grey"));
        
        ArrayNode elements = objectMapper.createArrayNode();
        elements.add(buildNote("暂未发现任何云实例，请检查云服务商配置是否正确。"));
        
        card.set("elements", elements);
        return card;
    }

    public ObjectNode buildErrorCard(String message) {
        ObjectNode card = objectMapper.createObjectNode();
        
        card.put("config", buildConfig());
        card.set("header", buildSimpleHeader("❌ 查询出错", "red"));
        
        ArrayNode elements = objectMapper.createArrayNode();
        elements.add(buildNote("错误信息: " + message));
        
        card.set("elements", elements);
        return card;
    }

    public ObjectNode buildShutdownResultCard(CloudInstance instance, boolean success, String reason) {
        ObjectNode card = objectMapper.createObjectNode();
        
        card.put("config", buildConfig());
        
        String headerText = success ? "✅ 实例关机成功" : "❌ 实例关机失败";
        String headerColor = success ? "green" : "red";
        card.set("header", buildSimpleHeader(headerText, headerColor));
        
        ArrayNode elements = objectMapper.createArrayNode();
        
        ObjectNode fields = objectMapper.createObjectNode();
        ArrayNode fieldArray = objectMapper.createArrayNode();
        
        fieldArray.add(buildField("实例ID", instance.getInstanceId()));
        fieldArray.add(buildField("云厂商", instance.getCloudProvider().getLabel()));
        fieldArray.add(buildField("区域", instance.getRegion()));
        
        if (instance.getInstanceName() != null && !instance.getInstanceName().isEmpty()) {
            fieldArray.add(buildField("实例名称", instance.getInstanceName()));
        }
        
        fields.set("fields", fieldArray);
        elements.add(fields);
        
        elements.add(buildNote("关机原因: " + reason));
        
        card.set("elements", elements);
        return card;
    }

    private ObjectNode buildConfig() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("wide_screen_mode", true);
        config.put("enable_forward", true);
        return config;
    }

    private ObjectNode buildHeader() {
        ObjectNode header = objectMapper.createObjectNode();
        
        ObjectNode title = objectMapper.createObjectNode();
        title.put("tag", "plain_text");
        title.put("content", "☁️ 云实例状态概览");
        header.set("title", title);
        
        header.put("template", "blue");
        
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

    private ObjectNode buildSummarySection(int total, int running, int stopped, int other) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("tag", "column_set");
        section.put("flex_mode", "none");
        section.put("background_style", "grey");
        
        ArrayNode columns = objectMapper.createArrayNode();
        
        columns.add(buildSummaryColumn("总计", String.valueOf(total), "blue"));
        columns.add(buildSummaryColumn("运行中", String.valueOf(running), "green"));
        columns.add(buildSummaryColumn("已停止", String.valueOf(stopped), "grey"));
        columns.add(buildSummaryColumn("其他", String.valueOf(other), "orange"));
        
        section.set("columns", columns);
        return section;
    }

    private ObjectNode buildSummaryColumn(String label, String value, String color) {
        ObjectNode column = objectMapper.createObjectNode();
        column.put("tag", "column");
        column.put("width", "weighted");
        column.put("weight", 1);
        
        ArrayNode elements = objectMapper.createArrayNode();
        
        ObjectNode valueElement = objectMapper.createObjectNode();
        valueElement.put("tag", "div");
        
        ObjectNode valueText = objectMapper.createObjectNode();
        valueText.put("tag", "lark_md");
        valueText.put("content", "**<font color='" + color + "'>" + value + "</font>**");
        valueElement.set("text", valueText);
        elements.add(valueElement);
        
        ObjectNode labelElement = objectMapper.createObjectNode();
        labelElement.put("tag", "div");
        
        ObjectNode labelText = objectMapper.createObjectNode();
        labelText.put("tag", "lark_md");
        labelText.put("content", label);
        labelElement.set("text", labelText);
        elements.add(labelElement);
        
        column.set("elements", elements);
        return column;
    }

    private ObjectNode buildSectionHeader(String text, String color) {
        ObjectNode header = objectMapper.createObjectNode();
        header.put("tag", "div");
        
        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("tag", "lark_md");
        textNode.put("content", "**<font color='" + color + "'>" + text + "</font>**");
        header.set("text", textNode);
        
        return header;
    }

    private ObjectNode buildInstanceCard(CloudInstance instance, boolean canShutdown) {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("tag", "div");
        
        ObjectNode fields = objectMapper.createObjectNode();
        ArrayNode fieldArray = objectMapper.createArrayNode();
        
        String displayName = instance.getInstanceName() != null && !instance.getInstanceName().isEmpty()
                ? instance.getInstanceName()
                : instance.getInstanceId().substring(0, Math.min(12, instance.getInstanceId().length())) + "...";
        
        fieldArray.add(buildField("实例", displayName));
        fieldArray.add(buildField("ID", instance.getInstanceId()));
        fieldArray.add(buildField("云厂商", instance.getCloudProvider().getLabel()));
        fieldArray.add(buildField("区域", instance.getRegion()));
        
        if (instance.getInstanceType() != null && !instance.getInstanceType().isEmpty()) {
            fieldArray.add(buildField("规格", instance.getInstanceType()));
        }
        
        String ipInfo = "";
        if (instance.getPublicIp() != null && !instance.getPublicIp().isEmpty()) {
            ipInfo = "公网: " + instance.getPublicIp();
        } else if (instance.getPrivateIp() != null && !instance.getPrivateIp().isEmpty()) {
            ipInfo = "内网: " + instance.getPrivateIp();
        }
        if (!ipInfo.isEmpty()) {
            fieldArray.add(buildField("IP", ipInfo));
        }
        
        fieldArray.add(buildField("状态", instance.getStatus().getLabel()));
        
        fields.set("fields", fieldArray);
        card.set("fields", fields);
        
        if (canShutdown) {
            ObjectNode actions = buildShutdownButton(instance);
            card.set("extra", actions);
        }
        
        return card;
    }

    private ObjectNode buildShutdownButton(CloudInstance instance) {
        ObjectNode button = objectMapper.createObjectNode();
        button.put("tag", "button");
        button.put("text", buildTextNode("关机"));
        button.put("type", "danger");
        
        ObjectNode value = objectMapper.createObjectNode();
        value.put("action", "shutdown");
        value.put("instance_id", instance.getInstanceId());
        value.put("provider", instance.getCloudProvider().name());
        value.put("region", instance.getRegion());
        
        button.set("value", value);
        
        ObjectNode confirm = objectMapper.createObjectNode();
        confirm.put("title", buildTextNode("确认关机"));
        confirm.put("text", buildTextNode("确定要关闭实例 [" + 
                (instance.getInstanceName() != null ? instance.getInstanceName() : instance.getInstanceId()) + 
                "] 吗？此操作将停止实例运行。"));
        button.set("confirm", confirm);
        
        return button;
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
        text.put("content", "**" + label + "**\n" + value);
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
        text.put("content", "💡 提示：点击「关机」按钮可停止运行中的实例");
        elements.add(text);
        
        footer.set("elements", elements);
        return footer;
    }
}
