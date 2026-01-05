package com.nhnacademy.cart.repository.impl;

import com.nhnacademy.cart.common.config.CartProperties;
import com.nhnacademy.cart.domain.RedisCart;
import com.nhnacademy.cart.common.resolver.CartHolder;
import com.nhnacademy.cart.repository.CartRedisRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CartRedisRepositoryImpl implements CartRedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CartProperties cartProperties;

    // 변경 감지를 위한 세트
    private String dirtyMembersKey;

    // 의도적으로 비웠는지, 기타 사유로 비워졌는지 확인하기 위한 세트
    private String emptyMembersKey;

    // ========================================================
    //  키 생성과 TTL 지정을 위한 헬퍼 메소드
    // ========================================================

    @PostConstruct // 빈 생성 후 실행
    public void init() {
        String prefix = cartProperties.getKeyPrefix();
        this.dirtyMembersKey = prefix + ":dirty:members";
        this.emptyMembersKey = prefix + ":empty:members";
    }

    private String validateAndGenerateKey(CartHolder holder) {
        if(holder == null)
            throw new IllegalArgumentException("CartHolder는 null 일 수 없습니다.");

        String prefix = cartProperties.getKeyPrefix();
        if (holder.isMember()) {
            return prefix + ":member:" + holder.getMemberId();
        } else {
            return prefix + ":guest:" + holder.getGuestId();
        }
    }

    private long getTtlInMinutes(CartHolder holder) {
        if (holder.isMember()) {
            return cartProperties.getMemberTtlMinutes();
        } else {
            return TimeUnit.DAYS.toMinutes(cartProperties.getGuestTtlDays());
        }
    }

    // ========================================================
    //  Helper Methods: Dirty & Empty Marking (Write-Back Logic)
    // ========================================================

    /**
     * Dirty Marking: 회원의 장바구니에 변경이 생겼음을 기록
     */
    public void markAsDirty(CartHolder holder) {
        if (holder.isMember()) {
            redisTemplate.opsForSet().add(dirtyMembersKey, holder.getMemberId().toString());
        }
    }

    /**
     * Empty Marking: 사용자가 의도적으로 장바구니를 비웠음을 기록
     * 현재 시간도 저장하여, 나중에 오래된 마킹을 지울 수 있게 함.
     */

    public void markAsEmpty(CartHolder holder) {
        if (holder.isMember()) {
            redisTemplate.opsForZSet().add(
                    emptyMembersKey,
                    holder.getMemberId().toString(),
                    System.currentTimeMillis() // Score = 현재 시간
            );
        }
    }

    /**
     * Dirty Mark Removal:
     * 장바구니가 Empty 마킹이 되어있지 않은데 여러 사유로 캐시(TTL 만료, 일시적인 오류)가 존재하지 않으나,
     * Dirty 마킹으로 남아있을 때... 오류 이므로 삭제해야함.
     * 정상 : (키가 없는 상태 + Empty마킹 + Dirty마킹)
     * 비정상 : (키가 없는 상태 + Dirty마킹)
     */
    private void removeFromDirtySet(CartHolder holder) {
        if (holder.isMember()) {
            redisTemplate.opsForSet().remove(dirtyMembersKey, holder.getMemberId().toString());
        }
    }

    /**
     * Empty Mark Removal: 여러 사유로 장바구니가 빈 상태가 아니게 되면 마킹 제거
     */
    private void removeFromEmptySet(CartHolder holder) {
        if (holder.isMember()) {
            redisTemplate.opsForZSet().remove(emptyMembersKey, holder.getMemberId().toString());}
    }

    // ========================================================
    //  CUD Operations (Modified for Write-Back)
    // ========================================================

    @Override//ok
    public void put(CartHolder holder, RedisCart cart) {
        String key = validateAndGenerateKey(holder);

        if (cart == null) {
            return;
        }

        long ttl = getTtlInMinutes(holder);

        redisTemplate.opsForHash().put(key, cart.getBookId().toString(), cart);
        redisTemplate.expire(key, ttl, TimeUnit.MINUTES);

        // Empty 상태 해제 (상품이 들어왔으므로)
        removeFromEmptySet(holder);
        // 변경사항 기록
        markAsDirty(holder);
    }

    @Override//ok
    public void putAll(CartHolder holder, List<RedisCart> carts) {
        String key = validateAndGenerateKey(holder);

        if (carts == null || carts.isEmpty()) {
            return;
        }

        long ttl = getTtlInMinutes(holder);

        Map<String, RedisCart> map = carts.stream()
                .collect(Collectors.toMap(
                        c -> String.valueOf(c.getBookId()),
                        c -> c));

        // Redis 저장
        redisTemplate.opsForHash().putAll(key, map);
        redisTemplate.expire(key, ttl, TimeUnit.MINUTES);

        // Empty 상태 해제
        removeFromEmptySet(holder);
        // 변경사항 기록
        markAsDirty(holder);
    }

    /**
     * DB 데이터를 Redis로 Warming합니다.
     */
    @Override
    public void restore(CartHolder holder, List<RedisCart> carts) {
        String key = validateAndGenerateKey(holder);

        // [Critical Check] 현재 의도적 삭제 상태인지 확인
        if (holder.isMember() && isMarkedAsEmpty(holder.getMemberId())) {
            return;
        }

        // 빈 상태를 restore 하는 경우 ...
        // 반복적인 DB 조회 -> warm-up 시도를 방지하기 위해, empty 상태 마킹
        if (carts == null || carts.isEmpty()) {
            markAsEmpty(holder);
            // 빈 상태를 원본으로 생각하고 restore 했음 -> Dirty가 아님
            removeFromDirtySet(holder);
            return;
        }

        long ttl = getTtlInMinutes(holder);

        Map<String, RedisCart> map = carts.stream()
                .collect(Collectors.toMap(
                        c -> String.valueOf(c.getBookId()),
                        c -> c));

        // Redis에 데이터 적재 (DB -> Redis)
        redisTemplate.opsForHash().putAll(key, map);
        redisTemplate.expire(key, ttl, TimeUnit.MINUTES);
        // 캐시가 없어 빈상태인데 Empty 마킹이 되어있지 않고, DirtySet에 등록되어 있다?
        // => DirtySet 에서 삭제... 오류 수정
        removeFromDirtySet(holder);
    }

    @Override
    public void delete(CartHolder holder, Long bookId) {
        String key = validateAndGenerateKey(holder);

        if(bookId == null)
            throw new IllegalArgumentException("bookId는 null 일 수 없습니다.");

        // Redis에서 개별 항목 삭제
        redisTemplate.opsForHash().delete(key, bookId.toString());

        // 변경사항 기록
        markAsDirty(holder);
        if(!hasKey(holder)) // key의 마지막 남은 value를 삭제하여, key가 삭제 됐을 경우 빈상태 기록
            markAsEmpty(holder);
    }

    @Override
    public void deleteAll(CartHolder holder) {
        // Redis에서 전체 항목 삭제
        String key = validateAndGenerateKey(holder);
        redisTemplate.delete(key);

        // 변경사항 기록
        markAsDirty(holder);
        markAsEmpty(holder);
    }

    // ========================================================
    //  READ Operations (Standard Redis Hash Ops)
    // ========================================================

    @Override
    public Optional<RedisCart> findByBookId(CartHolder holder, Long bookId) {
        String key = validateAndGenerateKey(holder);
        if(bookId == null)
            throw new IllegalArgumentException("bookId는 null 일 수 없습니다.");

        Object value = redisTemplate.opsForHash().get(key, bookId.toString());

        return Optional.ofNullable((RedisCart) value);
    }

    @Override
    public List<RedisCart> findAll(CartHolder holder) {
        String key = validateAndGenerateKey(holder);

        return redisTemplate.opsForHash().values(key).stream()
                .map(obj -> (RedisCart) obj)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByBookId(CartHolder holder, Long bookId) {
        String key = validateAndGenerateKey(holder);
        if(bookId == null)
            throw new IllegalArgumentException("bookId는 null 일 수 없습니다.");
        return redisTemplate.opsForHash().hasKey(key, bookId.toString());
    }

    @Override
    public long count(CartHolder holder) {
        String key = validateAndGenerateKey(holder);
        return redisTemplate.opsForHash().size(key);
    }

    @Override
    public boolean hasKey(CartHolder holder) {
        String key = validateAndGenerateKey(holder);
        return redisTemplate.hasKey(key);
    }

    // ========================================================
    //  Scheduler Support Methods
    // ========================================================

    /**
     * 스케줄러가 처리할 Dirty Member ID 목록을 꺼내옵니다. (SPOP)
     */
    @Override
    public List<Object> popDirtyMemberIds(long count) {
        if(count<=0) return Collections.emptyList();
        return redisTemplate.opsForSet().pop(dirtyMembersKey, count);
    }

    /**
     * 해당 회원이 '의도적으로 전체 삭제'를 했는지 확인
     */
    @Override
    public boolean isMarkedAsEmpty(Long memberId) {
        if(memberId == null) return false;

        // ZSCORE 명령어로 점수가 조회되면 존재하는 것
        Double score = redisTemplate.opsForZSet().score(emptyMembersKey, memberId.toString());
        return score != null;
    }

    /**
     * Empty 마킹을 정리
     */
    @Override
    public void removeEmptyMark(Long memberId) {
        if(memberId == null) return;

        redisTemplate.opsForZSet().remove(emptyMembersKey, memberId.toString());
    }

    @Override
    public List<Long> filterCleanMemberIds(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Set은 순서가 없으므로, List로 변환하여 미리 순서를 고정시킴
        List<Long> memberIdList = new ArrayList<>(memberIds);

        List<String> memberIdStrings = memberIdList.stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        // Pipeline 실행
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // Key용과 Value용 Serializer 분리
            RedisSerializer<String> keySerializer = redisTemplate.getStringSerializer();
            RedisSerializer valueSerializer = redisTemplate.getValueSerializer();

            // Key 직렬화 (Key는 문자열이 맞음)
            byte[] rawDirtyKey = keySerializer.serialize(dirtyMembersKey);

            for (String memberId : memberIdStrings) {
                // 직렬화 문제 조심...
                byte[] rawMemberId = valueSerializer.serialize(memberId);
                connection.sIsMember(rawDirtyKey, rawMemberId);
            }
            return null;
        });

        // 결과 매핑
        List<Long> cleanMemberIds = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            Boolean isDirty = (Boolean) results.get(i);
            Long memberId = memberIdList.get(i);

            // isDirty가 false면 Clean Member
            if (Boolean.FALSE.equals(isDirty)) {
                cleanMemberIds.add(memberId);
            }
        }

        return cleanMemberIds;
    }

    @Override
    public void deleteMembersBatch(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return;

        String prefix = cartProperties.getKeyPrefix();

        // Redis Key 목록 생성
        List<String> keys = memberIds.stream()
                .map(id -> prefix + ":member:" + id)
                .collect(Collectors.toList());

        // Member ID String 목록 생성
        List<String> memberIdStrings = memberIds.stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        // Pipeline 실행
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisSerializer<String> keySerializer = redisTemplate.getStringSerializer();
            RedisSerializer valueSerializer = redisTemplate.getValueSerializer();

            // 실제 데이터(Hash Key) 삭제
            for (String key : keys) {
                connection.del(keySerializer.serialize(key));
            }

            // Set 정리
            byte[] rawDirtyKey = keySerializer.serialize(dirtyMembersKey);
            byte[] rawEmptyKey = keySerializer.serialize(emptyMembersKey);

            byte[][] rawMemberIds = memberIdStrings.stream()
                    .map(id -> valueSerializer.serialize(id))
                    .toArray(byte[][]::new);

            if (rawMemberIds.length > 0) {
                // Dirty Set(Set)에서 제거
                connection.sRem(rawDirtyKey, rawMemberIds);
                // Empty Set(ZSet)에서 제거
                connection.zRem(rawEmptyKey, rawMemberIds);
            }
            return null;
        });
    }

    // 스케줄러 재시도 지원용
    @Override
    public void addDirtyMember(Long memberId) {
        if(memberId == null) return;
        redisTemplate.opsForSet().add(dirtyMembersKey, memberId.toString());
    }

    @Override
    public long removeOldEmptyMarks(double maxScore) {
        if(maxScore<=0.0) return 0;
        // ZSet에서 Score(시간)이 0 ~ maxScore 사이인 멤버들을 일괄 삭제
        Long count = redisTemplate.opsForZSet().removeRangeByScore(emptyMembersKey, 0, maxScore);
        return count != null ? count : 0;
    }
}