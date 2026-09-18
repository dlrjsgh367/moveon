package io.moveon.concert;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// 좌석 하나가 곧 예매 대상. status: FREE / HELD / CONFIRMED.
// 선점/확정은 SeatRepository 의 조건부 UPDATE 로만 바뀐다(초과판매 방지 지점).
@Entity
@Table(name = "seat")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long concertId;
    private String seatNo;
    private String status;
    private Long memberId;
    private LocalDateTime expiresAt;

    protected Seat() {
    }

    public Long getId() {
        return id;
    }

    public Long getConcertId() {
        return concertId;
    }

    public String getSeatNo() {
        return seatNo;
    }

    public String getStatus() {
        return status;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
