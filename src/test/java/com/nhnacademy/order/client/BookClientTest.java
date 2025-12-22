package com.nhnacademy.order.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nhnacademy.order.client.book.BookClient;
import com.nhnacademy.order.client.book.dto.BookResponse;
import com.nhnacademy.order.client.book.dto.BookStocksRequest;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.okhttp.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

/**
 * SpringBoot의 도움 없이, 순수 Feign과 Wiremock으로 BookClient의 기본 동작을 테스트합니다.
 */
class BookClientTest {

    @RegisterExtension
    static WireMockExtension wireMockServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private BookClient bookClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        bookClient = Feign.builder()
                .client(new OkHttpClient())
                .contract(new SpringMvcContract())
                .encoder(new JacksonEncoder(objectMapper))
                .decoder(new JacksonDecoder(objectMapper))
                .target(BookClient.class, wireMockServer.baseUrl());
    }

    @Test
    @DisplayName("책 정보 조회 성공")
    void testGetOrderBookInfos_Success() throws JsonProcessingException {
        // given
        List<Long> bookIds = List.of(1L, 2L);
        List<BookResponse> expectedResponse = List.of(
                new BookResponse(1L, "Book 1", 10000, true, "testImage1"),
                new BookResponse(2L, "Book 2", 20000, true , "testImage2")
        );

        wireMockServer.stubFor(get(urlPathEqualTo("/books/info"))
                .withQueryParam("bookIds", equalTo("1"))
                .withQueryParam("bookIds", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(expectedResponse))));

        // when
        List<BookResponse> actualResponse = bookClient.getOrderBookInfos(bookIds);

        // then
        assertThat(actualResponse).isEqualTo(expectedResponse);
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/books/info"))
                .withQueryParam("bookIds", equalTo("1"))
                .withQueryParam("bookIds", equalTo("2")));
    }

    @Test
    @DisplayName("재고 증가 성공")
    void testIncreaseStocks_Success() throws JsonProcessingException {
        // given
        UUID mockSagaId = mock(UUID.class);
        BookStocksRequest request = new BookStocksRequest(Map.of(1L, 10, 2L, 5));

        wireMockServer.stubFor(patch(urlPathEqualTo("/books/stocks/increase"))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(request)))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));

        // when & then
        assertDoesNotThrow(() -> bookClient.increaseStocks(mockSagaId, request));

        wireMockServer.verify(patchRequestedFor(urlPathEqualTo("/books/stocks/increase"))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(request))));
    }

    @Test
    @DisplayName("재고 감소 성공")
    void testDecreaseStocks_Success() throws JsonProcessingException {
        UUID mockSagaId = mock(UUID.class);
        // given
        BookStocksRequest request = new BookStocksRequest(Map.of(1L, 10, 2L, 5));

        wireMockServer.stubFor(patch(urlPathEqualTo("/books/stocks/decrease"))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(request)))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));

        // when & then
        assertDoesNotThrow(() -> bookClient.decreaseStocks(mockSagaId, request));

        wireMockServer.verify(patchRequestedFor(urlPathEqualTo("/books/stocks/decrease"))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(request))));
    }
}