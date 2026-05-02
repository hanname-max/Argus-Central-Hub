package com.argus.centralhub.shutdown.rule;

import com.argus.centralhub.domain.model.CloudInstance;

import java.time.LocalDateTime;

public class RuleContext {

    private final CloudInstance instance;
    private final LocalDateTime evaluationTime;

    public RuleContext(CloudInstance instance) {
        this.instance = instance;
        this.evaluationTime = LocalDateTime.now();
    }

    public RuleContext(CloudInstance instance, LocalDateTime evaluationTime) {
        this.instance = instance;
        this.evaluationTime = evaluationTime;
    }

    public CloudInstance getInstance() {
        return instance;
    }

    public LocalDateTime getEvaluationTime() {
        return evaluationTime;
    }

    public int getHour() {
        return evaluationTime.getHour();
    }
}
