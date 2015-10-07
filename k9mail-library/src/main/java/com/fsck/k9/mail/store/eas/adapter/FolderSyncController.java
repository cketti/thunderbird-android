package com.fsck.k9.mail.store.eas.adapter;


public interface FolderSyncController {
    void folderStatus(int status);

    void updateSyncKey(String newSyncKey);
}
