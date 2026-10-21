package com.example.ecommerce.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ReturnStatus {
    REQUESTED,
    APPROVED,
    RECEIVED,
    REJECTED,
    COMPLETED;

    public static Set<ReturnStatus> activeStatuses() {
        return EnumSet.of(REQUESTED, APPROVED, RECEIVED);
    }

    public boolean isActive() {
        return activeStatuses().contains(this);
    }
}
