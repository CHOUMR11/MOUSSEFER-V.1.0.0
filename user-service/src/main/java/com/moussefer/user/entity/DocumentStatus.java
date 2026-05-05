package com.moussefer.user.entity;

/**
 * Lifecycle state of an uploaded document.
 *
 * PENDING_REVIEW → MODERATOR/SUPER_ADMIN checks it
 *   ├── APPROVED (visible to user as "verified")
 *   ├── REJECTED (user must re-upload; reason stored)
 *   └── EXPIRED (automatic when expiryDate passes — driver must renew)
 */
public enum DocumentStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    EXPIRED
}
