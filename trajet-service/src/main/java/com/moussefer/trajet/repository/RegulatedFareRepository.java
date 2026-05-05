package com.moussefer.trajet.repository;

import com.moussefer.trajet.entity.RegulatedFare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegulatedFareRepository extends JpaRepository<RegulatedFare, String> {

    /**
     * Find active regulated fare by route. Matching is case-insensitive
     * because drivers and admins may enter city names with different casings.
     */
    @Query("""
        SELECT f FROM RegulatedFare f
        WHERE LOWER(f.departureCity) = LOWER(:dep)
          AND LOWER(f.arrivalCity)   = LOWER(:arr)
          AND f.active = true
    """)
    Optional<RegulatedFare> findActiveFare(@Param("dep") String departureCity,
                                           @Param("arr") String arrivalCity);

    /**
     * Used by admin for fare management (shows all fares, active or not).
     */
    @Query("""
        SELECT f FROM RegulatedFare f
        WHERE LOWER(f.departureCity) = LOWER(:dep)
          AND LOWER(f.arrivalCity)   = LOWER(:arr)
    """)
    Optional<RegulatedFare> findByRoute(@Param("dep") String departureCity,
                                         @Param("arr") String arrivalCity);

    /**
     * Filterable listing for admin UI. All filters are optional.
     */
    @Query("""
        SELECT f FROM RegulatedFare f
        WHERE (:city IS NULL OR LOWER(f.departureCity) LIKE LOWER(CONCAT('%', :city, '%'))
                             OR LOWER(f.arrivalCity)   LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:active IS NULL OR f.active = :active)
        ORDER BY f.departureCity, f.arrivalCity
    """)
    Page<RegulatedFare> search(@Param("city") String city,
                                @Param("active") Boolean active,
                                Pageable pageable);
}
