package com.nhnacademy.order.client.book.dto;

public record BookResponse(
    Long bookId,
    String bookName,
    Long categoryId,
    int price,
    boolean canPackage,
    String imageUrl
) {}
