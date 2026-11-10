package com.example.elibrary.domain.policy.impl;

import com.example.elibrary.config.FineProperties;
import com.example.elibrary.domain.model.Loan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyFinePolicyTest {
    private final DailyFinePolicy policy = new DailyFinePolicy(new FineProperties());

    @Test
    void roundsAnyStartedOverdueDayUp() {
        Instant borrowedAt = Instant.parse("2026-01-01T00:00:00Z");
        Loan loan = new Loan("loan-1", "book-1", "reader-1", borrowedAt, 14);

        assertEquals(new BigDecimal("0.00"), policy.calculate(loan, loan.getDueAt()));
        assertEquals(new BigDecimal("1.00"), policy.calculate(loan, loan.getDueAt().plusNanos(1)));
        assertEquals(new BigDecimal("1.00"), policy.calculate(loan, loan.getDueAt().plusSeconds(86_400)));
        assertEquals(new BigDecimal("2.00"), policy.calculate(
                loan, loan.getDueAt().plusSeconds(86_400).plusNanos(1)));
    }
}
