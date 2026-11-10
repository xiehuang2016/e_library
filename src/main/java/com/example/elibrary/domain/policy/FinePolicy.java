package com.example.elibrary.domain.policy;

import com.example.elibrary.domain.model.Loan;
import java.math.BigDecimal;
import java.time.Instant;

/** Extension point for overdue fine calculation. */
public interface FinePolicy {
    BigDecimal calculate(Loan loan, Instant returnedAt);
}
