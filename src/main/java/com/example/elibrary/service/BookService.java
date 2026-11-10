package com.example.elibrary.service;

import com.example.elibrary.domain.model.Book;
import com.example.elibrary.domain.model.BookContent;
import com.example.elibrary.exception.LibraryException;
import com.example.elibrary.repository.BookRepository;
import com.example.elibrary.repository.projection.BookAvailability;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookService {
    private final BookRepository books;

    public BookService(BookRepository books) {
        this.books = books;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "book-content", key = "#p0")
    public BookContent getContent(String bookId) {
        Book book = books.findById(bookId)
                .orElseThrow(() -> new LibraryException(HttpStatus.NOT_FOUND, "Book not found: " + bookId));
        return toContent(book);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "book-search",
            key = "#p0 == null ? '' : #p0.toLowerCase(T(java.util.Locale).ROOT)")
    public List<BookContent> search(String query) {
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return books.findByTitleNormalizedStartingWithOrAuthorNormalizedStartingWithOrIsbnNormalizedStartingWith(
                        normalizedQuery, normalizedQuery, normalizedQuery)
                .stream()
                .map(this::toContent)
                .sorted(Comparator.comparing(BookContent::bookId))
                .toList();
    }

    @Transactional(readOnly = true)
    public int getAvailableCopies(String bookId) {
        return books.findAvailableCopiesByBookId(bookId)
                .orElseThrow(() -> new LibraryException(HttpStatus.NOT_FOUND, "Book not found: " + bookId));
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> getAvailableCopies(Collection<String> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        return books.findAvailabilityByBookIdIn(bookIds).stream()
                .collect(Collectors.toMap(BookAvailability::getBookId, BookAvailability::getAvailableCopies));
    }

    @Transactional
    public boolean checkout(String bookId) {
        Book book = findBook(bookId);
        return book.checkout();
    }

    @Transactional
    public void returnCopy(String bookId) {
        findBook(bookId).returnCopy();
    }

    private Book findBook(String bookId) {
        return books.findById(bookId)
                .orElseThrow(() -> new LibraryException(HttpStatus.NOT_FOUND, "Book not found: " + bookId));
    }

    private BookContent toContent(Book book) {
        return new BookContent(
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getDescription(),
                book.getTotalCopies());
    }
}
