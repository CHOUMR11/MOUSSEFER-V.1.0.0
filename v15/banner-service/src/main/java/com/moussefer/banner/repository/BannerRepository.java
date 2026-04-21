package com.moussefer.banner.repository;

import com.moussefer.banner.entity.Banner;
import com.moussefer.banner.entity.BannerAudience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, String> {

    @Query("""
        SELECT b FROM Banner b
        WHERE b.active = true
        AND (b.targetAudience = :audience OR b.targetAudience = 'ALL')
        AND (b.startsAt IS NULL OR b.startsAt <= :now)
        AND (b.endsAt IS NULL OR b.endsAt >= :now)
        ORDER BY b.displayOrder ASC
    """)
    List<Banner> findActiveBannersForAudience(BannerAudience audience, LocalDateTime now);

    List<Banner> findAllByOrderByDisplayOrderAsc();
}
