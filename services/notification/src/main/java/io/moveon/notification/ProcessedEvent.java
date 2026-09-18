package io.moveon.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// 이미 처리한 이벤트 기록. eventId 가 PK 라 같은 이벤트 두 번 오면 두 번째 insert 가 막힌다(멱등).
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    private String eventId;
    private LocalDateTime processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = LocalDateTime.now();
    }
}
