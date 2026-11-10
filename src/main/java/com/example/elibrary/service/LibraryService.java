package com.example.elibrary.service;

import com.example.elibrary.config.LoanProperties;
import com.example.elibrary.constants.LibraryConstants;
import com.example.elibrary.api.dto.BookResponse;
import com.example.elibrary.domain.model.Loan;
import com.example.elibrary.domain.model.User;
import com.example.elibrary.domain.policy.FinePolicy;
import com.example.elibrary.exception.LibraryException;
import com.example.elibrary.repository.LoanRepository;
import com.example.elibrary.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LibraryService {
    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);

    private final BookService bookService;
    private final LoanRepository loans;
    private final UserRepository users;
    private final FinePolicy finePolicy;
    private final LoanProperties loanProperties;
    private final Clock clock = Clock.systemUTC();

    public LibraryService(
            BookService bookService,
            LoanRepository loans,
            UserRepository users,
            FinePolicy finePolicy,
            LoanProperties loanProperties) {
        this.bookService = bookService;
        this.loans = loans;
        this.users = users;
        this.finePolicy = finePolicy;
        this.loanProperties = loanProperties;
    }

    @Transactional(readOnly = true)
    public List<BookResponse> browse(String query) {
        var content = bookService.search(query);
        Map<String, Integer> availability = bookService.getAvailableCopies(
                content.stream().map(item -> item.bookId()).collect(Collectors.toList()));
        return content.stream()
                .map(item -> BookResponse.from(item, availability.getOrDefault(item.bookId(), 0)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BookResponse getBook(String bookId) {
        return BookResponse.from(bookService.getContent(bookId), bookService.getAvailableCopies(bookId));
    }

    @Transactional
    public Loan borrow(String bookId, String userId) {
        return borrowBatch(userId, List.of(bookId)).get(0);
    }

    @Transactional
    public List<Loan> borrowBatch(String userId, List<String> bookIds) {
        validateBatchIds(bookIds, "bookIds");
        assertNoOutstandingDebt(userId);
        users.findById(userId).orElseGet(() -> users.saveAndFlush(new User(userId)));

        Set<String> uniqueBookIds = new HashSet<>(bookIds);
        if (uniqueBookIds.size() != bookIds.size()) {
            throw new LibraryException(HttpStatus.BAD_REQUEST, "bookIds must not contain duplicates");
        }

        for (String bookId : bookIds) {
            bookService.getContent(bookId);
            if (loans.existsByBookIdAndUserIdAndReturnedAtIsNull(bookId, userId)) {
                throw new LibraryException(HttpStatus.CONFLICT, "User already has this book: " + bookId);
            }
        }

        List<Loan> newLoans = new ArrayList<>();
        for (String bookId : bookIds) {
            if (!bookService.checkout(bookId)) {
                throw new LibraryException(HttpStatus.CONFLICT, "No copies available: " + bookId);
            }
            newLoans.add(new Loan(
                    UUID.randomUUID().toString(), bookId, userId, Instant.now(clock), loanProperties.getTermDays()));
        }

        List<Loan> savedLoans = loans.saveAll(newLoans);
        log.info("loans_borrowed count={}", savedLoans.size());
        return List.copyOf(savedLoans);
    }

    @Transactional
    public Loan renew(String loanId, String userId) {
        Loan loan = getOwnedLoan(loanId, userId);
        Instant now = Instant.now(clock);
        if (!loan.isActive()) {
            throw new LibraryException(HttpStatus.CONFLICT, "Loan already returned");
        }
        if (!now.isBefore(loan.getDueAt())) {
            throw new LibraryException(HttpStatus.CONFLICT, "Overdue loans cannot be renewed");
        }
        if (loan.getRenewals() >= loanProperties.getMaxRenewals()) {
            throw new LibraryException(HttpStatus.CONFLICT, "Maximum renewals reached");
        }

        loan.renew(loanProperties.getTermDays());
        log.info("loan_renewed renewalCount={}", loan.getRenewals());
        return loan;
    }

    @Transactional
    public ReturnResult returnBook(String loanId, String userId) {
        Loan loan = getOwnedLoan(loanId, userId);
        validateActive(loan);
        BatchReturnResult result = processReturns(userId, List.of(loan));
        return new ReturnResult(loan, result.getTotalFine(), result.getOutstandingDebt());
    }

    @Transactional
    public BatchReturnResult returnBatch(String userId, List<String> loanIds) {
        validateBatchIds(loanIds, "loanIds");
        Set<String> uniqueLoanIds = new HashSet<>(loanIds);
        if (uniqueLoanIds.size() != loanIds.size()) {
            throw new LibraryException(HttpStatus.BAD_REQUEST, "loanIds must not contain duplicates");
        }

        Map<String, Loan> loansById = loans.findAllById(loanIds).stream()
                .collect(Collectors.toMap(Loan::getLoanId, loan -> loan));
        List<Loan> loansToReturn = new ArrayList<>();
        for (String loanId : loanIds) {
            Loan loan = loansById.get(loanId);
            if (loan == null) {
                throw new LibraryException(HttpStatus.NOT_FOUND, "Loan not found: " + loanId);
            }
            if (!loan.getUserId().equals(userId)) {
                throw new LibraryException(HttpStatus.FORBIDDEN, "Loan belongs to another user");
            }
            validateActive(loan);
            loansToReturn.add(loan);
        }
        return processReturns(userId, loansToReturn);
    }

    @Transactional(readOnly = true)
    public List<Loan> currentLoans(String userId) {
        return loans.findByUserIdAndReturnedAtIsNullOrderByBorrowedAtAsc(userId);
    }

    @Transactional(readOnly = true)
    public BigDecimal debtBalance(String userId) {
        return users.findById(userId)
                .map(User::getOutstandingDebt)
                .orElse(BigDecimal.ZERO.setScale(LibraryConstants.MONEY_SCALE));
    }

    @Transactional
    public BigDecimal payDebt(String userId, BigDecimal amount) {
        User user = users.findById(userId)
                .orElseThrow(() -> new LibraryException(HttpStatus.NOT_FOUND, "No outstanding debt"));
        if (user.getOutstandingDebt().signum() > 0 && loans.existsByUserIdAndReturnedAtIsNull(userId)) {
            throw new LibraryException(HttpStatus.CONFLICT, "Return all active loans before paying debt");
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(user.getOutstandingDebt()) > 0) {
            throw new LibraryException(HttpStatus.BAD_REQUEST, "Payment must be positive and no greater than the balance");
        }
        user.payDebt(amount);
        log.info("debt_payment_applied amount={} outstandingDebt={}", amount, user.getOutstandingDebt());
        return user.getOutstandingDebt();
    }

    private void assertNoOutstandingDebt(String userId) {
        BigDecimal balance = debtBalance(userId);
        if (balance.signum() > 0) {
            log.warn("borrow_blocked outstandingDebt={}", balance);
            throw new LibraryException(HttpStatus.CONFLICT, "Outstanding debt must be paid before borrowing");
        }
    }

    private Loan getOwnedLoan(String loanId, String userId) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> new LibraryException(HttpStatus.NOT_FOUND, "Loan not found: " + loanId));
        if (!loan.getUserId().equals(userId)) {
            throw new LibraryException(HttpStatus.FORBIDDEN, "Loan belongs to another user");
        }
        return loan;
    }

    private BatchReturnResult processReturns(String userId, List<Loan> loansToReturn) {
        Instant returnedAt = Instant.now(clock);
        BigDecimal totalFine = BigDecimal.ZERO.setScale(LibraryConstants.MONEY_SCALE);
        for (Loan loan : loansToReturn) {
            loan.returnNow(returnedAt);
            bookService.returnCopy(loan.getBookId());
            totalFine = totalFine.add(finePolicy.calculate(loan, returnedAt));
        }

        if (totalFine.signum() > 0) {
            User user = users.findById(userId)
                    .orElseThrow(() -> new LibraryException(HttpStatus.NOT_FOUND, "User not found: " + userId));
            user.addDebt(totalFine);
            users.save(user);
        }

        BigDecimal outstandingDebt = debtBalance(userId);
        log.info("loans_returned count={} fine={} outstandingDebt={}",
                loansToReturn.size(), totalFine, outstandingDebt);
        return new BatchReturnResult(loansToReturn, totalFine, outstandingDebt);
    }

    private void validateActive(Loan loan) {
        if (!loan.isActive()) {
            throw new LibraryException(HttpStatus.CONFLICT, "Loan already returned");
        }
    }

    private void validateBatchIds(List<String> ids, String fieldName) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new LibraryException(HttpStatus.BAD_REQUEST, fieldName + " must contain at least one non-blank ID");
        }
    }

    public static class ReturnResult {
        private final Loan loan;
        private final BigDecimal fine;
        private final BigDecimal outstandingDebt;

        ReturnResult(Loan loan, BigDecimal fine, BigDecimal outstandingDebt) {
            this.loan = loan;
            this.fine = fine;
            this.outstandingDebt = outstandingDebt;
        }

        public Loan getLoan() {
            return loan;
        }

        public BigDecimal getFine() {
            return fine;
        }

        public BigDecimal getOutstandingDebt() {
            return outstandingDebt;
        }
    }

    public static class BatchReturnResult {
        private final List<Loan> loans;
        private final BigDecimal totalFine;
        private final BigDecimal outstandingDebt;

        BatchReturnResult(List<Loan> loans, BigDecimal totalFine, BigDecimal outstandingDebt) {
            this.loans = List.copyOf(loans);
            this.totalFine = totalFine;
            this.outstandingDebt = outstandingDebt;
        }

        public List<Loan> getLoans() {
            return loans;
        }

        public BigDecimal getTotalFine() {
            return totalFine;
        }

        public BigDecimal getOutstandingDebt() {
            return outstandingDebt;
        }
    }
}
