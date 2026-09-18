package io.moveon.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/payment/health")
    public String health() {
        return "ok";
    }
}
