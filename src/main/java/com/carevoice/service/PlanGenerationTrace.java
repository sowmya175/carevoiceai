package com.carevoice.service;

import org.springframework.stereotype.Component;

/**
 * Records which plan model produced the result on this request thread.
 */
@Component
public class PlanGenerationTrace {
    private final ThreadLocal<GenerationMetadata> current = new ThreadLocal<>();

    public void record(GenerationMetadata metadata) {
        current.set(metadata);
    }

    public GenerationMetadata readAndClear() {
        GenerationMetadata metadata = current.get();
        current.remove();
        return metadata;
    }
}
