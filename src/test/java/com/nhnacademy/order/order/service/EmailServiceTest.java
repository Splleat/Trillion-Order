package com.nhnacademy.order.order.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private EmailService emailService;

    @Mock
    private MimeMessage mimeMessage;

    @DisplayName("주문 번호 이메일 발송 성공")
    @Test
    void sendOrderNumber_Success() {
        // given
        String toEmail = "test@example.com";
        String orderNumber = "ORDER-1234";

        given(javaMailSender.createMimeMessage()).willReturn(mimeMessage);
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        // when
        emailService.sendOrderNumber(toEmail, orderNumber);

        // then
        verify(javaMailSender).send(any(MimeMessage.class));
    }

    @DisplayName("이메일 주소가 없으면 발송하지 않음")
    @Test
    void sendOrderNumber_NoEmail() {
        // given
        String toEmail = null;
        String orderNumber = "ORDER-1234";

        // when
        emailService.sendOrderNumber(toEmail, orderNumber);

        // then
        verify(javaMailSender, never()).createMimeMessage();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @DisplayName("이메일 발송 중 예외 발생 시 에러 로그 출력 (예외 던지지 않음)")
    @Test
    void sendOrderNumber_Exception() {
        // given
        String toEmail = "test@example.com";
        String orderNumber = "ORDER-1234";

        given(javaMailSender.createMimeMessage()).willReturn(mimeMessage);
        doThrow(new MailSendException("Mail server error")).when(javaMailSender).send(any(MimeMessage.class));

        // when
        emailService.sendOrderNumber(toEmail, orderNumber);

        // then
        verify(javaMailSender).send(any(MimeMessage.class));
    }
}
