package com.nhnacademy.order.client.book;

import com.nhnacademy.order.client.book.dto.BookResponse;
import com.nhnacademy.order.client.book.dto.BookStocksRequest;
import com.nhnacademy.order.common.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Profile("!local")
@FeignClient(name = "BOOK-SERVICE", configuration = FeignClientConfig.class)
public interface BookClient {
    @GetMapping("/api/order-books")
    List<BookResponse> getOrderBookInfos(@RequestParam("bookIds") List<Long> bookIds);

    @PatchMapping("/api/order-books/increase-stocks")
    void increaseStocks(@RequestBody BookStocksRequest request);

    @PatchMapping("/api/order-books/decrease-stocks")
    void decreaseStocks(@RequestBody BookStocksRequest request);

    @PatchMapping("/api/order-books/rollback-stocks")
    void rollbackStocks(@RequestBody BookStocksRequest request);
}
