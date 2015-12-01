package com.fsck.k9.remote;


import java.util.List;


public interface DeleteStatus {
    List<String> getServerIdsForRetries();
    List<String> getServerIdsForReverts();
}
