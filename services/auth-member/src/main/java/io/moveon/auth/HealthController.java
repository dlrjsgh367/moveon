package io.moveon.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/auth/health")
    public String health() {
        return "ok";
    }
}
