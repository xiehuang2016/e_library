package com.example.elibrary.service;

import com.example.elibrary.ELibraryApplication;
import com.example.elibrary.domain.model.Book;
import com.example.elibrary.domain.model.BookContent;
import com.example.elibrary.domain.model.User;
import com.example.elibrary.exception.LibraryException;
import com.example.elibrary.domain.model.Loan;
import com.example.elibrary.repository.BookRepository;
import com.example.elibrary.repository.UserRepository;
import com.example.elibrary.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = ELibraryApplication.class)
@Transactional
class LibraryServiceTest {
    @Autowired
    LibraryService service;

    @Autowired
    BookRepository books;

    @Autowired
    LoanRepository loans;

    @Autowired
    UserRepository users;

    @Autowired
    BookService bookService;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void resetData() {
        loans.deleteAll();
        users.deleteAll();
        books.deleteAll();
        books.save(new Book("book-1", "Domain-Driven Design", "Eric Evans", "9780321125217", "Reference", 1));
        books.save(new Book("book-2", "Effective Java", "Joshua Bloch", "9780134685991", "Java", 2));
        cacheManager.getCache("book-content").clear();
        cacheManager.getCache("book-search").clear();
    }

    @Test
    void browseAndDetailUseDatabase() {
        assertEquals(2, service.browse("").size());
        assertEquals(1, service.browse("effective").size());
        assertEquals("Effective Java", service.getBook("book-2").getTitle());

        LibraryException exception = assertThrows(LibraryException.class, () -> service.getBook("missing"));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void cachesImmutableBookContent() {
        BookContent firstRead = bookService.getContent("book-1");
        BookContent secondRead = bookService.getContent("book-1");

        assertSame(firstRead, secondRead);
    }

    @Test
    void borrowReturnAndListCurrentLoans() {
        Loan loan = service.borrow("book-1", "reader-1");
        assertEquals(0, service.getBook("book-1").getAvailableCopies());

        List<Loan> currentLoans = service.currentLoans("reader-1");
        assertEquals(1, currentLoans.size());

        LibraryService.ReturnResult result = service.returnBook(loan.getLoanId(), "reader-1");
        assertNotNull(result.getFine());
        assertEquals(1, service.getBook("book-1").getAvailableCopies());
        assertTrue(service.currentLoans("reader-1").isEmpty());
        assertThrows(LibraryException.class, () -> service.returnBook(loan.getLoanId(), "reader-1"));
    }

    @Test
    void batchBorrowAndReturnAreAppliedTogether() {
        List<Loan> borrowed = service.borrowBatch("reader-1", List.of("book-1", "book-2"));
        assertEquals(2, borrowed.size());
        assertEquals(0, service.getBook("book-1").getAvailableCopies());
        assertEquals(1, service.getBook("book-2").getAvailableCopies());

        LibraryService.BatchReturnResult returned = service.returnBatch(
                "reader-1", borrowed.stream().map(Loan::getLoanId).toList());
        assertEquals(2, returned.getLoans().size());
        assertEquals(0, returned.getTotalFine().compareTo(BigDecimal.ZERO));
        assertTrue(service.currentLoans("reader-1").isEmpty());
        assertEquals(1, service.getBook("book-1").getAvailableCopies());
        assertEquals(2, service.getBook("book-2").getAvailableCopies());
    }

    @Test
    void batchBorrowValidatesAllBooksBeforeChangingInventory() {
        LibraryException exception = assertThrows(
                LibraryException.class,
                () -> service.borrowBatch("reader-1", List.of("book-1", "missing")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(1, service.getBook("book-1").getAvailableCopies());
        assertTrue(service.currentLoans("reader-1").isEmpty());
    }

    @Test
    void batchReturnValidatesAllLoansBeforeChangingInventory() {
        Loan firstLoan = service.borrow("book-1", "reader-1");
        Loan otherUsersLoan = service.borrow("book-2", "reader-2");

        LibraryException exception = assertThrows(
                LibraryException.class,
                () -> service.returnBatch(
                        "reader-1", List.of(firstLoan.getLoanId(), otherUsersLoan.getLoanId())));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertTrue(service.currentLoans("reader-1").stream()
                .anyMatch(loan -> loan.getLoanId().equals(firstLoan.getLoanId())));
        assertEquals(0, service.getBook("book-1").getAvailableCopies());
        assertEquals(1, service.getBook("book-2").getAvailableCopies());
    }

    @Test
    void debtCanOnlyBePaidAfterReturningAllLoans() {
        User user = new User("reader-1");
        user.addDebt(new BigDecimal("2.00"));
        users.save(user);
        books.findById("book-1").orElseThrow().checkout();
        Loan loan = loans.save(new Loan(
                "active-loan", "book-1", "reader-1", Instant.now(), 14));

        LibraryException blocked = assertThrows(
                LibraryException.class,
                () -> service.payDebt("reader-1", new BigDecimal("2.00")));
        assertEquals(HttpStatus.CONFLICT, blocked.getStatus());
        assertEquals(0, service.debtBalance("reader-1").compareTo(new BigDecimal("2.00")));

        service.returnBook(loan.getLoanId(), "reader-1");
        assertEquals(0, service.payDebt("reader-1", new BigDecimal("2.00")).compareTo(BigDecimal.ZERO));
    }

    @Test
    void rejectsUnavailableAndDuplicateBorrow() {
        service.borrow("book-1", "reader-1");

        LibraryException unavailable = assertThrows(
                LibraryException.class,
                () -> service.borrow("book-1", "reader-2"));
        assertEquals(HttpStatus.CONFLICT, unavailable.getStatus());
        assertThrows(LibraryException.class, () -> service.borrow("book-1", "reader-1"));
    }

    @Test
    void onlyUserCanReturn() {
        Loan loan = service.borrow("book-1", "reader-1");

        LibraryException exception = assertThrows(
                LibraryException.class,
                () -> service.returnBook(loan.getLoanId(), "reader-2"));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void allowsAtMostThreeRenewals() {
        Loan loan = service.borrow("book-1", "reader-1");

        service.renew(loan.getLoanId(), "reader-1");
        service.renew(loan.getLoanId(), "reader-1");
        service.renew(loan.getLoanId(), "reader-1");

        assertEquals(3, loan.getRenewals());
        LibraryException exception = assertThrows(
                LibraryException.class,
                () -> service.renew(loan.getLoanId(), "reader-1"));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void outstandingFineBlocksBorrowingUntilPaid() {
        books.findById("book-1").orElseThrow().checkout();
        Instant borrowedAt = Instant.now().minus(16, ChronoUnit.DAYS);
        users.save(new User("reader-1"));
        Loan overdueLoan = loans.save(new Loan("overdue-loan", "book-1", "reader-1", borrowedAt, 14));

        LibraryService.ReturnResult result = service.returnBook(overdueLoan.getLoanId(), "reader-1");
        assertTrue(result.getFine().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(result.getFine(), service.debtBalance("reader-1"));

        LibraryException blocked = assertThrows(
                LibraryException.class,
                () -> service.borrow("book-1", "reader-1"));
        assertEquals(HttpStatus.CONFLICT, blocked.getStatus());

        BigDecimal remaining = service.payDebt("reader-1", result.getFine());
        assertEquals(0, remaining.compareTo(BigDecimal.ZERO));
        assertEquals(0, service.debtBalance("reader-1").compareTo(BigDecimal.ZERO));
        assertNotNull(service.borrow("book-1", "reader-1"));
    }
}
