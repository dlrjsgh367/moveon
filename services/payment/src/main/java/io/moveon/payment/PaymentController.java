package io.moveon.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 결제 mock: 기본 성공. outcome="FAIL" 이면 실패로 응답(결제 실패 시나리오 테스트용).
// 내부 서비스(concert)가 동기 호출한다. 별도 인증 없음(컨테이너 네트워크 내부).
@RestController
public class PaymentController {

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    public record PayRequest(Long reservationId, Integer amount, String outcome) {
    }

    public record PayResponse(Long paymentId, Long reservationId, String status) {
    }

    @PostMapping("/payment")
    public PayResponse pay(@RequestBody PayRequest req) {
        if (req.reservationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reservationId 필수");
        }
        String status = "FAIL".equalsIgnoreCase(req.outcome()) ? "FAIL" : "SUCCESS";
        int amount = req.amount() == null ? 0 : req.amount();
        Payment saved = payments.save(new Payment(req.reservationId(), amount, status));
        return new PayResponse(saved.getId(), saved.getReservationId(), saved.getStatus());
    }
}
