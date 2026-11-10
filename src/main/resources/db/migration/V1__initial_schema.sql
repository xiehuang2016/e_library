CREATE TABLE books (
    book_id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    isbn VARCHAR(64) NOT NULL,
    title_normalized VARCHAR(255) NOT NULL,
    author_normalized VARCHAR(255) NOT NULL,
    isbn_normalized VARCHAR(64) NOT NULL,
    description VARCHAR(2000),
    total_copies INTEGER NOT NULL,
    available_copies INTEGER NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_books_copy_counts CHECK (
        total_copies >= 1 AND available_copies >= 0 AND available_copies <= total_copies
    )
);

CREATE UNIQUE INDEX ux_books_isbn ON books (isbn);
CREATE INDEX idx_books_title_normalized ON books (title_normalized);
CREATE INDEX idx_books_author_normalized ON books (author_normalized);
CREATE INDEX idx_books_isbn_normalized ON books (isbn_normalized);

CREATE TABLE users (
    user_id VARCHAR(64) PRIMARY KEY,
    outstanding_debt DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL,
    CONSTRAINT ck_users_nonnegative_debt CHECK (outstanding_debt >= 0)
);

CREATE TABLE loans (
    loan_id VARCHAR(36) PRIMARY KEY,
    book_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    borrowed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    due_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    returned_at TIMESTAMP(6) WITH TIME ZONE,
    renewal_count INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL,
    CONSTRAINT fk_loans_book FOREIGN KEY (book_id) REFERENCES books (book_id),
    CONSTRAINT fk_loans_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT ck_loans_renewal_count CHECK (renewal_count >= 0)
);

CREATE INDEX idx_loans_user_book_returned ON loans (user_id, book_id, returned_at);
CREATE INDEX idx_loans_user_returned_borrowed ON loans (user_id, returned_at, borrowed_at);
CREATE INDEX idx_loans_book ON loans (book_id);
