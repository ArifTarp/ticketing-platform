package com.demo.ticketing.booking.infra.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * The Redis distributed lock on {@code eventId:seatId} (root {@code CLAUDE.md}'s "Kafka topics &
 * the checkout saga", {@code docs/roadmap.md} Phase 7, {@code docs/business-rules.md}'s "concurrent
 * seat-race" edge path). A single key doubles as both the mutual-exclusion lock <em>and</em> the
 * 10-minute hold itself — acquiring it atomically via {@code SET key value NX EX 600} is what
 * resolves two concurrent {@code POST /bookings/hold} requests for the same seat to exactly one
 * winner, without a DB round-trip race (see {@code BookingHoldService}).
 *
 * <p>Deliberately <strong>not</strong> a naive "GET then SET if missing" — that reintroduces the
 * exact TOCTOU race the lock exists to prevent. {@link #tryAcquire} uses
 * {@code SETNX ... EX} (via {@link StringRedisTemplate#opsForValue()}'s {@code setIfAbsent}
 * overload, which Lettuce sends as a single atomic {@code SET key value NX EX ttl} command) and
 * {@link #release} uses a Lua script so a caller only ever deletes the key it itself set (the
 * classic compare-and-delete unlock pattern), never another holder's key.
 *
 * <p>Phase 8 ({@code saga-orchestrator}) reuses this same key convention to release holds on
 * payment failure and on the timeout sweep — see {@code services/booking/CLAUDE.md}.
 */
@Component
public class SeatHoldLockService {

    /** Matches business-rules.md's "Hold TTL: 10 minutes." */
    public static final Duration HOLD_TTL = Duration.ofMinutes(10);

    private static final String KEY_PREFIX = "booking:seat-hold:";

    /**
     * Only deletes the key if its value still matches {@code holdToken} — guards against a caller
     * accidentally releasing a lock it no longer owns (e.g. after the key expired and was
     * re-acquired by a different hold attempt in the window between a failed DB write and this
     * cleanup call).
     */
    private static final RedisScript<Long> RELEASE_IF_OWNED = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public SeatHoldLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Atomically acquires the {@code eventId:seatId} lock/hold for {@code holdToken}, valid for
     * {@link #HOLD_TTL}. Returns {@code false} immediately (no blocking/waiting) if another holder
     * already owns it — the caller must reject the whole hold request with 409, per
     * business-rules.md's concurrent seat-race path.
     */
    public boolean tryAcquire(Long eventId, Long seatId, String holdToken) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(eventId, seatId), holdToken, HOLD_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    /** Releases the lock/hold only if it is still owned by {@code holdToken}; a no-op otherwise. */
    public void release(Long eventId, Long seatId, String holdToken) {
        redisTemplate.execute(RELEASE_IF_OWNED, List.of(key(eventId, seatId)), holdToken);
    }

    private String key(Long eventId, Long seatId) {
        return KEY_PREFIX + eventId + ":" + seatId;
    }
}
