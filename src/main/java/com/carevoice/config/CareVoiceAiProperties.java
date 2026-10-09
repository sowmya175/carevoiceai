package com.carevoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carevoice.ai")
public class CareVoiceAiProperties {
    private boolean enabled;
    private String model = "gemini-3.8-flash";
    private boolean adaptiveQuestionsEnabled;
    private int timeoutMillis = 12_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isAdaptiveQuestionsEnabled() {
        return adaptiveQuestionsEnabled;
    }

    public void setAdaptiveQuestionsEnabled(boolean adaptiveQuestionsEnabled) {
        this.adaptiveQuestionsEnabled = adaptiveQuestionsEnabled;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
}
