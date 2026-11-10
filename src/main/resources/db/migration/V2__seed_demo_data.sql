INSERT INTO users (user_id, outstanding_debt, version) VALUES
    ('reader-1', 0.00, 0),
    ('reader-2', 0.00, 0),
    ('reader-3', 0.00, 0);

INSERT INTO books (
    book_id, title, author, isbn, title_normalized, author_normalized, isbn_normalized,
    description, total_copies, available_copies, version
) VALUES
    ('book-1', 'Domain-Driven Design', 'Eric Evans', '9780321125217',
     'domain-driven design', 'eric evans', '9780321125217',
     'Software design reference', 2, 2, 0),
    ('book-2', 'Effective Java', 'Joshua Bloch', '9780134685991',
     'effective java', 'joshua bloch', '9780134685991',
     'Java best practices', 3, 3, 0),
    ('journal-1', 'Journal of Software', 'Editorial Board', 'JS-2024-01',
     'journal of software', 'editorial board', 'js-2024-01',
     'Digital journal issue', 1, 1, 0);
