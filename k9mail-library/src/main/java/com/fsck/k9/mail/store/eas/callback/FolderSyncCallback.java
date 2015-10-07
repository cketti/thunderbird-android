package com.fsck.k9.mail.store.eas.callback;


public interface FolderSyncCallback {
    void addFolder(String serverId, String name, int type, String parentServerId);
    void removeFolder(String serverId);
    void changeFolder(String serverId, String name, String parentServerId);
    void clearFolders();
    void commitFolderChanges();
}
