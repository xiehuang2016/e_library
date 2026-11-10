package com.example.elibrary.domain.policy.impl;

import com.example.elibrary.config.FineProperties;
import com.example.elibrary.domain.model.Loan;
import com.example.elibrary.domain.policy.FinePolicy;
import com.example.elibrary.constants.LibraryConstants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class DailyFinePolicy implements FinePolicy {
    private final FineProperties properties;

    public DailyFinePolicy(FineProperties properties) {
        this.properties = properties;
    }

    @Override
    public BigDecimal calculate(Loan loan, Instant returnedAt) {
        if (!returnedAt.isAfter(loan.getDueAt())) {
            return BigDecimal.ZERO.setScale(LibraryConstants.MONEY_SCALE);
        }

        Duration overdue = Duration.between(loan.getDueAt(), returnedAt);
        long completeDays = overdue.toDays();
        long overdueDays = completeDays
                + (overdue.minus(completeDays, ChronoUnit.DAYS).isZero() ? 0 : 1);
        return properties.getDailyRate()
                .multiply(BigDecimal.valueOf(overdueDays))
                .setScale(LibraryConstants.MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
