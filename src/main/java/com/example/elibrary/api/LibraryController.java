package com.example.elibrary.api;

import com.example.elibrary.service.LibraryService;
import com.example.elibrary.api.dto.BookResponse;
import com.example.elibrary.domain.model.Loan;
import com.example.elibrary.logging.ScopeValue;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Validated
public class LibraryController {
    private final LibraryService service;

    public LibraryController(LibraryService service) {
        this.service = service;
    }

    @GetMapping("/books")
    public List<BookResponse> browse(@RequestParam(required = false) String q) {
        return service.browse(q);
    }

    @GetMapping("/books/{bookId}")
    public BookResponse detail(@PathVariable String bookId) {
        return service.getBook(bookId);
    }

    @PostMapping("/loans")
    public Loan borrow(@Valid @RequestBody BorrowRequest request) {
        return ScopeValue.callWith(
                ScopeValue.USER_ID,
                request.getUserId(),
                () -> service.borrow(request.getBookId(), request.getUserId()));
    }

    @PostMapping("/loans/batch")
    public List<Loan> borrowBatch(@Valid @RequestBody BatchBorrowRequest request) {
        return ScopeValue.callWith(
                ScopeValue.USER_ID,
                request.getUserId(),
                () -> service.borrowBatch(request.getUserId(), request.getBookIds()));
    }

    @PostMapping("/loans/return-batch")
    public LibraryService.BatchReturnResult returnBatch(@Valid @RequestBody BatchReturnRequest request) {
        return ScopeValue.callWith(
                ScopeValue.USER_ID,
                request.getUserId(),
                () -> service.returnBatch(request.getUserId(), request.getLoanIds()));
    }

    @PostMapping("/loans/{loanId}/return")
    public LibraryService.ReturnResult returnBook(
            @PathVariable String loanId, @Valid @RequestBody UserRequest request) {
        return ScopeValue.callWith(
                ScopeValue.USER_ID,
                request.getUserId(),
                () -> service.returnBook(loanId, request.getUserId()));
    }

    @PostMapping("/loans/{loanId}/renew")
    public Loan renew(@PathVariable String loanId, @Valid @RequestBody UserRequest request) {
        return ScopeValue.callWith(
                ScopeValue.USER_ID,
                request.getUserId(),
                () -> service.renew(loanId, request.getUserId()));
    }

    @GetMapping("/users/{userId}/loans")
    public List<Loan> currentLoans(@PathVariable String userId) {
        return ScopeValue.callWith(ScopeValue.USER_ID, userId, () -> service.currentLoans(userId));
    }

    @GetMapping("/users/{userId}/debt")
    public BigDecimal debt(@PathVariable String userId) {
        return ScopeValue.callWith(ScopeValue.USER_ID, userId, () -> service.debtBalance(userId));
    }

    @PostMapping("/users/{userId}/debt/payments")
    public BigDecimal payDebt(@PathVariable String userId, @Valid @RequestBody PaymentRequest request) {
        return ScopeValue.callWith(
                ScopeValue.USER_ID,
                userId,
                () -> service.payDebt(userId, request.getAmount()));
    }

    public static class BorrowRequest extends UserRequest {
        @NotBlank
        private String bookId;

        public String getBookId() {
            return bookId;
        }

        public void setBookId(String bookId) {
            this.bookId = bookId;
        }
    }

    public static class BatchBorrowRequest extends UserRequest {
        @NotEmpty
        private List<@NotBlank String> bookIds;

        public List<String> getBookIds() { return bookIds; }
        public void setBookIds(List<String> bookIds) { this.bookIds = bookIds; }
    }

    public static class BatchReturnRequest extends UserRequest {
        @NotEmpty
        private List<@NotBlank String> loanIds;

        public List<String> getLoanIds() { return loanIds; }
        public void setLoanIds(List<String> loanIds) { this.loanIds = loanIds; }
    }

    public static class UserRequest {
        @NotBlank
        private String userId;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }

    public static class PaymentRequest {
        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal amount;

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}
