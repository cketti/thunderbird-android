package com.fsck.k9.remote;


public interface BackendStorage {
    void createFolder(String name, BackendFolderType backendFolderType, String serverId, String parentServerId);

    void deleteFolderByServerId(String serverId);

    void deleteAllFolders();
}
