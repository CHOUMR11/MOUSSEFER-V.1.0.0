package com.moussefer.user.service;

import com.moussefer.user.config.MinioBucketInitializer;
import com.moussefer.user.config.MinioProperties;
import com.moussefer.user.dto.request.UpdateProfileRequest;
import com.moussefer.user.dto.response.DriverInfoResponse;
import com.moussefer.user.dto.response.PublicDriverProfileResponse;
import com.moussefer.user.dto.response.UserProfileResponse;
import com.moussefer.user.entity.UserProfile;
import com.moussefer.user.entity.UserRole;
import com.moussefer.user.entity.VerificationStatus;
import com.moussefer.user.exception.BusinessException;
import com.moussefer.user.exception.ResourceNotFoundException;
import com.moussefer.user.repository.UserProfileRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final MinioBucketInitializer bucketInitializer;

    private static final Set<String> ALLOWED_IMAGE_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final long MAX_PROFILE_PICTURE_SIZE = 5L * 1024 * 1024; // 5MB

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
    public UserProfileResponse uploadProfilePicture(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is required");
        }
        if (file.getSize() > MAX_PROFILE_PICTURE_SIZE) {
            throw new BusinessException("Profile picture must be 5MB or smaller");
        }
        if (file.getContentType() == null || !ALLOWED_IMAGE_MIME.contains(file.getContentType())) {
            throw new BusinessException(
                    "Unsupported image type. Allowed: JPEG, PNG, WebP");
        }

        UserProfile profile = findById(userId);

        String bucket = minioProperties.getBucket();
        if (!bucketInitializer.isBucketReady()) {
            throw new BusinessException("MinIO bucket not ready");
        }

        String safeName = sanitize(file.getOriginalFilename());
        String objectName = String.format("profile-pictures/%s/%s-%s",
                userId, UUID.randomUUID().toString().substring(0, 8), safeName);

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO upload failed for profile picture of user {}", userId, e);
            throw new BusinessException("Profile picture upload failed: " + e.getMessage());
        }

        String fileUrl = minioProperties.getEndpoint() + "/" + bucket + "/" + objectName;
        profile.setProfilePictureUrl(fileUrl);
        profile = userProfileRepository.save(profile);

        log.info("Profile picture updated for user {}: {}", userId, objectName);
        return UserProfileResponse.from(profile);
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "image";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
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

    // Public restricted driver profile
    @Transactional(readOnly = true)
    public PublicDriverProfileResponse getPublicDriverProfile(String driverId) {
        UserProfile profile = findById(driverId);
        if (profile.getRole() != UserRole.DRIVER) {
            throw new BusinessException("User is not a driver");
        }
        return PublicDriverProfileResponse.builder()
                .userId(profile.getUserId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .profilePictureUrl(profile.getProfilePictureUrl())
                .averageRating(profile.getAverageRating())
                .vehicleBrand(profile.getVehicleBrand())
                .vehicleModel(profile.getVehicleModel())
                .vehiclePlate(profile.getVehiclePlate())
                .build();
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