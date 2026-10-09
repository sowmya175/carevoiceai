package com.carevoice.proposal;

import com.carevoice.config.CareVoicePlanAiProperties;
import com.carevoice.proposal.PlanGenerationModel.PatientPlanGenerationContext;
import com.carevoice.proposal.PlanGenerationModel.PlanGenerationResult;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fine-tuned plan selector. It implements the same {@link PlanGenerationModel} contract
 * and does not activate a monitoring plan.
 */
public class VertexTunedPlanGenerationModel implements PlanGenerationModel {
    private static final Logger log = LoggerFactory.getLogger(VertexTunedPlanGenerationModel.class);

    private final Client client;
    private final CareVoicePlanAiProperties properties;

    public VertexTunedPlanGenerationModel(Client client, CareVoicePlanAiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public PlanGenerationResult generate(PatientPlanGenerationContext context) {
        String model = properties.getTunedModel() == null ? "" : properties.getTunedModel().trim();
        if (model.isBlank() || "latest".equalsIgnoreCase(model)) {
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
            log.info("tuned plan generation completed patientId={}", context.patientId());
            return result;
        } catch (PlanGenerationFailedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("tuned plan generation failed patientId={} errorType={}",
                    context.patientId(), ex.getClass().getSimpleName());
            throw new PlanGenerationFailedException();
        }
    }
}
