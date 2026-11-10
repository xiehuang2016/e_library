package com.example.elibrary.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "loans", indexes = {
        @Index(name = "idx_loans_user_book_returned", columnList = "user_id,book_id,returned_at"),
        @Index(name = "idx_loans_user_returned_borrowed", columnList = "user_id,returned_at,borrowed_at"),
        @Index(name = "idx_loans_book", columnList = "book_id")
})
public class Loan {
    @Id
    @Column(name = "loan_id", length = 36)
    private String loanId;

    @Column(name = "book_id", nullable = false, length = 64)
    private String bookId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "borrowed_at", nullable = false)
    private Instant borrowedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "renewal_count", nullable = false)
    private int renewals;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Loan() {
    }

    public Loan(String loanId, String bookId, String userId, Instant now, int loanDays) {
        this.loanId = loanId;
        this.bookId = bookId;
        this.userId = userId;
        this.borrowedAt = now;
        this.dueAt = now.plus(loanDays, ChronoUnit.DAYS);
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    public void returnNow(Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("Loan already returned");
        }
        returnedAt = now;
    }

    public void renew(int loanDays) {
        dueAt = dueAt.plus(loanDays, ChronoUnit.DAYS);
        renewals++;
    }

    public String getLoanId() {
        return loanId;
    }

    public String getBookId() {
        return bookId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public int getRenewals() {
        return renewals;
    }
}
