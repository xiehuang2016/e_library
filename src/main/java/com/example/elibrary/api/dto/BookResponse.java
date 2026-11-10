package com.example.elibrary.api.dto;

import com.example.elibrary.domain.model.BookContent;

public record BookResponse(
        String bookId,
        String title,
        String author,
        String isbn,
        String description,
        int totalCopies,
        int availableCopies) {
    public static BookResponse from(BookContent content, int availableCopies) {
        return new BookResponse(
                content.bookId(),
                content.title(),
                content.author(),
                content.isbn(),
                content.description(),
                content.totalCopies(),
                availableCopies);
    }
}
