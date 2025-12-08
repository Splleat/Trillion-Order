// TODO: 해당 테스트 고치고 다른 API와의 통신(MemberService, CouponService) 테스트 작성

//package com.nhnacademy.order.client;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nhnacademy.order.client.dto.BookResponse;
//import com.nhnacademy.order.client.dto.BookStocksRequest;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.context.annotation.FilterType;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//import static com.github.tomakehurst.wiremock.client.WireMock.*;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
//
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
//        properties = {"spring.cloud.openfeign.circuitbreaker.enabled=false", "toss.secret-key=test-key"})
//@AutoConfigureWireMock(port = 0)
//@ComponentScan(excludeFilters = @ComponentScan.Filter(
//        type = FilterType.REGEX,
//        pattern = "com\\.nhnacademy\\.payment\\..*" ))
//class BookClientTest {
//
//    @Autowired
//    private BookClient bookClient;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @DynamicPropertySource
//    static void overrideProperties(DynamicPropertyRegistry registry) {
//        registry.add("feign.client.config.book-service.url", () -> "http://localhost:${wiremock.server.port}");
//    }
//
//    @Test
//    @DisplayName("책 정보 조회 성공")
//    void testGetOrderBookInfos_Success() throws JsonProcessingException {
//        // given
//        List<Long> bookIds = List.of(1L, 2L);
//        List<BookResponse> expectedResponse = List.of(
//                new BookResponse(1L, "Book 1", 10000),
//                new BookResponse(2L, "Book 2", 20000)
//        );
//
//        // Feign 클라이언트가 @RequestParam 없이 List를 GET 요청으로 보낼 때,
//        // 종종 동일한 이름의 여러 쿼리 파라미터로 변환합니다 (e.g., ?bookIds=1&bookIds=2).
//        // 이 동작을 가정하고 스텁(stub)을 설정합니다.
//        stubFor(get(urlPathEqualTo("/api/book/1"))
//                .withQueryParam("bookIds", equalTo("1"))
//                .withQueryParam("bookIds", equalTo("2"))
//                .willReturn(aResponse()
//                        .withStatus(HttpStatus.OK.value())
//                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
//                        .withBody(objectMapper.writeValueAsString(expectedResponse))));
//
//        // when
//        List<BookResponse> actualResponse = bookClient.getOrderBookInfos(bookIds);
//
//        // then
//        assertThat(actualResponse).hasSize(2);
//        assertThat(actualResponse.get(0).bookId()).isEqualTo(1L);
//        assertThat(actualResponse.get(0).bookName()).isEqualTo("Book 1");
//        assertThat(actualResponse.get(1).price()).isEqualTo(20000);
//
//        verify(getRequestedFor(urlPathEqualTo("/api/book/1"))
//                .withQueryParam("bookIds", equalTo("1"))
//                .withQueryParam("bookIds", equalTo("2")));
//    }
//
//    @Test
//    @DisplayName("재고 증가 성공")
//    void testIncreaseStocks_Success() throws JsonProcessingException {
//        // given
//        BookStocksRequest request = new BookStocksRequest(UUID.randomUUID(), Map.of(1L, 10, 2L, 5));
//
//        stubFor(patch(urlPathEqualTo("/api/book/2"))
//                .withRequestBody(equalToJson(objectMapper.writeValueAsString(request)))
//                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));
//
//        // when & then
//        assertDoesNotThrow(() -> bookClient.increaseStocks(request));
//
//        verify(patchRequestedFor(urlPathEqualTo("/api/book/2"))
//                .withRequestBody(equalToJson(objectMapper.writeValueAsString(request))));
//    }
//
//    @Test
//    @DisplayName("재고 감소 성공")
//    void testDecreaseStocks_Success() throws JsonProcessingException {
//        // given
//        BookStocksRequest request = new BookStocksRequest(UUID.randomUUID(), Map.of(1L, 10, 2L, 5));
//
//        stubFor(patch(urlPathEqualTo("/api/book/3"))
//                .withRequestBody(equalToJson(objectMapper.writeValueAsString(request)))
//                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));
//
//        // when & then
//        assertDoesNotThrow(() -> bookClient.decreaseStocks(request));
//
//        verify(patchRequestedFor(urlPathEqualTo("/api/book/3"))
//                .withRequestBody(equalToJson(objectMapper.writeValueAsString(request))));
//    }
//}