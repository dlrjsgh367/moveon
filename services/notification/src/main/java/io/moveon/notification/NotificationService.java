package io.moveon.notification;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final ProcessedEventRepository processed;
    private final NotificationRepository notifications;

    public NotificationService(ProcessedEventRepository processed,
                               NotificationRepository notifications) {
        this.processed = processed;
        this.notifications = notifications;
    }

    // eventId 기준 멱등 발송. 처음이면 발송(저장)하고 true, 이미 처리했으면 false.
    @Transactional
    public boolean sendIfNew(String eventId, Long memberId, Long concertId, String seatNo) {
        if (processed.existsById(eventId)) {
            return false;
        }
        try {
            processed.saveAndFlush(new ProcessedEvent(eventId));   // PK 충돌 시 동시 중복
        } catch (DataIntegrityViolationException dup) {
            return false;
        }
        String message = "공연 " + concertId + " 좌석 " + seatNo + " 예매가 확정되었습니다";
        notifications.save(new Notification(eventId, memberId, message));
        return true;
    }
}
