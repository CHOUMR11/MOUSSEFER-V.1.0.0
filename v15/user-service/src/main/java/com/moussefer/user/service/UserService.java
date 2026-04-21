package com.moussefer.user.service;

import com.moussefer.user.dto.request.UpdateProfileRequest;
import com.moussefer.user.dto.response.DriverInfoResponse;
import com.moussefer.user.dto.response.UserProfileResponse;
import com.moussefer.user.entity.UserProfile;
import com.moussefer.user.entity.UserRole;
import com.moussefer.user.entity.VerificationStatus;
import com.moussefer.user.exception.BusinessException;
import com.moussefer.user.exception.ResourceNotFoundException;
import com.moussefer.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserProfileRepository userProfileRepository;

    // ========== PUBLIC / SELF-SERVICE ==========
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String userId) {
        return UserProfileResponse.from(findById(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        UserProfile profile = findById(userId);
        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) profile.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) profile.setPhoneNumber(request.getPhoneNumber());
        if (request.getProfilePictureUrl() != null) profile.setProfilePictureUrl(request.getProfilePictureUrl());

        if (profile.getRole() == UserRole.DRIVER) {
            if (request.getCinNumber() != null) profile.setCinNumber(request.getCinNumber());
            if (request.getDriverLicenseNumber() != null) profile.setDriverLicenseNumber(request.getDriverLicenseNumber());
            if (request.getVehicleBrand() != null) profile.setVehicleBrand(request.getVehicleBrand());
            if (request.getVehicleModel() != null) profile.setVehicleModel(request.getVehicleModel());
            if (request.getVehiclePlate() != null) profile.setVehiclePlate(request.getVehiclePlate());
            if (request.getVehicleColor() != null) profile.setVehicleColor(request.getVehicleColor());
            if (request.getVehicleYear() != null) profile.setVehicleYear(request.getVehicleYear());
        }

        if (profile.getRole() == UserRole.ORGANIZER) {
            if (request.getCompanyName() != null) profile.setCompanyName(request.getCompanyName());
            if (request.getCompanyRegistration() != null) profile.setCompanyRegistration(request.getCompanyRegistration());
        }

        profile = userProfileRepository.save(profile);
        log.info("Profile updated for user: {}", userId);
        return UserProfileResponse.from(profile);
    }

    @Transactional
    public void deactivateOwnAccount(String userId) {
        UserProfile profile = findById(userId);
        if (!profile.isActive()) throw new BusinessException("Account already deactivated");
        profile.setActive(false);
        userProfileRepository.save(profile);
        log.info("User {} deactivated own account", userId);
    }

    @Transactional
    public void reactivateOwnAccount(String userId) {
        UserProfile profile = findById(userId);
        if (profile.isActive()) throw new BusinessException("Account already active");
        profile.setActive(true);
        userProfileRepository.save(profile);
        log.info("User {} reactivated own account", userId);
    }

    @Transactional
    public void updateFcmToken(String userId, String fcmToken) {
        UserProfile profile = findById(userId);
        profile.setFcmToken(fcmToken);
        userProfileRepository.save(profile);
    }

    // ========== INTERNAL ENDPOINTS (for other services) ==========
    @Transactional(readOnly = true)
    public boolean isUserActive(String userId) {
        return findById(userId).isActive();
    }

    @Transactional(readOnly = true)
    public LocalDateTime getSuspensionEndDate(String userId) {
        return findById(userId).getSuspendedUntil();
    }

    @Transactional(readOnly = true)
    public DriverInfoResponse getUserInfoInternal(String userId) {
        UserProfile p = findById(userId);
        return DriverInfoResponse.builder()
                .userId(p.getUserId())
                .email(p.getEmail())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .phoneNumber(p.getPhoneNumber())
                .vehicleBrand(p.getVehicleBrand())
                .vehicleModel(p.getVehicleModel())
                .vehiclePlate(p.getVehiclePlate())
                .vehicleColor(p.getVehicleColor())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DriverInfoResponse> getAllActiveDrivers() {
        return userProfileRepository.findAllDrivers().stream()
                .filter(UserProfile::isActive)
                .map(d -> DriverInfoResponse.builder()
                        .userId(d.getUserId())
                        .email(d.getEmail())
                        .firstName(d.getFirstName())
                        .lastName(d.getLastName())
                        .phoneNumber(d.getPhoneNumber())
                        .vehicleBrand(d.getVehicleBrand())
                        .vehicleModel(d.getVehicleModel())
                        .vehiclePlate(d.getVehiclePlate())
                        .vehicleColor(d.getVehicleColor())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverInfoResponse getDriverInfoForPayment(String driverId) {
        UserProfile profile = findById(driverId);
        if (profile.getRole() != UserRole.DRIVER) {
            throw new BusinessException("User is not a driver");
        }
        return DriverInfoResponse.builder()
                .userId(profile.getUserId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber())
                .vehicleBrand(profile.getVehicleBrand())
                .vehicleModel(profile.getVehicleModel())
                .vehiclePlate(profile.getVehiclePlate())
                .vehicleColor(profile.getVehicleColor())
                .build();
    }

    @Transactional
    public void updateDriverRating(String driverId, double averageRating, int totalTrips) {
        userProfileRepository.updateDriverRating(driverId, averageRating, totalTrips);
        log.info("Driver rating updated: driverId={}, avg={}, trips={}", driverId, averageRating, totalTrips);
    }

    // ========== VERIFICATION (called by admin-service via internal API) ==========
    @Transactional
    public void updateVerificationStatus(String userId, VerificationStatus status, String rejectionReason) {
        UserProfile profile = findById(userId);
        profile.setVerificationStatus(status);
        profile.setRejectionReason(status == VerificationStatus.REJECTED ? rejectionReason : null);
        if (profile.getRole() == UserRole.ORGANIZER && status == VerificationStatus.APPROVED) {
            profile.setVerifiedOrganizer(true);
        }
        userProfileRepository.save(profile);
        log.info("Verification status for user {} set to {}", userId, status);
    }

    // ========== SUSPENSION (called by admin-service) ==========
    @Transactional
    public void setSuspension(String userId, LocalDateTime until, String reason) {
        UserProfile profile = findById(userId);
        profile.setSuspendedUntil(until);
        profile.setSuspensionReason(reason);
        userProfileRepository.save(profile);
        log.info("User {} suspended until {}: {}", userId, until, reason);
    }

    @Transactional
    public void liftSuspension(String userId) {
        UserProfile profile = findById(userId);
        profile.setSuspendedUntil(null);
        profile.setSuspensionReason(null);
        userProfileRepository.save(profile);
        log.info("Suspension lifted for user {}", userId);
    }

    // ========== PRIVATE HELPERS ==========
    private UserProfile findById(String userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}