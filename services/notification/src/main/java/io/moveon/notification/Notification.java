package io.moveon.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventId;
    private Long memberId;
    private String message;
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(String eventId, Long memberId, String message) {
        this.eventId = eventId;
        this.memberId = memberId;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }
}
