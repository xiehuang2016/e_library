package com.example.elibrary.repository;

import com.example.elibrary.domain.model.Book;
import com.example.elibrary.repository.projection.BookAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, String> {
    List<Book> findByTitleNormalizedStartingWithOrAuthorNormalizedStartingWithOrIsbnNormalizedStartingWith(
            String title, String author, String isbn);

    @Query("select b.availableCopies from Book b where b.bookId = :bookId")
    Optional<Integer> findAvailableCopiesByBookId(@Param("bookId") String bookId);

    @Query("select b.bookId as bookId, b.availableCopies as availableCopies "
            + "from Book b where b.bookId in :bookIds")
    List<BookAvailability> findAvailabilityByBookIdIn(@Param("bookIds") Collection<String> bookIds);
}
