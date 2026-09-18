package io.moveon.concert;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

    private final SeatRepository seats;
    private final long holdTtlSeconds;

    public ReservationService(
            SeatRepository seats,
            @Value("${reservation.hold-ttl-seconds}") long holdTtlSeconds) {
        this.seats = seats;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    // 좌석 선점. 성공하면 갱신된 좌석(HELD, expiresAt) 반환. 이미 잡혀 있으면 409.
    @Transactional
    public Seat hold(Long concertId, String seatNo, Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = seats.hold(concertId, seatNo, memberId, now.plusSeconds(holdTtlSeconds), now);
        if (updated == 0) {
            seats.findByConcertIdAndSeatNo(concertId, seatNo)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "좌석 없음"));
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 잡힌 좌석");
        }
        return seats.findByConcertIdAndSeatNo(concertId, seatNo).orElseThrow();
    }
}
