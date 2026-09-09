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

    /**
     * Refreshes a key's TTL back out to {@link #HOLD_TTL} only if the key is still present —
     * a no-op (returns {@code 0}) if it already expired. No {@code holdToken} comparison is
     * possible here (unlike {@link #RELEASE_IF_OWNED}): the caller extending a
     * <em>previously</em>-acquired seat's TTL (ADR-0004's append-to-pending-booking path in
     * {@code BookingHoldService}) never had that seat's original {@code holdToken} in the first
     * place — it belongs to an earlier, unrelated hold request. Safe for the same reason
     * {@link #forceRelease} documents: {@code SeatAvailability} is the durable source of truth
     * gating re-acquisition, so refreshing whatever key happens to still be there (if any)
     * can never let a second holder believe it owns a seat this booking's DB row also claims.
     */
    private static final RedisScript<Long> EXTEND_IF_PRESENT = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 1 then "
                    + "return redis.call('expire', KEYS[1], ARGV[1]) "
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

    /**
     * Refreshes the {@code eventId:seatId} key's TTL back out to {@link #HOLD_TTL}, if the key is
     * still present. Used by {@code BookingHoldService.holdSeats}'s ADR-0004 append path: when a
     * second {@code hold} call for the same {@code (userId, eventId)} pair pushes the existing
     * {@code PENDING} booking's DB {@code expires_at} out to a new window, every seat already held
     * by that booking (not just the newly requested ones) must have its Redis TTL pushed out to
     * match — otherwise its lock/hold key can expire out from under a booking/{@code
     * SeatAvailability} row that the DB still says is live. Returns {@code true} if a key was
     * actually refreshed, {@code false} if it had already expired (a real but pre-existing gap this
     * method deliberately does not attempt to paper over — see {@code services/booking/CLAUDE.md}'s
     * "Known gap: no timeout sweep yet").
     */
    public boolean extendTtl(Long eventId, Long seatId) {
        Long refreshed = redisTemplate.execute(
                EXTEND_IF_PRESENT, List.of(key(eventId, seatId)), String.valueOf(HOLD_TTL.getSeconds()));
        return refreshed != null && refreshed == 1L;
    }

    /**
     * Unconditionally deletes the {@code eventId:seatId} key, with no {@code holdToken}
     * comparison. Used by the Phase 8 saga completion path (payment failure) and the timeout sweep,
     * neither of which has access to the original {@code holdToken} generated by
     * {@code BookingHoldService.holdSeats} (it was never persisted anywhere outside that one
     * request).
     *
     * <p><strong>Why this is safe despite bypassing the owner check:</strong> by the time this is
     * called, {@code SeatAvailability.status} (the durable record) already gates re-acquisition —
     * {@code BookingHoldService.persistHold}'s defensive DB check rejects a new hold on a seat
     * whose {@code SeatAvailability} row is not {@code AVAILABLE}/absent, regardless of what Redis
     * says. So even in the pathological window where this delete races with some other future
     * holder's {@code SETNX}, that holder can only win the DB write once this caller has also
     * flipped {@code SeatAvailability} back to {@code AVAILABLE} in the same transaction — there is
     * no way for two holders to both believe they own the seat.
     */
    public void forceRelease(Long eventId, Long seatId) {
        redisTemplate.delete(key(eventId, seatId));
    }

    private String key(Long eventId, Long seatId) {
        return KEY_PREFIX + eventId + ":" + seatId;
    }
}
