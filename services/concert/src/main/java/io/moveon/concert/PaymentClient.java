package io.moveon.concert;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 결제 서비스에 동기 요청하고 상태(SUCCESS/FAIL)를 돌려받는다.
@Component
public class PaymentClient {

    private final RestClient client;

    public PaymentClient(RestClient paymentRestClient) {
        this.client = paymentRestClient;
    }

    public String pay(Long reservationId, Integer amount, String outcome) {
        Map<String, Object> body = new HashMap<>();
        body.put("reservationId", reservationId);
        body.put("amount", amount == null ? 0 : amount);
        body.put("outcome", outcome);   // null 이면 payment 가 SUCCESS 처리
        Map<?, ?> resp = client.post().uri("/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        return resp == null ? "FAIL" : String.valueOf(resp.get("status"));
    }
}
