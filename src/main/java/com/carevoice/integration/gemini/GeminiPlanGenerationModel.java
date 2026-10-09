package com.carevoice.integration.gemini;
import com.carevoice.exception.PlanGenerationFailedException;
import com.carevoice.service.PlanGenerationJson;
import com.carevoice.service.PlanGenerationModel;
import com.carevoice.service.PlanGenerationPrompt;
import com.carevoice.service.PlanTaskContract;

import com.carevoice.config.CareVoicePlanAiProperties;
import com.carevoice.service.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.service.PlanGenerationModel.PlanGenerationResult;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporary base plan model. It uses the plan-ai model and timeout, not the extraction model.
 * Java validation remains authoritative. This class does not activate a plan.
 */
public class GeminiPlanGenerationModel implements PlanGenerationModel {
    private static final Logger log = LoggerFactory.getLogger(GeminiPlanGenerationModel.class);

    private final Client client;
    private final CareVoicePlanAiProperties properties;

    public GeminiPlanGenerationModel(Client client, CareVoicePlanAiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public PlanGenerationResult generate(PatientPlanGenerationContext context) {
        String model = properties.getBaseModel() == null ? "" : properties.getBaseModel().trim();
        if (model.isBlank()) {
            throw new PlanGenerationFailedException();
        }
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(PlanTaskContract.TEXT)))
                .responseMimeType("application/json")
                .responseSchema(PlanGenerationJson.responseSchema())
                .build();
        try {
            GenerateContentResponse response = client.models.generateContent(
                    model, PlanGenerationPrompt.userText(context), config);
            PlanGenerationResult result = PlanGenerationJson.parse(response.text());
            log.info("base plan generation completed patientId={}", context.patientId());
            return result;
        } catch (PlanGenerationFailedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("base plan generation failed patientId={} errorType={}",
                    context.patientId(), ex.getClass().getSimpleName());
            throw new PlanGenerationFailedException();
        }
    }

    public PlanGenerationResult parse(String json) {
        return PlanGenerationJson.parse(json);
    }
}
