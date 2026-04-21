package com.moussefer.demande.entity;

public enum DemandeStatus {
    OPEN,
    TRIGGERED,
    CLOSED,
    CANCELLED,
    MERGED   // FEAT-01: demand was merged into another
}