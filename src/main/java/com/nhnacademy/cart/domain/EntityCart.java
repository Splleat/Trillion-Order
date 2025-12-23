package com.nhnacademy.cart.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor // Builder 패턴을 쓰려면 전체 생성자가 필요
@Builder
@Entity
@Table(name = "Cart",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "cart_member_book_unique",
                        columnNames = {"member_id", "book_id"}
                )
        },
        indexes = {
                // 스케줄러 성능을 위해 필수적인 인덱스
                @Index(name = "idx_cart_created_at", columnList = "created_at")
        }
)
public class EntityCart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id", nullable = false)
    Long cartId;

    @Column(name = "member_id", nullable = false)
    Long memberId;

    @Column(name = "book_id", nullable = false)
    Long bookId;

    @Column(name = "cart_quantity", nullable = false)
    int cartQuantity;

    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        // 값이 없을 때만 현재 시간으로 설정
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}