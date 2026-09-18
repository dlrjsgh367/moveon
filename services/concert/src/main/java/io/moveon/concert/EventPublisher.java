package io.moveon.concert;

import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// "예매 확정" 이벤트를 Redis Streams 에 발행. notification 이 소비자 그룹으로 읽는다.
// eventId 는 예매당 고정("confirm:<id>") -> 소비 쪽이 이 키로 멱등 처리.
@Component
public class EventPublisher {

    public static final String STREAM = "reservation-confirmed";

    private final StringRedisTemplate redis;

    public EventPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publishConfirmed(Long reservationId, Long memberId, Long concertId, String seatNo) {
        Map<String, String> event = Map.of(
                "eventId", "confirm:" + reservationId,
                "reservationId", String.valueOf(reservationId),
                "memberId", String.valueOf(memberId),
                "concertId", String.valueOf(concertId),
                "seatNo", seatNo);
        redis.opsForStream().add(STREAM, event);
    }
}
