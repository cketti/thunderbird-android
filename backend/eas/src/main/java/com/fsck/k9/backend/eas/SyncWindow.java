package com.fsck.k9.backend.eas;


public interface SyncWindow {
    int getValue();
    SyncWindowUnit getUnit();


    enum SyncWindowUnit {
        ALL,
        DAYS,
        WEEKS,
        MONTHS
    }
}
