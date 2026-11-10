package com.example.elibrary.repository;

import com.example.elibrary.domain.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, String> {
    boolean existsByBookIdAndUserIdAndReturnedAtIsNull(String bookId, String userId);

    boolean existsByUserIdAndReturnedAtIsNull(String userId);

    List<Loan> findByUserIdAndReturnedAtIsNullOrderByBorrowedAtAsc(String userId);
}
