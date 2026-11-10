package com.example.elibrary.logging;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** A scoped set of values copied into every SLF4J log event on the current thread. */
public final class ScopeValue implements AutoCloseable {
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");
    private static final int MAX_VALUE_LENGTH = 128;

    private final Map<String, String> previousContext;
    private boolean closed;

    private ScopeValue(Map<String, String> values) {
        Map<String, String> currentContext = MDC.getCopyOfContextMap();
        this.previousContext = currentContext == null ? null : new HashMap<>(currentContext);
        values.forEach((key, value) -> {
            if (key != null && value != null && SAFE_KEY.matcher(key).matches()) {
                String safeValue = CONTROL_CHARACTERS.matcher(value).replaceAll("_");
                MDC.put(key, safeValue.substring(0, Math.min(safeValue.length(), MAX_VALUE_LENGTH)));
            }
        });
    }

    public static ScopeValue open(String key, String value) {
        return new ScopeValue(Collections.singletonMap(key, value));
    }

    public static ScopeValue open(Map<String, String> values) {
        return new ScopeValue(values);
    }

    public static Optional<String> get(String key) {
        return Optional.ofNullable(MDC.get(key));
    }

    public static <T> T callWith(String key, String value, Supplier<T> action) {
        try (ScopeValue ignored = open(key, value)) {
            return action.get();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (previousContext == null || previousContext.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
    }
}
