package com.moussefer.demande.repository;

import com.moussefer.demande.entity.DemandePassager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandePassagerRepository extends JpaRepository<DemandePassager, String> {

    List<DemandePassager> findByDemandeId(String demandeId);

    boolean existsByDemandeIdAndPassengerId(String demandeId, String passengerId);

    @Query("SELECT COALESCE(SUM(dp.seatsReserved), 0) FROM DemandePassager dp WHERE dp.demandeId = :demandeId")
    int sumSeatsReservedByDemandeId(@Param("demandeId") String demandeId);

    @Query("""
        SELECT DISTINCT dp.passengerId FROM DemandePassager dp
        WHERE dp.demandeId IN (
            SELECT d.id FROM DemandeCollective d
            WHERE d.departureCity = :dep AND d.arrivalCity = :arr
              AND d.status = 'OPEN'
        )
    """)
    List<String> findWaitingPassengerIdsByRoute(@Param("dep") String dep, @Param("arr") String arr);
}