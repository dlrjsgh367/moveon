package io.moveon.concert;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "concert")
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String venue;
    private LocalDate performDate;

    protected Concert() {
    }

    public Concert(String title, String venue, LocalDate performDate) {
        this.title = title;
        this.venue = venue;
        this.performDate = performDate;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getVenue() {
        return venue;
    }

    public LocalDate getPerformDate() {
        return performDate;
    }
}
