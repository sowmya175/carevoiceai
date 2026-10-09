package com.carevoice.checkin;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Test clock. Production uses {@link Clock#systemUTC()}. */
public final class MutableClock extends Clock {
    private final AtomicReference<Instant> instant;

    public MutableClock(Instant instant) {
        this.instant = new AtomicReference<>(instant);
    }

    public void setInstant(Instant instant) {
        this.instant.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant.get(), zone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
