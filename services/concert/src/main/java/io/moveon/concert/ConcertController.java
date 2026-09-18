package io.moveon.concert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConcertController {

    private final ConcertRepository concerts;
    private final SeatRepository seats;
    private final ReservationService reservations;
    private final JwtVerifier jwt;
    private final PaymentClient paymentClient;
    private final EventPublisher publisher;

    public ConcertController(ConcertRepository concerts, SeatRepository seats,
                             ReservationService reservations, JwtVerifier jwt,
                             PaymentClient paymentClient, EventPublisher publisher) {
        this.concerts = concerts;
        this.seats = seats;
        this.reservations = reservations;
        this.jwt = jwt;
        this.paymentClient = paymentClient;
        this.publisher = publisher;
    }

    public record RegisterRequest(String title, String venue, LocalDate performDate) {
    }

    public record ReserveRequest(String seatNo) {
    }

    public record ReservationResponse(Long reservationId, Long concertId, String seatNo,
                                      String status, LocalDateTime expiresAt) {
    }

    public record PayRequest(Integer amount, String outcome) {
    }

    @GetMapping("/concerts")
    public List<Concert> list() {
        return concerts.findAll();
    }

    @GetMapping("/concerts/{id}")
    public Concert detail(@PathVariable Long id) {
        return concerts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공연 없음"));
    }

    @GetMapping("/concerts/{id}/seats")
    public List<Map<String, Object>> seatsOf(@PathVariable Long id) {
        return seats.findByConcertIdOrderBySeatNo(id).stream()
                .map(s -> Map.<String, Object>of("seatNo", s.getSeatNo(), "status", s.getStatus()))
                .toList();
    }

    @PostMapping("/concerts")
    @ResponseStatus(HttpStatus.CREATED)
    public Concert register(@RequestBody RegisterRequest req) {
        if (req.title() == null || req.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title 필수");
        }
        // ponytail: 새 공연엔 좌석 시드가 없다. MVP 예매 검증은 시드 공연(1~3)으로 한다.
        return concerts.save(new Concert(req.title(), req.venue(), req.performDate()));
    }

    @PostMapping("/concerts/{id}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@PathVariable Long id,
                                       @RequestBody ReserveRequest req,
                                       @RequestHeader(value = "Authorization", required = false) String auth) {
        Long memberId = jwt.memberId(auth);
        if (req.seatNo() == null || req.seatNo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatNo 필수");
        }
        Seat held = reservations.hold(id, req.seatNo(), memberId);
        return new ReservationResponse(held.getId(), held.getConcertId(), held.getSeatNo(),
                held.getStatus(), held.getExpiresAt());
    }

    // 결제 후 확정. payment 동기 호출 -> 성공이면 확정 + 이벤트 발행, 실패면 좌석 유지(HELD).
    @PostMapping("/concerts/reservations/{reservationId}/pay")
    public Map<String, Object> pay(@PathVariable Long reservationId,
                                   @RequestBody(required = false) PayRequest req,
                                   @RequestHeader(value = "Authorization", required = false) String auth) {
        Long memberId = jwt.memberId(auth);
        Seat seat = reservations.requireOwnedHeld(reservationId, memberId);
        Integer amount = req == null ? null : req.amount();
        String outcome = req == null ? null : req.outcome();

        String paid = paymentClient.pay(reservationId, amount, outcome);
        if (!"SUCCESS".equals(paid)) {
            // 결제 실패: 좌석은 HELD 로 유지되어 유저가 재시도 가능.
            return Map.of("reservationId", reservationId, "status", "PAYMENT_FAILED",
                    "seatStatus", "HELD");
        }
        reservations.confirm(reservationId, memberId);
        publisher.publishConfirmed(reservationId, memberId, seat.getConcertId(), seat.getSeatNo());
        return Map.of("reservationId", reservationId, "status", "CONFIRMED",
                "seatNo", seat.getSeatNo());
    }
}
