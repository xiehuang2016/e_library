package com.example.elibrary.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScopeValueTest {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void nestedScopesRestorePreviousValues() {
        try (ScopeValue requestScope = ScopeValue.open(ScopeValue.REQUEST_ID, "request-123")) {
            assertEquals("request-123", ScopeValue.get(ScopeValue.REQUEST_ID).orElseThrow());

            try (ScopeValue userScope = ScopeValue.open(Map.of(ScopeValue.USER_ID, "user-7"))) {
                assertEquals("request-123", ScopeValue.get(ScopeValue.REQUEST_ID).orElseThrow());
                assertEquals("user-7", ScopeValue.get(ScopeValue.USER_ID).orElseThrow());
            }

            assertFalse(ScopeValue.get(ScopeValue.USER_ID).isPresent());
            assertEquals("request-123", ScopeValue.get(ScopeValue.REQUEST_ID).orElseThrow());
        }

        assertFalse(ScopeValue.get(ScopeValue.REQUEST_ID).isPresent());
    }

    @Test
    void callWithRestoresContextWhenActionThrows() {
        try (ScopeValue requestScope = ScopeValue.open(ScopeValue.REQUEST_ID, "request-456")) {
            try {
                ScopeValue.callWith(ScopeValue.USER_ID, "user-9", () -> {
                    throw new IllegalStateException("expected");
                });
            } catch (IllegalStateException expected) {
                assertEquals("request-456", ScopeValue.get(ScopeValue.REQUEST_ID).orElseThrow());
                assertFalse(ScopeValue.get(ScopeValue.USER_ID).isPresent());
            }
        }
    }

    @Test
    void sanitizesControlCharactersInLogValues() {
        ScopeValue.callWith(ScopeValue.USER_ID, "user\n42", () -> {
            assertEquals("user_42", ScopeValue.get(ScopeValue.USER_ID).orElseThrow());
            return null;
        });
    }
}
