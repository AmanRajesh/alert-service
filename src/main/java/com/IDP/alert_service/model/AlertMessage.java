package com.IDP.alert_service.model;

public record AlertMessage(
        String type,      // e.g., "STATIONARY", "COLLISION_WARNING"
        String sessionId,
        String message,
        long timestamp
) {}