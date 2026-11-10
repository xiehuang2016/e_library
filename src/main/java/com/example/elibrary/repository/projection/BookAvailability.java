package com.example.elibrary.repository.projection;

public interface BookAvailability {
    String getBookId();

    int getAvailableCopies();
}
