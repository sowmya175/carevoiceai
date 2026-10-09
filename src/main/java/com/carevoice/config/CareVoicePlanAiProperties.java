package com.carevoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

/**
 * Plan-generation model settings. These do not change clinical extraction, notes, or wording.
 */
@ConfigurationProperties(prefix = "carevoice.plan-ai")
public class CareVoicePlanAiProperties {
    public static final String RECOMMENDED_BASE_MODEL = "gemini-3.5-flash";
    public static final String NOT_TUNABLE_RUNTIME_MODEL = "gemini-3.8-flash";

    private boolean enabled;
    private String provider = "deterministic";
    private String baseModel = RECOMMENDED_BASE_MODEL;
    private String tunedModel = "";
    private String projectId = "";
    private String location = "";
    private int timeoutMillis = 20_000;
    private boolean fallbackToBase;

    public void requireCoherent(Environment environment) {
        if (!enabled) {
            return;
        }
        String selected = provider == null ? "" : provider.trim();
        if ("gemini-base".equalsIgnoreCase(selected)) {
            requireTunableBaseModel();
            requireGoogleApiKey(environment);
            return;
        }
        if ("vertex-tuned".equalsIgnoreCase(selected)) {
            requireTunableBaseModel();
            if (tunedModel == null || tunedModel.isBlank()) {
                throw new IllegalStateException(
                        "CAREVOICE_PLAN_TUNED_MODEL is required when CAREVOICE_PLAN_MODEL_PROVIDER is vertex-tuned.");
            }
            if ("latest".equalsIgnoreCase(tunedModel.trim())) {
                throw new IllegalStateException(
                        "CAREVOICE_PLAN_TUNED_MODEL must be an immutable tuned model resource, not latest.");
            }
            if (projectId == null || projectId.isBlank() || location == null || location.isBlank()) {
                throw new IllegalStateException(
                        "CAREVOICE_PLAN_PROJECT_ID and CAREVOICE_PLAN_LOCATION are required when CAREVOICE_PLAN_MODEL_PROVIDER is vertex-tuned.");
            }
            if (fallbackToBase) {
                requireGoogleApiKey(environment);
            }
            return;
        }
        throw new IllegalStateException(
                "CAREVOICE_PLAN_MODEL_PROVIDER must be gemini-base or vertex-tuned when CAREVOICE_PLAN_AI_ENABLED is true. "
                        + "Set CAREVOICE_PLAN_AI_ENABLED=false to use the deterministic generator.");
    }

    private void requireTunableBaseModel() {
        if (baseModel == null || baseModel.isBlank()) {
            throw new IllegalStateException("CAREVOICE_PLAN_BASE_MODEL is required when plan AI is enabled.");
        }
        if (NOT_TUNABLE_RUNTIME_MODEL.equalsIgnoreCase(baseModel.trim())) {
            throw new IllegalStateException(
                    "CAREVOICE_PLAN_BASE_MODEL cannot be gemini-3.8-flash. "
                            + "That model remains the extraction runtime. Use a tunable plan base such as gemini-3.5-flash.");
        }
    }

    private static void requireGoogleApiKey(Environment environment) {
        String apiKey = environment.getProperty("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_API_KEY is required when the plan provider is gemini-base or fallback to the base plan model is enabled.");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseModel() { return baseModel; }
    public void setBaseModel(String baseModel) { this.baseModel = baseModel; }
    public String getTunedModel() { return tunedModel; }
    public void setTunedModel(String tunedModel) { this.tunedModel = tunedModel; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public int getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    public boolean isFallbackToBase() { return fallbackToBase; }
    public void setFallbackToBase(boolean fallbackToBase) { this.fallbackToBase = fallbackToBase; }
}
