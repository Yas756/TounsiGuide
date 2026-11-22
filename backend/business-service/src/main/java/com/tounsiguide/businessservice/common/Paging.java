package com.tounsiguide.businessservice.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** {@code ?page&size} as in the other services: page from 0, size from 1 to 50 (out-of-range values are clamped). */
public final class Paging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    private Paging() {
    }

    public static int page(int page) {
        return Math.max(0, page);
    }

    public static int size(int size) {
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(page(page), size(size), sort);
    }
}
