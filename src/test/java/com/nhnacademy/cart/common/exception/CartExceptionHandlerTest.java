package com.nhnacademy.cart.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TestController.class)
@Import(CartExceptionHandler.class)
class CartExceptionHandlerTest {
    // JPA Auditing 에러 방지용 (필수)
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("404 Not Found 핸들링 확인")
    void handleCartNotFoundException() throws Exception {
        mockMvc.perform(get("/test/cart/error/404"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("장바구니가 없습니다."));
    }

    @Test
    @DisplayName("409 Conflict 핸들링 확인")
    void handleCartCapacityExceededException() throws Exception {
        mockMvc.perform(get("/test/cart/error/409"))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("용량 초과입니다."));
    }

    @Test
    @DisplayName("400 Bad Request (Validation) 핸들링 확인")
    void handleValidationException() throws Exception {
        TestDto invalidDto = new TestDto();
        String json = objectMapper.writeValueAsString(invalidDto);

        mockMvc.perform(post("/test/cart/error/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Validation failed")));
    }

    @Test
    @DisplayName("400 Bad Request (DateTimeParse) 핸들링 확인")
    void handleDateTimeParseException() throws Exception {
        mockMvc.perform(get("/test/cart/error/parse-error"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Date/Time format error")));
    }

    @Test
    @DisplayName("400 Bad Request (IllegalArgument) 핸들링 확인")
    void handleIllegalArgumentException() throws Exception {
        mockMvc.perform(get("/test/cart/error/illegal"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 인자입니다."));
    }

    @Test
    @DisplayName("500 Internal Server Error 핸들링 확인")
    void handleGlobalException() throws Exception {
        mockMvc.perform(get("/test/cart/error/500"))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cart Internal Server Error")));
    }
}

