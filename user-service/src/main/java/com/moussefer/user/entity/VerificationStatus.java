package com.moussefer.user.entity;

public enum VerificationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    VERIFIED   // <-- added: auto‑set when all documents are approved
}