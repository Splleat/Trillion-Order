package com.nhnacademy.order.client.book.dto;

import java.util.Map;

public record BookStocksRequest(
    Map<Long, Integer> quantityMap
) {}
