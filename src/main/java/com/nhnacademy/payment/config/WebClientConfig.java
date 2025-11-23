package com.nhnacademy.payment.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;


@Configuration
public class WebClientConfig {
        @Bean
        public WebClient tossWebClient() {
            HttpClient httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) //5초 안에 연결 실패시 에러
                    .responseTimeout(Duration.ofSeconds(10))//연결을 됐는데 10초안에 응답 없을시 에러
                    .doOnConnected(conn ->
                            conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS)) //외부 서버에서 응답을 주다가 응답이 끊긴 경우 10초 대기후 연결 끊어버림
                                    .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));//우리 쪽 서버에서 문제가 생긴 경우 10초 동안 전송이 안되면 끊어버림

            return WebClient.builder()
                    .baseUrl("https://api.tosspayments.com/v1")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

        }

}
