package com.chessanalyzer.game;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A simple, stable JSON shape for one page of results. Spring's own Page object
 * has many internal fields and its JSON form isn't guaranteed to stay the same,
 * so we copy just what the frontend needs.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
