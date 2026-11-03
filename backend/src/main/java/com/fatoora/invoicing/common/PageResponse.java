package com.fatoora.invoicing.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** A stable JSON shape for paged lists (Spring's Page JSON is not a public contract). */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.map(mapper).getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
