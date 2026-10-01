package com.example.ecommerce.global;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PagingSupport {

    private PagingSupport() {
    }

    public static boolean isPaged(Integer page, Integer size) {
        return page != null && size != null && size > 0 && page >= 0;
    }

    public static <T> Map<String, Object> slice(List<T> all, int page, int size) {
        List<T> source = all == null ? List.of() : all;
        int from = Math.min(page * size, source.size());
        int to = Math.min(from + size, source.size());
        int totalPages = source.isEmpty() ? 0 : (int) Math.ceil(source.size() / (double) size);

        Map<String, Object> body = new HashMap<>();
        body.put("content", source.subList(from, to));
        body.put("totalPages", totalPages);
        body.put("totalElements", source.size());
        body.put("number", page);
        body.put("size", size);
        return body;
    }
}
