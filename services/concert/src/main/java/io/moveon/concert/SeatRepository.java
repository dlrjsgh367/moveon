package io.moveon.concert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByConcertIdOrderBySeatNo(Long concertId);

    Optional<Seat> findByConcertIdAndSeatNo(Long concertId, String seatNo);

    // 선점: FREE 이거나 (HELD 인데 만료됨)일 때만 성공. 동시에 들어오면 MySQL 행 잠금으로
    // 한 트랜잭션만 조건을 만족 -> 영향행 1. 나머지는 확정/유효 HELD 를 보게 되어 0.
    // 만료 재선점(lazy)도 같은 WHERE 로 함께 처리한다(별도 스케줄러 없음).
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Seat s
               SET s.status = 'HELD', s.memberId = :memberId, s.expiresAt = :expiresAt
             WHERE s.concertId = :concertId AND s.seatNo = :seatNo
               AND (s.status = 'FREE' OR (s.status = 'HELD' AND s.expiresAt < :now))
            """)
    int hold(@Param("concertId") Long concertId,
             @Param("seatNo") String seatNo,
             @Param("memberId") Long memberId,
             @Param("expiresAt") LocalDateTime expiresAt,
             @Param("now") LocalDateTime now);

    // 확정: 본인이 잡은, 아직 유효한 HELD 만 CONFIRMED 로. 영향행 1이면 확정 성공.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Seat s
               SET s.status = 'CONFIRMED', s.expiresAt = NULL
             WHERE s.id = :seatId AND s.memberId = :memberId
               AND s.status = 'HELD' AND s.expiresAt > :now
            """)
    int confirm(@Param("seatId") Long seatId,
                @Param("memberId") Long memberId,
                @Param("now") LocalDateTime now);
}
