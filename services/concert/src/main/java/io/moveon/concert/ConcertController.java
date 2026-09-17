package io.moveon.concert;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConcertController {

    private final ConcertRepository repository;

    public ConcertController(ConcertRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/concerts")
    public List<Concert> list() {
        return repository.findAll();
    }
}
