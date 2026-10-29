package com.example.demo.kafka;

public class BookEvent {

    public static final String TYPE_CREATED = "BOOK_CREATED";
    public static final String TYPE_DELETED = "BOOK_DELETED";

    private String type;
    private Long bookId;
    private String bookName;

    public BookEvent() {
    }

    public BookEvent(String type, Long bookId, String bookName) {
        this.type = type;
        this.bookId = bookId;
        this.bookName = bookName;
    }

    public static BookEvent created(Long bookId, String bookName) {
        return new BookEvent(TYPE_CREATED, bookId, bookName);
    }

    public static BookEvent deleted(Long bookId) {
        return new BookEvent(TYPE_DELETED, bookId, null);
    }

    public String getType() {
        return type;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getBookName() {
        return bookName;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public String toMessage() {
        if (TYPE_DELETED.equals(type)) {
            return "書本已刪除: id=" + bookId;
        }
        return "新書上架: " + bookName;
    }
}
