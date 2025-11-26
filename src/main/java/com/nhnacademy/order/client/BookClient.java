package com.nhnacademy.order.client;

import com.nhnacademy.order.client.dto.BookResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;

import java.util.List;
import java.util.Map;

@FeignClient(name = "book-service")
public interface BookClient {
    @GetMapping("/api/book/1")
    List<BookResponse> getOrderBookInfos(List<Long> bookIds);

    @PatchMapping("/api/book/2")
    void decreaseStock(Map<Long, Integer> bookStocks);
}
