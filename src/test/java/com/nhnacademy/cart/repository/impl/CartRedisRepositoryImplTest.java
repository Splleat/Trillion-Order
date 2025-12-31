package com.nhnacademy.cart.repository.impl;

import com.nhnacademy.cart.common.config.CartProperties;
import com.nhnacademy.cart.domain.RedisCart;
import com.nhnacademy.cart.common.resolver.CartHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataRedisTest
@Import(CartRedisRepositoryImpl.class)
@EnableConfigurationProperties(CartProperties.class)
@TestPropertySource(properties = {
        "cart.key-prefix=test:cart", // 테스트 격리를 위한 Prefix
        "cart.member-ttl-minutes=60",
        "cart.guest-ttl-days=3"
})
class CartRedisRepositoryImplTest {

    @MockitoBean //Jpa 없어도 실행 가능하게 모킹
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private CartRedisRepositoryImpl repository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private CartHolder memberHolder;
    private CartHolder guestHolder;
    private RedisCart item1;
    private RedisCart item2;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
            template.setHashKeySerializer(new StringRedisSerializer());
            template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
            return template;
        }
    }

    @BeforeEach
    void setUp() {
        memberHolder = CartHolder.member(100L);
        guestHolder = CartHolder.guest("guest-abc-123");
        item1 = RedisCart.create(1L, 2, LocalDateTime.now());
        item2 = RedisCart.create(2L, 5, LocalDateTime.now());
        cleanUpTestData();
    }

    @AfterEach
    void tearDown() {
        cleanUpTestData();
    }

    private void cleanUpTestData() {
        Set<String> keys = redisTemplate.keys("test:cart:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ========================================================
    //  ValidateAndGenerateKey Exception Tests
    //  (CartHolder가 null일 때 예외를 던지는지 확인)
    // ========================================================

    @Test
    @DisplayName("Validation: CartHolder가 null이면 예외 발생 (validateAndGenerateKey)")
    void validateHolderNull_ThrowsException() {
        // put
        assertThatThrownBy(() -> repository.put(null, item1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CartHolder는 null 일 수 없습니다.");

        // putAll
        assertThatThrownBy(() -> repository.putAll(null, List.of(item1)))
                .isInstanceOf(IllegalArgumentException.class);

        // restore
        assertThatThrownBy(() -> repository.restore(null, List.of(item1)))
                .isInstanceOf(IllegalArgumentException.class);

        // delete
        assertThatThrownBy(() -> repository.delete(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        // deleteAll
        assertThatThrownBy(() -> repository.deleteAll(null))
                .isInstanceOf(IllegalArgumentException.class);

        // findByBookId
        assertThatThrownBy(() -> repository.findByBookId(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        // existsByBookId
        assertThatThrownBy(() -> repository.existsByBookId(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        // count
        assertThatThrownBy(() -> repository.count(null))
                .isInstanceOf(IllegalArgumentException.class);

        // hasKey
        assertThatThrownBy(() -> repository.hasKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========================================================
    //  ID & Payload Validation Tests
    //  (BookId, MemberId Null 체크 및 데이터 Empty 체크)
    // ========================================================

    @Test
    @DisplayName("Validation: BookId 또는 MemberId가 null이면 예외 발생")
    void validateIdsNull_ThrowsException() {
        // delete (BookId null)
        assertThatThrownBy(() -> repository.delete(memberHolder, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bookId는 null 일 수 없습니다.");

        // findByBookId (BookId null)
        assertThatThrownBy(() -> repository.findByBookId(memberHolder, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bookId는 null 일 수 없습니다.");

        // existsByBookId (BookId null)
        assertThatThrownBy(() -> repository.existsByBookId(memberHolder, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bookId는 null 일 수 없습니다.");
    }

    @Test
    @DisplayName("Safety: 데이터(Payload)가 없거나 조건 미달 시 안전하게 종료 (Return)")
    void safetyReturns_NoException() {
        // put (Cart null) -> return
        repository.put(memberHolder, null);
        assertThat(repository.count(memberHolder)).isZero();

        // putAll (List null/empty) -> return
        repository.putAll(memberHolder, null);
        repository.putAll(memberHolder, Collections.emptyList());
        assertThat(repository.count(memberHolder)).isZero();

        // findAll (Holder null) -> return empty list (메서드 초입부 확인)
        assertThatThrownBy(() -> repository.findAll(null))
                .isInstanceOf(IllegalArgumentException.class);

        // popDirtyMemberIds (count <= 0) -> return empty
        assertThat(repository.popDirtyMemberIds(0)).isEmpty();

        // filterCleanMemberIds (null/empty) -> return empty
        assertThat(repository.filterCleanMemberIds(null)).isEmpty();
        assertThat(repository.filterCleanMemberIds(Collections.emptySet())).isEmpty();

        // deleteMembersBatch (null/empty) -> return
        repository.deleteMembersBatch(null);
        repository.deleteMembersBatch(Collections.emptySet());

        // removeOldEmptyMarks (score <= 0) -> return 0
        assertThat(repository.removeOldEmptyMarks(0)).isZero();
    }

    @Test
    @DisplayName("Safety: Scheduler 메서드들은 memberId가 null이면 조용히 종료 또는 false 반환")
    void schedulerSafetyTests() {
        // isMarkedAsEmpty (null) -> return false
        assertThat(repository.isMarkedAsEmpty(null)).isFalse();

        // removeEmptyMark (null) -> return
        repository.removeEmptyMark(null);

        // addDirtyMember (null) -> return
        repository.addDirtyMember(null);
    }

    // ========================================================
    //  Functional Tests (CUD & Read)
    // ========================================================

    @Test
    @DisplayName("Feature: 회원 저장, 조회, 삭제, 전체삭제")
    void memberCrud() {
        // Put
        repository.put(memberHolder, item1);
        assertThat(repository.count(memberHolder)).isEqualTo(1);
        assertThat(repository.hasKey(memberHolder)).isTrue();

        // Find
        assertThat(repository.findByBookId(memberHolder, 1L)).isPresent();
        assertThat(repository.findAll(memberHolder)).hasSize(1);

        // Delete (Last Item) -> Empty Mark Check
        repository.delete(memberHolder, 1L);
        assertThat(repository.count(memberHolder)).isZero();
        assertThat(repository.isMarkedAsEmpty(100L)).isTrue();

        // DeleteAll
        repository.put(memberHolder, item1);
        repository.deleteAll(memberHolder);
        assertThat(repository.hasKey(memberHolder)).isFalse();
        assertThat(repository.isMarkedAsEmpty(100L)).isTrue();
    }

    @Test
    @DisplayName("Feature: putAll 및 Dirty Check")
    void putAllAndDirty() {
        repository.putAll(memberHolder, List.of(item1, item2));

        assertThat(repository.count(memberHolder)).isEqualTo(2);

        // Dirty Set 확인
        Boolean isDirty = redisTemplate.opsForSet().isMember("test:cart:dirty:members", "100");
        assertThat(isDirty).isTrue();
    }

    @Test
    @DisplayName("Feature: Guest는 Dirty Set에 추가되지 않음")
    void guestDirtyCheck() {
        repository.put(guestHolder, item1);

        Boolean isDirty = redisTemplate.opsForSet().isMember("test:cart:dirty:members", guestHolder.getGuestId());
        assertThat(isDirty).isFalse();
    }

    // ========================================================
    //  Restore Logic Tests (3가지 분기)
    // ========================================================

    @Test
    @DisplayName("Restore: [1] 수동으로 삭제된 유저(Empty Marked)는 복구 무시")
    void restore_SkipMarked() {
        // given
        repository.deleteAll(memberHolder); // Mark as empty
        assertThat(repository.isMarkedAsEmpty(100L)).isTrue();

        // when
        repository.restore(memberHolder, List.of(item1));

        // then: 복구되지 않아야 함
        assertThat(repository.count(memberHolder)).isZero();
    }

    @Test
    @DisplayName("Restore: [2] 빈 리스트 복구 -> Empty Mark & Dirty 해제")
    void restore_EmptyList() {
        // given: Dirty 상태라고 가정
        repository.addDirtyMember(100L);

        // when
        repository.restore(memberHolder, Collections.emptyList());

        // then
        assertThat(repository.isMarkedAsEmpty(100L)).isTrue();
        Boolean isDirty = redisTemplate.opsForSet().isMember("test:cart:dirty:members", "100");
        assertThat(isDirty).isFalse();
    }

    @Test
    @DisplayName("Restore: [3] 정상 복구 -> 데이터 저장 & Dirty 해제")
    void restore_Normal() {
        // given
        repository.addDirtyMember(100L);

        // when
        repository.restore(memberHolder, List.of(item1));

        // then
        assertThat(repository.count(memberHolder)).isEqualTo(1);
        Boolean isDirty = redisTemplate.opsForSet().isMember("test:cart:dirty:members", "100");
        assertThat(isDirty).isFalse(); // DB와 동기화 되었으므로 Dirty 해제
    }

    // ========================================================
    //  Batch & Scheduler Tests (Pipelines)
    // ========================================================

    @Test
    @DisplayName("Batch: filterCleanMemberIds - Dirty가 아닌 멤버만 반환")
    void filterCleanMembers() {
        repository.addDirtyMember(100L); // 100 is dirty

        List<Long> result = repository.filterCleanMemberIds(Set.of(100L, 200L));

        assertThat(result).hasSize(1);
        assertThat(result).contains(200L);
    }

    @Test
    @DisplayName("Batch: deleteMembersBatch - 일괄 삭제 확인")
    void deleteMembersBatch() {
        // given
        repository.put(memberHolder, item1);
        repository.addDirtyMember(100L);
        repository.removeEmptyMark(100L);

        // when
        repository.deleteMembersBatch(Set.of(100L));

        // then
        assertThat(repository.hasKey(memberHolder)).isFalse();
        Boolean isDirty = redisTemplate.opsForSet().isMember("test:cart:dirty:members", "100");
        assertThat(isDirty).isFalse();
    }

    @Test
    @DisplayName("Scheduler: popDirtyMemberIds")
    void popDirty() {
        repository.addDirtyMember(100L);
        repository.addDirtyMember(200L);

        List<Object> popped = repository.popDirtyMemberIds(10);
        assertThat(popped).hasSize(2);
        assertThat(repository.popDirtyMemberIds(10)).isEmpty();
    }

    @Test
    @DisplayName("Scheduler: removeOldEmptyMarks")
    void removeOldEmptyMarks() {
        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add("test:cart:empty:members", "100", now - 10000);
        redisTemplate.opsForZSet().add("test:cart:empty:members", "200", now + 10000);

        long count = repository.removeOldEmptyMarks(now);
        assertThat(count).isEqualTo(1);
        assertThat(repository.isMarkedAsEmpty(100L)).isFalse();
        assertThat(repository.isMarkedAsEmpty(200L)).isTrue();
    }
}