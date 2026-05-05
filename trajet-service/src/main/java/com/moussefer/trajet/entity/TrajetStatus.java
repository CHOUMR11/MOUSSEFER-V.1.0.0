package com.moussefer.trajet.entity;

public enum TrajetStatus {
    ACTIVE,       // prioritaire, réservable
    LOCKED,       // en file d'attente, non réservable
    FULL,         // complet, cède la place
    DEPARTED,     // parti
    CANCELLED     // annulé (admin)
}