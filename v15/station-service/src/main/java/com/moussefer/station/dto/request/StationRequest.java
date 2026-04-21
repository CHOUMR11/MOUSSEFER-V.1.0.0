package com.moussefer.station.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StationRequest {

    @NotBlank(message = "Station name is required")
    private String name;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Region is required")
    private String region;

    private String address;

    private Double latitude;

    private Double longitude;
}
