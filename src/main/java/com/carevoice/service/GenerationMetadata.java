package com.carevoice.service;

/**
 * Developer metadata stored on a proposal. It is not part of the clinician or patient API.
 */
public record GenerationMetadata(
        String provider,
        String model,
        String datasetVersion,
        String taskContractVersion
) {}
