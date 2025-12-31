package com.nhnacademy.cart.repository.impl;

import com.nhnacademy.cart.common.config.CartProperties;
import com.nhnacademy.cart.domain.EntityCart;
import com.nhnacademy.cart.domain.RedisCart;
import com.nhnacademy.cart.dto.CartDto;
import com.nhnacademy.cart.common.resolver.CartHolder;
import com.nhnacademy.cart.dto.CartSummaryDto;
import com.nhnacademy.cart.repository.CartJpaRepository;
import com.nhnacademy.cart.repository.CartRedisRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@EnableConfigurationProperties(CartProperties.class)
@TestPropertySource(properties = {
        "cart.key-prefix=test:cart",
        "cart.member-ttl-minutes=5",
        "cart.guest-ttl-days=1"
})
class CartRepositoryImplTest {

    @Autowired
    private CartRepositoryImpl cartRepository;

    @Autowired
    private CartJpaRepository jpaRepository;

    @Autowired
    private CartRedisRepository redisRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private EntityManager em; // JPA 동작 검증용

    private CartHolder memberHolder;
    private CartHolder guestHolder;
    private CartDto cartDto1;
    private CartDto cartDto2;

    @BeforeEach
    void setUp() {
        memberHolder = CartHolder.member(100L);
        guestHolder = CartHolder.guest("guest-abc-123");
        cartDto1 = new CartDto(1L, 2, LocalDateTime.now());
        cartDto2 = new CartDto(2L, 5, LocalDateTime.now());
        cleanUpRedis();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedis();
    }

    private void cleanUpRedis() {
        Set<String> keys = redisTemplate.keys("test:cart:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ======================================================================
    //  [Write] Validation & Branch Tests
    // ======================================================================

    @Test
    @DisplayName("Write: save - dto가 null이면 아무 동작 안 함")
    void save_NullDto() {
        cartRepository.save(memberHolder, null);
        assertThat(redisRepository.count(memberHolder)).isZero();
    }

    @Test
    @DisplayName("Write: saveAll - 빈 리스트면 아무 동작 안 함")
    void saveAll_EmptyList() {
        cartRepository.saveAll(memberHolder, Collections.emptyList());
        assertThat(redisRepository.count(memberHolder)).isZero();
    }

    @Test
    @DisplayName("Write: 정상 저장 (Redis O, DB X)")
    void save_Success() {
        cartRepository.save(memberHolder, cartDto1);

        assertThat(redisRepository.existsByBookId(memberHolder, 1L)).isTrue();
        assertThat(jpaRepository.count()).isZero();
    }

    // ======================================================================
    //  [Read] Cache Miss & WarmUp Logic
    // ======================================================================

    @Test
    @DisplayName("Read: Cache Miss -> DB 조회 -> Redis WarmUp -> 데이터 반환")
    void findAll_WarmUp() {
        // given: DB에만 데이터 존재
        saveToDb(memberHolder, cartDto1);

        // when
        List<CartDto> result = cartRepository.findAll(memberHolder);

        // then
        assertThat(result).hasSize(1);
        assertThat(redisRepository.hasKey(memberHolder)).isTrue(); // Redis에 생김
    }

    @Test
    @DisplayName("Read: DB에도 데이터가 없으면 빈 리스트 반환 & Empty Marking")
    void findAll_NoData() {
        // given: DB, Redis 모두 비어있음

        // when
        List<CartDto> result = cartRepository.findAll(memberHolder);

        // then
        assertThat(result).isEmpty();
        assertThat(redisRepository.isMarkedAsEmpty(memberHolder.getMemberId())).isTrue();
    }

    @Test
    @DisplayName("Read: Cache Hit (Redis에 있으면 DB 조회 안 함)")
    void findAll_CacheHit() {
        // given: Redis에만 데이터 존재
        redisRepository.put(memberHolder, RedisCart.create(1L, 10, LocalDateTime.now()));

        // when
        List<CartDto> result = cartRepository.findAll(memberHolder);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCartQuantity()).isEqualTo(10);
        // DB는 비어있음 확인
        assertThat(jpaRepository.count()).isZero();
    }

    // ======================================================================
    //  [Read] findByBookId, existsByBookId Branch Tests
    // ======================================================================

    @Test
    @DisplayName("Read: findByBookId - 워밍 후 메모리에서 찾음")
    void findByBookId_Warmed() {
        saveToDb(memberHolder, cartDto1);

        Optional<CartDto> result = cartRepository.findByBookId(memberHolder, 1L);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Read: findByBookId - 이미 캐시가 있으면 Redis에서 찾음")
    void findByBookId_Cached() {
        redisRepository.put(memberHolder, RedisCart.create(1L, 5, LocalDateTime.now()));

        Optional<CartDto> result = cartRepository.findByBookId(memberHolder, 1L);

        assertThat(result).isPresent();
        assertThat(result.get().getCartQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Read: existsByBookId - 워밍 후 메모리에서 확인")
    void existsByBookId_Warmed() {
        saveToDb(memberHolder, cartDto1);

        boolean exists = cartRepository.existsByBookId(memberHolder, 1L);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Read: existsByBookId - 캐시가 있지만 해당 아이템은 없는 경우")
    void existsByBookId_Cached_But_Not_Found() {
        redisRepository.put(memberHolder, RedisCart.create(2L, 5, LocalDateTime.now())); // 2번만 있음

        boolean exists = cartRepository.existsByBookId(memberHolder, 1L); // 1번 찾음

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Read: existsByBookId - 캐시도 없고 DB도 없음")
    void existsByBookId_NoData() {
        boolean exists = cartRepository.existsByBookId(memberHolder, 1L);
        assertThat(exists).isFalse();
    }

    // ======================================================================
    //  [Count & Summary] Branch Tests
    // ======================================================================

    @Test
    @DisplayName("Count: 워밍 후 메모리 리스트 사이즈 반환")
    void count_Warmed() {
        saveToDb(memberHolder, cartDto1);
        saveToDb(memberHolder, cartDto2);

        long count = cartRepository.countDistinctCartItem(memberHolder);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Count: 캐시가 있으면 Redis count 반환")
    void count_Cached() {
        redisRepository.put(memberHolder, RedisCart.create(1L, 1, LocalDateTime.now()));

        long count = cartRepository.countDistinctCartItem(memberHolder);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Count: 데이터 없으면 0")
    void count_Zero() {
        long count = cartRepository.countDistinctCartItem(memberHolder);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("Summary: 워밍 후 요약 정보 반환")
    void summary_Warmed() {
        saveToDb(memberHolder, cartDto1); // qty 2
        saveToDb(memberHolder, cartDto2); // qty 5

        CartSummaryDto summary = cartRepository.getSummary(memberHolder);

        assertThat(summary.getLineCount()).isEqualTo(2); // 종류 2개
        assertThat(summary.getTotalQuantity()).isEqualTo(7); // 합계 7
    }

    @Test
    @DisplayName("Summary: 캐시가 있으면 Redis 기반 요약")
    void summary_Cached() {
        redisRepository.put(memberHolder, RedisCart.create(1L, 10, LocalDateTime.now()));

        CartSummaryDto summary = cartRepository.getSummary(memberHolder);

        assertThat(summary.getLineCount()).isEqualTo(1);
        assertThat(summary.getTotalQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Summary: 데이터 없으면 (0, 0)")
    void summary_Empty() {
        CartSummaryDto summary = cartRepository.getSummary(memberHolder);

        assertThat(summary.getLineCount()).isZero();
        assertThat(summary.getTotalQuantity()).isZero();
    }

    // ======================================================================
    //  [Logic] Special Cases (Empty Marking, Guest)
    // ======================================================================

    @Test
    @DisplayName("Logic: Empty 마킹된 유저는 DB에 데이터가 있어도 조회 안 함 (Skip DB)")
    void skipDbIfMarkedEmpty() {
        // given: DB에는 데이터가 있지만, Redis에는 Empty Mark가 있음
        saveToDb(memberHolder, cartDto1);
        redisRepository.markAsEmpty(memberHolder);

        // when
        List<CartDto> result = cartRepository.findAll(memberHolder);

        // then: DB 조회 없이 빈 리스트 반환
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Safety: 비회원은 워밍업 시도 안 함")
    void guest_NoWarmUp() {
        // 비회원은 DB 조회를 안 하므로 항상 빈 리스트 (Redis에 없으면)
        List<CartDto> result = cartRepository.findAll(guestHolder);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("WarmUp: warmUp 메서드 직접 호출 테스트")
    void warmUp_DirectCall() {
        saveToDb(memberHolder, cartDto1);

        cartRepository.warmUp(memberHolder);

        assertThat(redisRepository.hasKey(memberHolder)).isTrue();
    }

    // ======================================================================
    //  Helper
    // ======================================================================
    private void saveToDb(CartHolder holder, CartDto dto) {
        EntityCart entity = EntityCart.builder()
                .memberId(holder.getMemberId())
                .bookId(dto.getBookId())
                .cartQuantity(dto.getCartQuantity())
                .createdAt(dto.getCreatedAt())
                .build();
        jpaRepository.save(entity);

        // 영속성 컨텍스트 초기화 (DB 조회를 강제하기 위함)
        em.flush();
        em.clear();
    }
}