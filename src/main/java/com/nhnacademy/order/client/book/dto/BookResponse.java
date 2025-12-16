package com.nhnacademy.order.client.book.dto;

import java.util.Set;

public record BookResponse(
    Long bookId,
    String bookName,
    Set<Long> categoryIds,
    int price,
    boolean canPackage,
    String imageUrl
) {}
