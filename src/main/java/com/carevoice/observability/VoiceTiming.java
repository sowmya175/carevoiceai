package com.carevoice.observability;

import org.slf4j.Logger;

import java.util.function.Supplier;

public final class VoiceTiming {
    private static final ThreadLocal<Boolean> HYBRID_EXTRACTION = new ThreadLocal<>();

    private VoiceTiming() {}

    public static long millisSince(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    public static void log(Logger log, String details) {
        log.info("voiceTiming {}", details);
    }

    public static <T> T hybridExtraction(Supplier<T> supplier) {
        Boolean previous = HYBRID_EXTRACTION.get();
        HYBRID_EXTRACTION.set(Boolean.TRUE);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                HYBRID_EXTRACTION.remove();
            } else {
                HYBRID_EXTRACTION.set(previous);
            }
        }
    }

    public static boolean insideHybridExtraction() {
        return Boolean.TRUE.equals(HYBRID_EXTRACTION.get());
    }
}
