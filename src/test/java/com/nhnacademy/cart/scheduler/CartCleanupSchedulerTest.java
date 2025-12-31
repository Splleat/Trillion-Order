package com.nhnacademy.cart.scheduler;

import com.nhnacademy.cart.common.config.CartProperties;
import com.nhnacademy.cart.domain.EntityCart;
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
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional // 기본적으로 롤백
@EnableConfigurationProperties(CartProperties.class)
@TestPropertySource(properties = {
        "cart.key-prefix=test:cart",
        "cart.retention-days=30",
        "cart.scheduler.batch-size=10",
        "cart.scheduler.cleanup-cron=0 0 0 * * *"
})
class CartCleanupSchedulerTest {

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private LockProvider lockProvider;

    @Autowired
    private CartCleanupScheduler scheduler;

    @Autowired
    private CartJpaRepository jpaRepository;

    // 실제 동작하되, 필요할 때 에러를 발생시키기 위해 SpyBean 사용
    @MockitoSpyBean
    private CartRedisRepository redisRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private EntityManager em;

    private final Long CLEAN_MEMBER_ID = 100L;
    private final Long DIRTY_MEMBER_ID = 200L;

    @BeforeEach
    void setUp() {
        // ShedLock 잠금 획득 성공 처리
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
    //  [Logic] Normal Cleanup (Happy Path)
    // ======================================================================

    @Test
    @DisplayName("Cleanup: [Clean Member] 정상 삭제 (DB 삭제 O, Redis 삭제 로직 호출)")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanup_CleanMember() {
        try {
            // given
            createOldData(CLEAN_MEMBER_ID, 40);

            // when
            scheduler.cleanupOldCartItems();

            // then
            assertThat(jpaRepository.findAllByMemberId(CLEAN_MEMBER_ID)).isEmpty();

            // SpyBean 검증: Redis 삭제 메서드가 호출되었는지 확인
            verify(redisRepository).deleteMembersBatch(anySet());
        } finally {
            // NOT_SUPPORTED는 롤백이 안 되므로 수동 삭제 필요
            cleanUp();
        }
    }

    @Test
    @DisplayName("Cleanup: [Dirty Member] 활동 중인 회원은 삭제 스킵")
    void cleanup_DirtyMember() {
        // given
        createOldData(DIRTY_MEMBER_ID, 40);
        redisRepository.addDirtyMember(DIRTY_MEMBER_ID); // Redis에 Dirty Marking

        // when
        scheduler.cleanupOldCartItems();

        // then
        assertThat(jpaRepository.findAllByMemberId(DIRTY_MEMBER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("Cleanup: [Recent Data] 기간이 지나지 않은 데이터는 삭제 안 됨")
    void cleanup_RecentData() {
        // given
        createOldData(CLEAN_MEMBER_ID, 10); // 30일 안 지남

        // when
        scheduler.cleanupOldCartItems();

        // then
        assertThat(jpaRepository.findAllByMemberId(CLEAN_MEMBER_ID)).hasSize(1);
    }

    // ======================================================================
    //  [Branch] Empty Mark & Loop Logic
    // ======================================================================

    @Test
    @DisplayName("EmptyMarks: [삭제 대상 있음] 기간 지난 마킹 삭제")
    void cleanupOldEmptyMarks_WithData() {
        // given
        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add("test:cart:empty:members", "100", now - TimeUnit.DAYS.toMillis(4)); // Old
        redisTemplate.opsForZSet().add("test:cart:empty:members", "200", now - TimeUnit.DAYS.toMillis(1)); // New

        // when
        scheduler.cleanupOldEmptyMarks();

        // then
        assertThat(redisRepository.isMarkedAsEmpty(100L)).isFalse();
        assertThat(redisRepository.isMarkedAsEmpty(200L)).isTrue();
    }

    @Test
    @DisplayName("EmptyMarks: [삭제 대상 없음] 로그 분기(else) 커버리지")
    void cleanupOldEmptyMarks_NoData() {
        // given: 데이터 없음

        // when
        scheduler.cleanupOldEmptyMarks();

        // then: 에러 없이 정상 종료 (로그 찍힘)
        // verify를 통해 removeOldEmptyMarks가 호출되었는지 확인
        verify(redisRepository).removeOldEmptyMarks(anyDouble());
    }

    @Test
    @DisplayName("Cleanup: [Loop Break] 조회된 모든 회원이 Dirty 상태라면 스킵하고 루프 진행")
    void cleanup_AllDirtySkip() {
        // given
        createOldData(DIRTY_MEMBER_ID, 40);

        // SpyBean을 조작하여 "모든 회원이 Dirty라 Clean 회원이 없다"고 속임
        given(redisRepository.filterCleanMemberIds(anySet()))
                .willReturn(Collections.emptyList());

        // when
        scheduler.cleanupOldCartItems();

        // then
        // DB 삭제가 일어나지 않았어야 함
        assertThat(jpaRepository.findAllByMemberId(DIRTY_MEMBER_ID)).hasSize(1);
    }

    // ======================================================================
    //  [Exception] Catch Block Coverage
    // ======================================================================

    @Test
    @DisplayName("Exception: Redis 삭제 중 에러 발생 시 로그 찍고 계속 진행 (DB 롤백 X)")
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 커밋 후 이벤트 실행 위해
    void cleanup_RedisException() {
        try {
            // given
            createOldData(CLEAN_MEMBER_ID, 40);

            // Redis 삭제 시 에러 발생하도록 조작
            willThrow(new RuntimeException("Redis Connection Fail"))
                    .given(redisRepository).deleteMembersBatch(anySet());

            // when
            scheduler.cleanupOldCartItems();

            // then
            // 예외가 발생했어도 DB 삭제는 커밋되어야 함 (Redis 에러는 무시 정책)
            assertThat(jpaRepository.findAllByMemberId(CLEAN_MEMBER_ID)).isEmpty();
        } finally {
            cleanUp();
        }
    }

    @Test
    @DisplayName("Exception: Empty Mark 삭제 중 에러 발생 시 로그 찍고 종료")
    void cleanupOldEmptyMarks_Exception() {
        // given
        willThrow(new RuntimeException("Redis Error"))
                .given(redisRepository).removeOldEmptyMarks(anyDouble());

        // when
        scheduler.cleanupOldEmptyMarks();

        // then
        // 예외가 밖으로 전파되지 않고(catch됨) 테스트가 통과해야 함
    }

    // ======================================================================
    //  [Internal] Reflection Test
    // ======================================================================

    @Test
    @DisplayName("Internal: 트랜잭션이 없을 때 executeAfterCommit은 즉시 실행됨")
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 트랜잭션 없이 실행
    void executeAfterCommit_NoTransaction() throws Exception {
        // given
        java.lang.reflect.Method method = CartCleanupScheduler.class.getDeclaredMethod("executeAfterCommit", Runnable.class);
        method.setAccessible(true);

        final boolean[] executed = {false};

        // when
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        method.invoke(scheduler, (Runnable) () -> executed[0] = true);

        // then
        assertThat(executed[0]).isTrue();
    }

    // ======================================================================
    //  Helper Methods
    // ======================================================================

    private void createOldData(Long memberId, int daysAgo) {
        // 트랜잭션이 없는 상태에서 호출될 수도 있으므로, 트랜잭션 보장
        transactionTemplate().execute(status -> {
            EntityCart entity = EntityCart.builder()
                    .memberId(memberId)
                    .bookId(1L)
                    .cartQuantity(1)
                    .createdAt(LocalDateTime.now().minusDays(daysAgo))
                    .build();
            jpaRepository.save(entity);
            return null;
        });
    }

    // EntityManager를 이용해 수동 트랜잭션 템플릿 생성 (Helper용)
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate() {
        return new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.orm.jpa.JpaTransactionManager(em.getEntityManagerFactory())
        );
    }
}