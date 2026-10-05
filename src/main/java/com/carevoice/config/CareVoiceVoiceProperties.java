package com.carevoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carevoice.voice")
public class CareVoiceVoiceProperties {
    private boolean enabled;
    private String provider = "groq";
    private String geminiTranscriptionModel = "gemini-3.5-transcribe";
    private int maxFileSizeMb = 20;
    private String language = "";
    private String transcriptionPrompt = DEFAULT_TRANSCRIPTION_PROMPT;
    private Groq groq = new Groq();

    public static final String DEFAULT_TRANSCRIPTION_PROMPT =
            "Daily patient health check-in. The speaker may answer symptom questions, including pain ratings from 0 to 10. Preserve spoken numbers accurately.";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getGeminiTranscriptionModel() {
        return geminiTranscriptionModel;
    }

    public void setGeminiTranscriptionModel(String geminiTranscriptionModel) {
        this.geminiTranscriptionModel = geminiTranscriptionModel;
    }

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTranscriptionPrompt() {
        return transcriptionPrompt;
    }

    public void setTranscriptionPrompt(String transcriptionPrompt) {
        this.transcriptionPrompt = transcriptionPrompt;
    }

    public Groq getGroq() {
        return groq;
    }

    public void setGroq(Groq groq) {
        this.groq = groq;
    }

    public long maxFileSizeBytes() {
        return maxFileSizeMb * 1024L * 1024L;
    }

    public static class Groq {
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String apiKey = "";
        private String model = "whisper-large-v3-turbo";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
