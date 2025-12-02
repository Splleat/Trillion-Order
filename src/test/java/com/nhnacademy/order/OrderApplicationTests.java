package com.nhnacademy.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "toss.secret-key=dummy-secret-key-for-test"
})
class OrderApplicationTests {

    @Test
    void contextLoads() {
    }

}
