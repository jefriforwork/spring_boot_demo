package com.example.demo.cache;

import com.example.demo.entity.Book;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class BookCache {

    public static final String NAME = "books";

    private final CacheManager cacheManager;

    public BookCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public BookSnapshot get(Long id) {
        Cache cache = cacheManager.getCache(NAME);
        if (cache == null) {
            return null;
        }
        try {
            return cache.get(id, BookSnapshot.class);
        } catch (SerializationException | IllegalStateException ex) {
            cache.evict(id);
            return null;
        }
    }

    public void putAfterCommit(Book book) {
        BookSnapshot snapshot = BookSnapshot.from(book);
        Long id = book.getId();
        runAfterCommit(() -> cache().put(id, snapshot));
    }

    public void markDeletedAfterCommit(Long id) {
        BookSnapshot snapshot = BookSnapshot.deleted(id);
        runAfterCommit(() -> cache().put(id, snapshot));
    }

    public boolean putIfAbsent(Book book) {
        Cache.ValueWrapper existing = cache().putIfAbsent(book.getId(), BookSnapshot.from(book));
        return existing == null;
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(NAME);
        if (cache == null) {
            throw new IllegalStateException("Cache " + NAME + " is not configured");
        }
        return cache;
    }
}
