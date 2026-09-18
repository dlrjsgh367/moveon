package io.moveon.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/notification/health")
    public String health() {
        return "ok";
    }
}
