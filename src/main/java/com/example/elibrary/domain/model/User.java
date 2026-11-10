package com.example.elibrary.domain.model;

import com.example.elibrary.constants.LibraryConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "outstanding_debt", nullable = false, precision = 12, scale = LibraryConstants.MONEY_SCALE)
    private BigDecimal outstandingDebt = BigDecimal.ZERO.setScale(LibraryConstants.MONEY_SCALE);

    @Version
    @Column(nullable = false)
    private Long version;

    protected User() {
    }

    public User(String userId) {
        this.userId = userId;
    }

    public void addDebt(BigDecimal amount) {
        if (amount.signum() > 0) {
            outstandingDebt = outstandingDebt.add(amount);
        }
    }

    public void payDebt(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.compareTo(outstandingDebt) > 0) {
            throw new IllegalArgumentException("Payment must be positive and no greater than the balance");
        }
        outstandingDebt = outstandingDebt.subtract(amount);
    }

    public String getUserId() {
        return userId;
    }

    public BigDecimal getOutstandingDebt() {
        return outstandingDebt;
    }
}
