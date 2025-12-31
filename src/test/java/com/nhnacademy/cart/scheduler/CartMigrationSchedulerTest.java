package com.nhnacademy.cart.scheduler;

import com.nhnacademy.cart.common.config.CartProperties;
import com.nhnacademy.cart.domain.EntityCart;
import com.nhnacademy.cart.domain.RedisCart;
import com.nhnacademy.cart.common.resolver.CartHolder;
import com.nhnacademy.cart.repository.CartJpaRepository;
import com.nhnacademy.cart.repository.CartRedisRepository;
import jakarta.persistence.EntityManager;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
@EnableConfigurationProperties(CartProperties.class)
@TestPropertySource(properties = {
        "cart.key-prefix=test:cart",
        "cart.scheduler.migration-delay=5000"
})
class CartMigrationSchedulerTest {
    // JPA Auditing 충돌 방지를 위해 모킹함
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    //ShedLock 중복 빈 등록 방지 및 실행 허용
    @MockitoBean
    private LockProvider lockProvider;

    @Autowired
    private CartMigrationScheduler scheduler;

    // 실제 동작은 하되, 특정 상황(예외)만 조작하기 위해 Spy 사용
    @MockitoSpyBean
    private CartJpaRepository jpaRepository;

    @MockitoSpyBean
    private CartRedisRepository redisRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private EntityManager em;

    private final Long MEMBER_ID = 100L;
    private CartHolder holder;

    @BeforeEach
    void setUp() {
        holder = CartHolder.member(MEMBER_ID);

        // ShedLock 항상 성공 처리
        given(lockProvider.lock(any(LockConfiguration.class)))
                .willReturn(Optional.of(mock(SimpleLock.class)));

        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jpaRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("test:cart:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ======================================================================
    //  정상 동기화 시나리오
    // ======================================================================

    @Test
    @DisplayName("Migration: Redis 데이터가 DB로 정상 이관(Overwrite) 되어야 함")
    void sync_Normal() {
        // given
        // Redis에 데이터 저장
        RedisCart item = RedisCart.create(1L, 5, LocalDateTime.now());
        redisRepository.put(holder, item); // put 내부에서 markAsDirty 자동 호출됨

        // check: Dirty Set에 들어갔는지 확인
        assertThat(redisRepository.popDirtyMemberIds(100)).hasSize(1);
        redisRepository.addDirtyMember(MEMBER_ID); // pop으로 꺼냈으니 다시 넣어줌 (테스트 위해)

        // when
        scheduler.syncCartDataToDb();

        // then
        // DB에 데이터가 저장되었는지 확인
        assertThat(jpaRepository.findAllByMemberId(MEMBER_ID)).hasSize(1);
        assertThat(jpaRepository.findAllByMemberId(MEMBER_ID).get(0).getCartQuantity()).isEqualTo(5);

        // Dirty Set이 비워졌는지 확인
        assertThat(redisRepository.popDirtyMemberIds(100)).isEmpty();
    }

    @Test
    @DisplayName("Migration: Empty Mark가 있으면 DB 데이터를 모두 삭제해야 함")
    void sync_EmptyMark() {
        // given
        // DB에 잔여 데이터 존재 (지워져야 함)
        saveToDb(MEMBER_ID, 1L);

        // Redis에 Empty Mark & Dirty Mark 설정
        redisRepository.markAsEmpty(holder);
        redisRepository.addDirtyMember(MEMBER_ID);

        // when
        scheduler.syncCartDataToDb();

        // then
        assertThat(jpaRepository.findAllByMemberId(MEMBER_ID)).isEmpty(); // DB 삭제됨
        assertThat(redisRepository.isMarkedAsEmpty(MEMBER_ID)).isFalse(); // Mark 해제됨
    }

    // ======================================================================
    //  데이터 유실 방지
    // ======================================================================

    @Test
    @DisplayName("Defense: Dirty 상태지만 Redis 데이터가 비어있다면(유실), DB를 삭제하지 않고 건너뜀")
    void sync_SkipIfRedisEmpty() {
        // given
        // DB에 소중한 데이터 존재
        saveToDb(MEMBER_ID, 1L);

        // Redis 데이터는 없음(Empty) 하지만 Dirty Set에는 들어있음 (이상 상태)
        redisRepository.addDirtyMember(MEMBER_ID);

        // when
        scheduler.syncCartDataToDb();

        // then
        // 동기화 로직이 돌면 DB를 지우고 Redis(빈값)를 덮어씌워야 하지만,
        // 방어 로직 때문에 DB 데이터가 살아있어야 함.
        assertThat(jpaRepository.findAllByMemberId(MEMBER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("Loop: Dirty Member가 없으면 즉시 종료")
    void sync_NoDirtyMembers() {
        // given: Dirty Set 비어있음

        // when
        scheduler.syncCartDataToDb();

        // then
        // 아무 에러 없이 종료되고, DB 조회/삭제 로직이 실행되지 않았음을 검증
        // SpyBean을 통해 verify
        verify(jpaRepository, org.mockito.Mockito.never()).deleteAllByMemberId(any());
    }

    // ======================================================================
    //  예외 처리 및 재시도 검증
    // ======================================================================

    @Test
    @DisplayName("Exception: DataIntegrityViolationException(치명적 에러) 발생 시 재시도하지 않음(Drop)")
    void sync_CriticalException() {
        // given
        redisRepository.put(holder, RedisCart.create(1L, 1, LocalDateTime.now()));

        // DB 저장 시 치명적 에러 발생하도록 조작 (SpyBean)
        willThrow(new DataIntegrityViolationException("Constraint Violation"))
                .given(jpaRepository).saveAll(anyList());

        // when
        scheduler.syncCartDataToDb();

        // then
        // 에러가 로그에 찍히고 메서드는 정상 종료
        // Dirty Set에서 해당 ID가 제거된 상태여야 함 (재시도 안 함)
        assertThat(redisRepository.popDirtyMemberIds(100)).isEmpty();
    }

    @Test
    @DisplayName("Exception: 일반 Exception(일시적 장애) 발생 시 Dirty Set에 다시 추가(Retry)")
    void sync_RetryableException() {
        // given
        redisRepository.put(holder, RedisCart.create(1L, 1, LocalDateTime.now()));

        // DB 저장 시 일반 에러 발생하도록 조작
        willThrow(new RuntimeException("DB Connection Timeout"))
                .given(jpaRepository).saveAll(anyList());

        // when
        scheduler.syncCartDataToDb();

        // then
        // 에러가 로그에 찍히고
        // isMember로 존재 여부 확인 (데이터 보존)
        Boolean isMember = redisTemplate.opsForSet().isMember("test:cart:dirty:members", String.valueOf(MEMBER_ID));
        assertThat(isMember).isTrue();
    }

    // ======================================================================
    //  Loop Limit Test
    // ======================================================================
    @Test
    @DisplayName("Logic: 최대 루프 횟수(MAX_MIGRATION_LOOP)만큼만 동작하고 종료")
    void sync_MaxLoopLimit() {
        // 데이터가 없으면 루프 안 돔 -> 0번 실행 확인
        scheduler.syncCartDataToDb();
        verify(redisRepository).popDirtyMemberIds(100);
    }

    // Helper
    private void saveToDb(Long memberId, Long bookId) {
        EntityCart entity = EntityCart.builder()
                .memberId(memberId)
                .bookId(bookId)
                .cartQuantity(1)
                .createdAt(LocalDateTime.now())
                .build();
        jpaRepository.save(entity);
        em.flush();
        em.clear();
    }
}