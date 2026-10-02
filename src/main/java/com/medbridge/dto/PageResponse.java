package com.medbridge.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A stable JSON shape for paged results. Returning Spring's Page directly works but
 * exposes framework internals, and its JSON layout is not guaranteed across versions.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
