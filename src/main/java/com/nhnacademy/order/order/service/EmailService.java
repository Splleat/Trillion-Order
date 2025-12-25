package com.nhnacademy.order.order.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Async
    public void sendOrderNumber(String toEmail, String orderNumber) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("이메일 주소가 없어 주문 번호를 발송하지 못했습니다.");
            return;
        }

        try {
            log.info("주문 번호 이메일 발송 시작: {} -> {}", toEmail, orderNumber);
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[Trillion] 주문이 성공적으로 접수되었습니다.");

            String content = String.format("""
                <div style="font-family: 'Apple SD Gothic Neo', sans-serif; padding: 20px; border: 1px solid #ddd; border-radius: 10px;">
                    <h2 style="color: #333;">주문 확인 안내</h2>
                    <br/>
                    <div style="background-color: #f9f9f9; padding: 15px; border-radius: 5px;">
                        <span style="color: #666; font-size: 14px;">주문 번호</span>
                        <h3 style="color: #007bff; margin: 5px 0 0 0;">%s</h3>
                    </div>
                    <br/>
                    <p>감사합니다.</p>
                </div>
                """, orderNumber);

            helper.setText(content, true);

            javaMailSender.send(message);
            log.info("주문 번호 이메일 발송 완료: {}", toEmail);

        } catch (MessagingException e) {
            log.error("이메일 발송 중 오류 발생: {}", toEmail, e);
        }
    }
}
