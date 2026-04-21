package com.moussefer.banner.service;

import com.moussefer.banner.dto.request.BannerRequest;
import com.moussefer.banner.entity.Banner;
import com.moussefer.banner.entity.BannerAudience;
import com.moussefer.banner.entity.BannerInteraction;
import com.moussefer.banner.exception.ResourceNotFoundException;
import com.moussefer.banner.repository.BannerInteractionRepository;
import com.moussefer.banner.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BannerService {

    private final BannerRepository bannerRepository;
    private final BannerInteractionRepository interactionRepository;

    public List<Banner> getActiveBanners(String audience) {
        BannerAudience target = audience != null
                ? BannerAudience.valueOf(audience.toUpperCase())
                : BannerAudience.ALL;
        return bannerRepository.findActiveBannersForAudience(target, LocalDateTime.now());
    }

    public List<Banner> getAll() {
        return bannerRepository.findAllByOrderByDisplayOrderAsc();
    }

    public Banner getById(String id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner", "id", id));
    }

    @Transactional
    public Banner create(BannerRequest request) {
        Banner banner = Banner.builder()
                .title(request.getTitle())
                .imageUrl(request.getImageUrl())
                .redirectUrl(request.getRedirectUrl())
                .displayOrder(request.getDisplayOrder())
                .targetAudience(request.getTargetAudience() != null
                        ? request.getTargetAudience() : BannerAudience.ALL)
                .active(request.isActive())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .build();
        log.info("Creating banner: {}", banner.getTitle());
        return bannerRepository.save(banner);
    }

    @Transactional
    public Banner update(String id, BannerRequest request) {
        Banner banner = getById(id);
        banner.setTitle(request.getTitle());
        banner.setImageUrl(request.getImageUrl());
        banner.setRedirectUrl(request.getRedirectUrl());
        banner.setDisplayOrder(request.getDisplayOrder());
        banner.setActive(request.isActive());
        if (request.getTargetAudience() != null) banner.setTargetAudience(request.getTargetAudience());
        banner.setStartsAt(request.getStartsAt());
        banner.setEndsAt(request.getEndsAt());
        return bannerRepository.save(banner);
    }

    @Transactional
    public void delete(String id) {
        Banner banner = getById(id);
        bannerRepository.delete(banner);
        log.info("Banner {} deleted", id);
    }

    // ─── Tracking ────────────────────────────────────────────────────
    @Transactional
    public void trackInteraction(String bannerId, String userId, String type) {
        BannerInteraction interaction = BannerInteraction.builder()
                .bannerId(bannerId)
                .userId(userId)
                .interactionType(BannerInteraction.InteractionType.valueOf(type.toUpperCase()))
                .build();
        interactionRepository.save(interaction);
    }

    public Map<String, Object> getBannerPerformance(String bannerId) {
        Banner banner = getById(bannerId);
        long impressions = interactionRepository.countByBannerIdAndInteractionType(
                bannerId, BannerInteraction.InteractionType.IMPRESSION);
        long clicks = interactionRepository.countByBannerIdAndInteractionType(
                bannerId, BannerInteraction.InteractionType.CLICK);
        double ctr = impressions > 0 ? (double) clicks / impressions * 100 : 0;
        return Map.of(
                "bannerId", bannerId,
                "title", banner.getTitle(),
                "impressions", impressions,
                "clicks", clicks,
                "ctr", String.format("%.2f%%", ctr),
                "active", banner.isActive()
        );
    }

    public List<Map<String, Object>> getGlobalPerformance() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Banner banner : bannerRepository.findAllByOrderByDisplayOrderAsc()) {
            result.add(getBannerPerformance(banner.getId()));
        }
        return result;
    }
}
