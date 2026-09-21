package com.example.myapplication.network;

import java.util.Collections;
import java.util.List;

/**
 * Forma minima de un Page<T> de Spring Data - solo lo que la app consume.
 */
public class PageResponse<T> {
    private List<T> content;
    private int totalPages;
    private long totalElements;

    public List<T> getContent() {
        return content != null ? content : Collections.emptyList();
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }
}
