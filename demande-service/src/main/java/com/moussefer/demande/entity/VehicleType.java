package com.moussefer.demande.entity;

import lombok.Getter;

@Getter
public enum VehicleType {
    VOITURE_4(4),
    VOITURE_8(8),
    MINIBUS(16),
    BUS(52);

    private final int capacity;

    VehicleType(int capacity) {
        this.capacity = capacity;
    }
}