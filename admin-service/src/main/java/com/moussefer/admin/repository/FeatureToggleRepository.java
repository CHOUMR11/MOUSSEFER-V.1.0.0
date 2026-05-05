package com.moussefer.admin.repository;

import com.moussefer.admin.entity.FeatureToggle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureToggleRepository extends JpaRepository<FeatureToggle, String> {
    Optional<FeatureToggle> findByFeatureKey(String featureKey);
    List<FeatureToggle> findByCategoryOrderByFeatureKey(String category);
    List<FeatureToggle> findAllByOrderByCategoryAscFeatureKeyAsc();
}
