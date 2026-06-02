package com.zillya.timonfech.zillwrapper.core.entities;


public enum ItemProcessingStatus {

    /** Initial state. No generation attempted. */
    PENDING,

    /** Licenses generated and persisted. Artifact production not yet complete. */
    GENERATED,

    /** All requested artifacts (Excel, Email) produced successfully. Intermediate state before delivery. */
    ARTIFACTS_READY,

    /** Artifact production failed. Licenses exist. Can be retried. */
    ARTIFACT_FAILED,

    /** All requested artifacts delivered (sent to client/source) successfully. Terminal success state. */
    DELIVERED,

    /** Artifacts exist, but at least one delivery channel failed (e.g. SMTP timeout). Terminal (retriable). */
    DELIVERY_FAILED,

    /** License generation failed. No licenses in DB. Quota refundable. Terminal failure state. */
    FAILED,

}
