package com.example.elibrary.domain.model;

/** Immutable catalog fields safe to retain in the local cache. */
public record BookContent(
        String bookId,
        String title,
        String author,
        String isbn,
        String description,
        int totalCopies) {
}
