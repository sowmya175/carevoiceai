package com.carevoice.training;

import com.google.genai.Client;
import com.google.genai.types.CreateTuningJobConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.TuningDataset;
import com.google.genai.types.TuningJob;
import com.google.genai.types.TuningValidationDataset;

/**
 * Submits a supervised tuning job with Application Default Credentials.
 * Construct this only after an explicit submit confirmation.
 */
public final class VertexTuningJobGateway implements TuningJobGateway {
    private final String projectId;
    private final String location;
    private final int timeoutMillis;

    public VertexTuningJobGateway(String projectId, String location, int timeoutMillis) {
        this.projectId = projectId;
        this.location = location;
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 20_000;
    }

    @Override
    public SubmittedJob submit(TuningSubmission submission) {
        try (Client client = client()) {
            TuningJob job = client.tunings.tune(
                    submission.baseModel(),
                    TuningDataset.builder().gcsUri(submission.trainingGcsUri()).build(),
                    CreateTuningJobConfig.builder()
                            .validationDataset(TuningValidationDataset.builder()
                                    .gcsUri(submission.validationGcsUri())
                                    .build())
                            .tunedModelDisplayName(submission.displayName())
                            .build());
            return new SubmittedJob(job.name().orElse(""), job.state().map(Object::toString).orElse(""));
        }
    }

    @Override
    public String status(String resourceName) {
        try (Client client = client()) {
            TuningJob job = client.tunings.get(resourceName, null);
            return job.state().map(Object::toString).orElse("");
        }
    }

    private Client client() {
        return Client.builder()
                .vertexAI(true)
                .project(projectId)
                .location(location)
                .httpOptions(HttpOptions.builder()
                        .timeout(timeoutMillis)
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
    }
}
