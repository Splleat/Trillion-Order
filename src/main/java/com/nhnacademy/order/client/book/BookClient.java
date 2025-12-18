package com.nhnacademy.order.client.book;

import com.nhnacademy.order.client.book.dto.BookResponse;
import com.nhnacademy.order.client.book.dto.BookStocksRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

//@Profile("!local")
@FeignClient(name = "book-service")
public interface BookClient {
    @GetMapping("/books/info")
    List<BookResponse> getOrderBookInfos(@RequestParam("bookIds") List<Long> bookIds);

    @PatchMapping("/books/stocks/increase")
    void increaseStocks(@RequestHeader("X-Saga-Id") UUID sagaId, @RequestBody BookStocksRequest request);

    @PatchMapping("/books/stocks/decrease")
    void decreaseStocks(@RequestHeader("X-Saga-Id") UUID sagaId, @RequestBody BookStocksRequest request);

    @PatchMapping("/books/stocks/rollback")
    void rollbackStocks(@RequestHeader("X-Saga-Id") UUID sagaId, @RequestBody BookStocksRequest request);
}