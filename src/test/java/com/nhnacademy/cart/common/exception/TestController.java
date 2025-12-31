package com.nhnacademy.cart.common.exception;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/test/cart/error")
public class TestController {

    @GetMapping("/404")
    public void throwNotFound() {
        throw new CartNotFoundException("장바구니가 없습니다.");
    }

    @GetMapping("/409")
    public void throwConflict() {
        throw new CartCapacityExceededException("용량 초과입니다.");
    }

    @GetMapping("/parse-error")
    public void throwDateTimeParse() {
        throw new DateTimeParseException("날짜 형식이 틀림", "2025-99-99", 0);
    }

    @GetMapping("/illegal")
    public void throwIllegal() {
        throw new IllegalArgumentException("잘못된 인자입니다.");
    }

    @GetMapping("/500")
    public void throwException() throws Exception {
        throw new Exception("알 수 없는 서버 에러");
    }

    @PostMapping("/valid")
    public void validationCheck(@RequestBody @Valid TestDto dto) {
    }
}
