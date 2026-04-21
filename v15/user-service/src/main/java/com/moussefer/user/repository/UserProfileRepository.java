package com.moussefer.user.repository;

import com.moussefer.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

    @Query("SELECT u FROM UserProfile u WHERE u.role = 'DRIVER'")
    @NonNull
    List<UserProfile> findAllDrivers();

    @Modifying
    @Transactional
    @Query("UPDATE UserProfile u SET u.averageRating = :rating, u.totalTrips = :totalTrips WHERE u.userId = :driverId")
    void updateDriverRating(@Param("driverId") @NonNull String driverId,
                            @Param("rating") @NonNull Double rating,
                            @Param("totalTrips") @NonNull Integer totalTrips);
}