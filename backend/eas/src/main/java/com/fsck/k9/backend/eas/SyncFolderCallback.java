package com.fsck.k9.backend.eas;


public interface SyncFolderCallback {
    void onMessageFullyDownloaded(String folderServerId, String messageServerId);
}
