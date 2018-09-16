package com.fsck.k9.protocol.eas.adapter;


import com.fsck.k9.protocol.eas.CommandStatusException;


public interface FolderSyncController {
    void folderStatus(int status) throws CommandStatusException;

    void updateSyncKey(String newSyncKey);
}
