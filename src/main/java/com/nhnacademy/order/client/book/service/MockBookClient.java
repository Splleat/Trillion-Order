package com.nhnacademy.order.client.book.service;

import com.nhnacademy.order.client.book.BookClient;
import com.nhnacademy.order.client.book.dto.BookResponse;
import com.nhnacademy.order.client.book.dto.BookStocksRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Profile("local123") // 일단 못쓰게
@Component
public class MockBookClient implements BookClient {

    @Override
    public List<BookResponse> getOrderBookInfos(List<Long> bookIds) {
        log.info("MockBookClient getOrderBookInfos called with bookIds: {}", bookIds);
        return bookIds.stream()
                .map(id -> new BookResponse(
                        id,
                        "Mock Book Title " + id,
                        25000,
                        true,
                        "http://example.com/mock_book_" + id + ".jpg"))
                .collect(Collectors.toList());
    }

    @Override
    public void increaseStocks(UUID sagaId, BookStocksRequest request) {
        log.info("MockBookClient 재고 증가 요청: {}", request);
    }

    @Override
    public void decreaseStocks(UUID sagaId, BookStocksRequest request) {
        log.info("MockBookClient 재고 감소 요청: {}", request);
    }

    @Override
    public void rollbackStocks(UUID sagaId, BookStocksRequest request) {
        log.info("MockBookClient 재고 복원 요청: {}", request);
    }
}
