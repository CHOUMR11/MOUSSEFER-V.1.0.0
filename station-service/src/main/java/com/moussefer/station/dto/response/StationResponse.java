package com.moussefer.station.dto.response;

import com.moussefer.station.entity.Station;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StationResponse {
    private String id;
    private String name;
    private String city;
    private String region;
    private String address;
    private Double latitude;
    private Double longitude;
    private boolean active;
    private LocalDateTime createdAt;

    public static StationResponse from(Station s) {
        return StationResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .city(s.getCity())
                .region(s.getRegion())
                .address(s.getAddress())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .active(s.isActive())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
