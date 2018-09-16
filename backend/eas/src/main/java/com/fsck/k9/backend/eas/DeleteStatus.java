package com.fsck.k9.backend.eas;


import java.util.List;


public interface DeleteStatus {
    List<String> getServerIdsForRetries();
    List<String> getServerIdsForReverts();
}
