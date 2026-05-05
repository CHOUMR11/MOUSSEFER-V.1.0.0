package com.moussefer.user.entity;

/**
 * Types of documents a driver must upload to be verified.
 *
 * Aligned with the driver KYC maquette (Inscription des informations chauffeur):
 *   - CIN: Carte d'Identité Nationale scan
 *   - DRIVING_LICENSE_FRONT / BACK: Permis de conduire recto/verso
 *   - VEHICLE_PHOTO: Photo du louage (showing license plate)
 *   - INSURANCE: Attestation d'assurance véhicule (with expiry date)
 *   - TECHNICAL_VISIT: Rapport de visite technique (with expiry date)
 *   - LOUAGE_AUTHORIZATION: رخصة اللواج — official transport license
 */
public enum DocumentType {
    CIN,
    DRIVING_LICENSE_FRONT,
    DRIVING_LICENSE_BACK,
    VEHICLE_PHOTO,
    INSURANCE,
    TECHNICAL_VISIT,
    LOUAGE_AUTHORIZATION,
    OTHER
}
