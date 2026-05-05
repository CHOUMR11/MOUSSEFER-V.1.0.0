package com.moussefer.user.scheduler;

import com.moussefer.user.entity.VerificationStatus;
import com.moussefer.user.repository.DriverDocumentRepository;
import com.moussefer.user.repository.UserProfileRepository;
import com.moussefer.user.service.DriverDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentExpiryScheduler {

    private final DriverDocumentRepository documentRepository;
    private final DriverDocumentService documentService;
    private final UserProfileRepository userProfileRepository;

    @Scheduled(cron = "0 0 2 * * ?")  // 2:00 AM every night
    @Transactional
    public void expireOutdatedDocuments() {
        int expired = documentRepository.markExpired(LocalDate.now());
        log.info("Marked {} documents as EXPIRED", expired);
    }

    @Scheduled(cron = "0 5 2 * * ?")  // 2:05 AM
    @Transactional
    public void recalcVerificationStatusForAffectedDrivers() {
        List<String> driverIds = documentRepository.findDistinctUserIdsWithExpiredDocuments();
        for (String driverId : driverIds) {
            boolean stillComplete = documentService.isKycComplete(driverId);
            userProfileRepository.findById(driverId).ifPresent(profile -> {
                if (!stillComplete && profile.getVerificationStatus() == VerificationStatus.VERIFIED) {
                    profile.setVerificationStatus(VerificationStatus.PENDING);
                    userProfileRepository.save(profile);
                    log.info("KYC status reset to PENDING for driver {}", driverId);
                }
            });
        }
    }
}