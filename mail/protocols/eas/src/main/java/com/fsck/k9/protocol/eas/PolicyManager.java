package com.fsck.k9.protocol.eas;


public interface PolicyManager {
    void setAccountPolicy(Policy policy);
    int getMaxSyncWindow();
}
