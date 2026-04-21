package com.moussefer.notification.repository;

import com.moussefer.notification.entity.AlertSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AlertSubscriptionRepository extends JpaRepository<AlertSubscription, String> {

    List<AlertSubscription> findByDepartureCityAndArrivalCityAndActiveTrue(String dep, String arr);

    List<AlertSubscription> findByDepartureCityAndArrivalCityAndDesiredDateAndActiveTrue(
            String dep, String arr, LocalDate date);

    List<AlertSubscription> findByUserIdAndActiveTrue(String userId);

    boolean existsByUserIdAndDepartureCityAndArrivalCityAndActiveTrue(String userId, String dep, String arr);
}
