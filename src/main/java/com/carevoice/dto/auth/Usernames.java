package com.carevoice.dto.auth;

import java.util.Locale;

/** Canonical username form used by registration, login, and the demo clinician account. */
public final class Usernames {
    private Usernames() {}

    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
