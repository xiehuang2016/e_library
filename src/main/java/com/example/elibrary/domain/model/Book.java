package com.example.elibrary.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.util.Locale;

@Entity
@Table(name = "books", indexes = {
        @Index(name = "idx_books_title_normalized", columnList = "title_normalized"),
        @Index(name = "idx_books_author_normalized", columnList = "author_normalized"),
        @Index(name = "idx_books_isbn_normalized", columnList = "isbn_normalized"),
        @Index(name = "ux_books_isbn", columnList = "isbn", unique = true)
})
public class Book {
    @Id
    @Column(name = "book_id", length = 64)
    private String bookId;

    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, length = 255)
    private String author;
    @Column(nullable = false, length = 64)
    private String isbn;
    @Column(name = "title_normalized", nullable = false, length = 255)
    private String titleNormalized;
    @Column(name = "author_normalized", nullable = false, length = 255)
    private String authorNormalized;
    @Column(name = "isbn_normalized", nullable = false, length = 64)
    private String isbnNormalized;
    @Column(length = 2000)
    private String description;
    @Column(name = "total_copies", nullable = false)
    private int totalCopies;
    @Column(name = "available_copies", nullable = false)
    private int availableCopies;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Book() {
    }

    public Book(String bookId, String title, String author, String isbn, String description, int copies) {
        this(bookId, title, author, isbn, description, copies, copies);
    }

    public Book(
            String bookId,
            String title,
            String author,
            String isbn,
            String description,
            int totalCopies,
            int availableCopies) {
        if (totalCopies < 1 || availableCopies < 0 || availableCopies > totalCopies) {
            throw new IllegalArgumentException("copy counts are invalid");
        }
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        normalizeSearchFields();
        this.description = description;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public boolean checkout() {
        if (availableCopies == 0) {
            return false;
        }
        availableCopies--;
        return true;
    }

    public void returnCopy() {
        if (availableCopies < totalCopies) {
            availableCopies++;
        }
    }

    public String getBookId() {
        return bookId;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getDescription() {
        return description;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    @PrePersist
    @PreUpdate
    private void normalizeSearchFields() {
        titleNormalized = title.toLowerCase(Locale.ROOT);
        authorNormalized = author.toLowerCase(Locale.ROOT);
        isbnNormalized = isbn.toLowerCase(Locale.ROOT);
    }
}
