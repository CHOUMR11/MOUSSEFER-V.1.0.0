package com.moussefer.auth.repository;

import com.moussefer.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    @Modifying
    @Query("UPDATE User u SET u.refreshToken = :token, u.refreshTokenExpiry = :expiry WHERE u.id = :userId")
    void updateRefreshToken(String userId, String token, LocalDateTime expiry);

    @Modifying
    @Query("UPDATE User u SET u.lastLogin = CURRENT_TIMESTAMP WHERE u.id = :userId")
    void updateLastLogin(String userId);
}