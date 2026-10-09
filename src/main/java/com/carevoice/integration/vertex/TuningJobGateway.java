package com.carevoice.integration.vertex;

/**
 * Explicit Vertex tuning submission. Tests and dry runs do not call an implementation.
 */
public interface TuningJobGateway {
    SubmittedJob submit(TuningSubmission submission);

    String status(String resourceName);

    record TuningSubmission(
            String baseModel,
            String projectId,
            String location,
            String trainingGcsUri,
            String validationGcsUri,
            String displayName
    ) {}

    record SubmittedJob(String resourceName, String state) {}
}
