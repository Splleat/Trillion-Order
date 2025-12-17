package com.nhnacademy.order.client.book.service;

import com.nhnacademy.order.client.book.BookClient;
import com.nhnacademy.order.client.book.dto.BookResponse;
import com.nhnacademy.order.client.book.dto.BookStocksRequest;
import com.nhnacademy.order.client.common.handler.ResilienceFallbackHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// 추후 외부 API 호출 관련 로직(ex) 로깅) 추가를 고려해 서비스 계층으로 분리 (확장성)
@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {
    private final BookClient bookClient;
    private final ResilienceFallbackHandler fallbackHandler;
    private static final String SERVICE_NAME = "도서 API";

    // 도서 정보 조회
    @CircuitBreaker(name = "book-service", fallbackMethod = "fallbackGetBookInfos")
    @Retry(name = "book-service")
    public Map<Long, BookResponse> getBookInfos(List<Long> bookIds) {
        List<BookResponse> bookResponses = bookClient.getOrderBookInfos(bookIds);

        return bookResponses.stream()
                .collect(Collectors.toMap(BookResponse::bookId, Function.identity()));
    }

    // 재고 감소 (주문 생성)
    @CircuitBreaker(name = "book-service", fallbackMethod = "fallbackDecreaseStocks")
    @Retry(name = "book-service")
    public void decreaseStocks(UUID sagaId, Map<Long, Integer> quantityMap) {
        bookClient.decreaseStocks(sagaId, new BookStocksRequest(quantityMap));
    }

    // 재고 증가 (주문 취소, 주문 상품 환불)
    @CircuitBreaker(name = "book-service", fallbackMethod = "fallbackIncreaseStocks")
    @Retry(name = "book-service")
    public void increaseStocks(UUID sagaId, Map<Long, Integer> quantityMap) {
        bookClient.increaseStocks(sagaId, new BookStocksRequest(quantityMap));
    }

    // 재고 복구 (주문 생성 실패 시)
    @CircuitBreaker(name = "book-service", fallbackMethod = "fallbackRollbackStocks")
    @Retry(name = "book-service")
    public void rollbackStocks(UUID sagaId, Map<Long, Integer> quantityMap) {
        bookClient.rollbackStocks(sagaId, new BookStocksRequest(quantityMap));
    }

    private Map<Long, BookResponse> fallbackGetBookInfos(List<Long> bookIds, Throwable throwable) {
        return fallbackHandler.handle(SERVICE_NAME, "도서 정보 조회", throwable);
    }

    private void fallbackDecreaseStocks(UUID sagaId, Map<Long, Integer> quantityMap, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "재고 감소", throwable);
    }

    private void fallbackIncreaseStocks(UUID sagaId, Map<Long, Integer> quantityMap, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "재고 증가", throwable);
    }

    private void fallbackRollbackStocks(UUID sagaId, Map<Long, Integer> quantityMap, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "재고 복구", throwable);
    }
}
