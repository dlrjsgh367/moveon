package io.moveon.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

// 예매 확정 이벤트를 소비 -> 멱등 발송 -> XACK. 발송 실패(예외)면 XACK 안 함 -> 재전달.
@Component
public class ConfirmationListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationListener.class);

    private final NotificationService service;
    private final StringRedisTemplate redis;

    public ConfirmationListener(NotificationService service, StringRedisTemplate redis) {
        this.service = service;
        this.redis = redis;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> v = message.getValue();
        String eventId = v.get("eventId");
        Long memberId = Long.valueOf(v.get("memberId"));
        Long concertId = Long.valueOf(v.get("concertId"));
        String seatNo = v.get("seatNo");

        boolean sent = service.sendIfNew(eventId, memberId, concertId, seatNo);
        if (sent) {
            log.info("알림 발송: eventId={} member={} seat={}", eventId, memberId, seatNo);
        } else {
            log.info("중복 이벤트 건너뜀: eventId={}", eventId);
        }
        // 처리 완료(발송 또는 중복확인) 후 XACK.
        redis.opsForStream().acknowledge(StreamConfig.STREAM, StreamConfig.GROUP, message.getId());
    }
}
