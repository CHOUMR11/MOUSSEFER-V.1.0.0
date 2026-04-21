package com.moussefer.banner.repository;

import com.moussefer.banner.entity.BannerInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BannerInteractionRepository extends JpaRepository<BannerInteraction, String> {

    long countByBannerIdAndInteractionType(String bannerId, BannerInteraction.InteractionType type);

    @Query("SELECT b.bannerId, b.interactionType, COUNT(b) FROM BannerInteraction b GROUP BY b.bannerId, b.interactionType")
    List<Object[]> getInteractionStats();

    @Query("SELECT b.bannerId, COUNT(b) FROM BannerInteraction b WHERE b.interactionType = 'CLICK' GROUP BY b.bannerId ORDER BY COUNT(b) DESC")
    List<Object[]> getTopBannersByClicks();
}
